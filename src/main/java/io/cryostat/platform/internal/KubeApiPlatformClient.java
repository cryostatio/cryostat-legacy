/*
 * Copyright The Cryostat Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.cryostat.platform.internal;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.management.remote.JMXServiceURL;

import io.cryostat.core.log.Logger;
import io.cryostat.core.net.JFRConnectionToolkit;
import io.cryostat.core.net.discovery.JvmDiscoveryClient.EventKind;
import io.cryostat.platform.AbstractPlatformClient;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.ServiceRef.AnnotationKey;
import io.cryostat.platform.discovery.BaseNodeType;
import io.cryostat.platform.discovery.EnvironmentNode;
import io.cryostat.platform.discovery.NodeType;
import io.cryostat.platform.discovery.TargetNode;

import dagger.Lazy;
import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointPort;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectReference;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.fabric8.kubernetes.client.informers.SharedIndexInformer;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.concurrent.ConcurrentException;
import org.apache.commons.lang3.concurrent.LazyInitializer;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

public class KubeApiPlatformClient extends AbstractPlatformClient {

    private static final long ENDPOINTS_INFORMER_RESYNC_PERIOD = Duration.ofSeconds(30).toMillis();
    public static final String REALM = "KubernetesApi";

    private final KubernetesClient k8sClient;
    private final Set<String> namespaces;
    private final LazyInitializer<HashMap<String, SharedIndexInformer<Endpoints>>> nsInformers =
            new LazyInitializer<HashMap<String, SharedIndexInformer<Endpoints>>>() {
                @Override
                protected HashMap<String, SharedIndexInformer<Endpoints>> initialize()
                        throws ConcurrentException {
                    // TODO add support for some wildcard indicating a single Informer for any
                    // namespace that
                    // Cryostat has permissions to. This will need some restructuring of how the
                    // namespaces
                    // within the discovery tree are mapped.
                    var result = new HashMap<String, SharedIndexInformer<Endpoints>>();
                    namespaces.forEach(
                            ns -> {
                                result.put(
                                        ns,
                                        k8sClient
                                                .endpoints()
                                                .inNamespace(ns)
                                                .inform(
                                                        new EndpointsHandler(),
                                                        ENDPOINTS_INFORMER_RESYNC_PERIOD));
                                logger.info(
                                        "Started Endpoints SharedInformer for namespace \"{}\"",
                                        ns);
                            });
                    return result;
                }
            };
    private Integer memoHash;
    private EnvironmentNode memoTree;
    private final Lazy<JFRConnectionToolkit> connectionToolkit;
    private final Logger logger;
    private final Map<Triple<String, String, String>, Pair<HasMetadata, EnvironmentNode>>
            discoveryNodeCache = new ConcurrentHashMap<>();
    private final Map<Triple<String, String, String>, Object> queryLocks =
            new ConcurrentHashMap<>();

    KubeApiPlatformClient(
            Collection<String> namespaces,
            KubernetesClient k8sClient,
            Lazy<JFRConnectionToolkit> connectionToolkit,
            Logger logger) {
        this.namespaces = new HashSet<>(namespaces);
        this.k8sClient = k8sClient;
        this.connectionToolkit = connectionToolkit;
        this.logger = logger;
    }

    @Override
    public void start() {
        try {
            nsInformers.get(); // trigger lazy init
        } catch (ConcurrentException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public List<ServiceRef> listDiscoverableServices() {
        try {
            return getAllServiceRefs();
        } catch (Exception e) {
            logger.warn(e);
            return Collections.emptyList();
        }
    }

    @Override
    public EnvironmentNode getDiscoveryTree() {
        int currentHash = 0;
        HashCodeBuilder hcb = new HashCodeBuilder();
        Map<String, SharedIndexInformer<Endpoints>> informers = safeGetInformers();
        for (var informer : informers.values()) {
            List<Endpoints> store = informer.getStore().list();
            hcb.append(store.hashCode());
        }
        currentHash = hcb.build();
        if (Objects.equals(memoHash, currentHash)) {
            logger.trace("Using memoized discovery tree");
            return new EnvironmentNode(memoTree);
        }
        memoHash = currentHash;
        EnvironmentNode realmNode =
                new EnvironmentNode(REALM, BaseNodeType.REALM, Collections.emptyMap(), Set.of());
        informers
                .entrySet()
                .forEach(
                        entry -> {
                            var namespace = entry.getKey();
                            var store = entry.getValue().getStore().list();
                            EnvironmentNode nsNode =
                                    new EnvironmentNode(namespace, KubernetesNodeType.NAMESPACE);
                            try {
                                store.stream()
                                        .flatMap(endpoints -> getTargetTuples(endpoints).stream())
                                        .forEach(tuple -> buildOwnerChain(nsNode, tuple));
                            } catch (Exception e) {
                                logger.warn(e);
                            } finally {
                                discoveryNodeCache.clear();
                                queryLocks.clear();
                            }
                            realmNode.addChildNode(nsNode);
                        });
        memoTree = realmNode;
        return realmNode;
    }

    private Map<String, SharedIndexInformer<Endpoints>> safeGetInformers() {
        Map<String, SharedIndexInformer<Endpoints>> informers;
        try {
            informers = nsInformers.get();
        } catch (ConcurrentException e) {
            throw new IllegalStateException(e);
        }
        return informers;
    }

    private void buildOwnerChain(EnvironmentNode nsNode, TargetTuple targetTuple) {
        ObjectReference target = targetTuple.addr.getTargetRef();
        if (target == null) {
            logger.error(
                    "Address {} for Endpoint {} had null target reference",
                    targetTuple.addr.getIp() != null
                            ? targetTuple.addr.getIp()
                            : targetTuple.addr.getHostname(),
                    targetTuple.objRef.getName());
            return;
        }
        String targetKind = target.getKind();
        KubernetesNodeType targetType = KubernetesNodeType.fromKubernetesKind(targetKind);
        if (targetType == KubernetesNodeType.POD) {
            // if the Endpoint points to a Pod, chase the owner chain up as far as possible, then
            // add that to the Namespace

            Pair<HasMetadata, EnvironmentNode> pod =
                    discoveryNodeCache.computeIfAbsent(
                            cacheKey(target.getNamespace(), target), this::queryForNode);
            pod.getRight()
                    .addChildNode(
                            new TargetNode(
                                    KubernetesNodeType.ENDPOINT, targetTuple.toServiceRef()));

            Pair<HasMetadata, EnvironmentNode> node = pod;
            while (true) {
                Pair<HasMetadata, EnvironmentNode> owner = getOrCreateOwnerNode(node);
                if (owner == null) {
                    break;
                }
                EnvironmentNode ownerNode = owner.getRight();
                ownerNode.addChildNode(node.getRight());
                node = owner;
            }
            nsNode.addChildNode(node.getRight());
        } else {
            // if the Endpoint points to something else(?) than a Pod, just add the target straight
            // to the Namespace
            nsNode.addChildNode(
                    new TargetNode(KubernetesNodeType.ENDPOINT, targetTuple.toServiceRef()));
        }
    }

    private Pair<HasMetadata, EnvironmentNode> getOrCreateOwnerNode(
            Pair<HasMetadata, EnvironmentNode> child) {
        HasMetadata childRef = child.getLeft();
        if (childRef == null) {
            logger.error(
                    "Could not locate node named {} of kind {} while traversing environment",
                    child.getRight().getName(),
                    child.getRight().getNodeType());
            return null;
        }
        List<OwnerReference> owners = childRef.getMetadata().getOwnerReferences();
        // Take first "expected" owner Kind from NodeTypes, or if none, simply use the first owner.
        // If there are no owners then return null to signify this and break the chain
        if (owners.isEmpty()) {
            return null;
        }
        String namespace = childRef.getMetadata().getNamespace();
        OwnerReference owner =
                owners.stream()
                        .filter(o -> KubernetesNodeType.fromKubernetesKind(o.getKind()) != null)
                        .findFirst()
                        .orElse(owners.get(0));
        return discoveryNodeCache.computeIfAbsent(cacheKey(namespace, owner), this::queryForNode);
    }

    private Triple<String, String, String> cacheKey(String ns, OwnerReference resource) {
        return Triple.of(ns, resource.getKind(), resource.getName());
    }

    // Unfortunately, ObjectReference and OwnerReference both independently implement getKind and
    // getName - they don't come from a common base class.
    private Triple<String, String, String> cacheKey(String ns, ObjectReference resource) {
        return Triple.of(ns, resource.getKind(), resource.getName());
    }

    private Pair<HasMetadata, EnvironmentNode> queryForNode(
            Triple<String, String, String> lookupKey) {
        String namespace = lookupKey.getLeft();
        KubernetesNodeType nodeType = KubernetesNodeType.fromKubernetesKind(lookupKey.getMiddle());
        String nodeName = lookupKey.getRight();
        if (nodeType == null) {
            return null;
        }
        synchronized (queryLocks.computeIfAbsent(lookupKey, k -> new Object())) {
            EnvironmentNode node;
            HasMetadata kubeObj =
                    nodeType.getQueryFunction().apply(k8sClient).apply(namespace).apply(nodeName);
            if (kubeObj != null) {
                node = new EnvironmentNode(nodeName, nodeType, kubeObj.getMetadata().getLabels());
            } else {
                node = new EnvironmentNode(nodeName, nodeType);
            }
            return Pair.of(kubeObj, node);
        }
    }

    private boolean isCompatiblePort(EndpointPort port) {
        return "jfr-jmx".equals(port.getName()) || 9091 == port.getPort();
    }

    private List<ServiceRef> getAllServiceRefs() {
        return safeGetInformers().values().stream()
                .flatMap(i -> i.getStore().list().stream())
                .flatMap(endpoints -> getServiceRefs(endpoints).stream())
                .collect(Collectors.toList());
    }

    private List<TargetTuple> getTargetTuples(Endpoints endpoints) {
        List<TargetTuple> tts = new ArrayList<>();
        for (EndpointSubset subset : endpoints.getSubsets()) {
            for (EndpointPort port : subset.getPorts()) {
                if (!isCompatiblePort(port)) {
                    continue;
                }
                for (EndpointAddress addr : subset.getAddresses()) {
                    tts.add(new TargetTuple(addr.getTargetRef(), addr, port));
                }
            }
        }
        return tts;
    }

    private List<ServiceRef> getServiceRefs(Endpoints endpoints) {
        return getTargetTuples(endpoints).stream()
                .map(TargetTuple::toServiceRef)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private final class EndpointsHandler implements ResourceEventHandler<Endpoints> {
        @Override
        public void onAdd(Endpoints endpoints) {
            getServiceRefs(endpoints)
                    .forEach(serviceRef -> notifyAsyncTargetDiscovery(EventKind.FOUND, serviceRef));
        }

        @Override
        public void onUpdate(Endpoints oldEndpoints, Endpoints newEndpoints) {
            Set<ServiceRef> previousRefs = new HashSet<>(getServiceRefs(oldEndpoints));
            Set<ServiceRef> currentRefs = new HashSet<>(getServiceRefs(newEndpoints));

            if (previousRefs.equals(currentRefs)) {
                return;
            }

            ServiceRef.compare(previousRefs).to(currentRefs).updated().stream()
                    .forEach(sr -> notifyAsyncTargetDiscovery(EventKind.MODIFIED, sr));

            ServiceRef.compare(previousRefs).to(currentRefs).added().stream()
                    .forEach(sr -> notifyAsyncTargetDiscovery(EventKind.FOUND, sr));

            ServiceRef.compare(previousRefs).to(currentRefs).removed().stream()
                    .forEach(sr -> notifyAsyncTargetDiscovery(EventKind.LOST, sr));
        }

        @Override
        public void onDelete(Endpoints endpoints, boolean deletedFinalStateUnknown) {
            if (deletedFinalStateUnknown) {
                logger.warn("Deleted final state unknown: {}", endpoints);
                return;
            }
            getServiceRefs(endpoints)
                    .forEach(serviceRef -> notifyAsyncTargetDiscovery(EventKind.LOST, serviceRef));
        }
    }

    private class TargetTuple {
        ObjectReference objRef;
        EndpointAddress addr;
        EndpointPort port;

        TargetTuple(ObjectReference objRef, EndpointAddress addr, EndpointPort port) {
            this.objRef = objRef;
            this.addr = addr;
            this.port = port;
        }

        ServiceRef toServiceRef() {
            Pair<HasMetadata, EnvironmentNode> node =
                    discoveryNodeCache.computeIfAbsent(
                            cacheKey(objRef.getNamespace(), objRef),
                            KubeApiPlatformClient.this::queryForNode);
            HasMetadata podRef = node.getLeft();
            if (node.getRight().getNodeType() != KubernetesNodeType.POD) {
                throw new IllegalStateException();
            }
            if (podRef == null) {
                throw new IllegalStateException();
            }
            try {
                String targetName = objRef.getName();

                String ip = addr.getIp().replaceAll("\\.", "-");
                String namespace = podRef.getMetadata().getNamespace();
                String host = String.format("%s.%s.pod", ip, namespace);

                JMXServiceURL jmxUrl =
                        new JMXServiceURL(
                                "rmi",
                                "",
                                0,
                                "/jndi/rmi://" + host + ':' + port.getPort() + "/jmxrmi");
                ServiceRef serviceRef =
                        new ServiceRef(null, URI.create(jmxUrl.toString()), targetName);
                serviceRef.setLabels(podRef.getMetadata().getLabels());
                serviceRef.setPlatformAnnotations(podRef.getMetadata().getAnnotations());
                serviceRef.setCryostatAnnotations(
                        Map.of(
                                AnnotationKey.REALM,
                                REALM,
                                AnnotationKey.HOST,
                                addr.getIp(),
                                AnnotationKey.PORT,
                                Integer.toString(port.getPort()),
                                AnnotationKey.NAMESPACE,
                                addr.getTargetRef().getNamespace(),
                                AnnotationKey.POD_NAME,
                                addr.getTargetRef().getName()));
                return serviceRef;
            } catch (Exception e) {
                logger.warn(e);
                return null;
            }
        }
    }

    public enum KubernetesNodeType implements NodeType {
        NAMESPACE("Namespace"),
        STATEFULSET(
                "StatefulSet",
                c -> ns -> n -> c.apps().statefulSets().inNamespace(ns).withName(n).get()),
        DAEMONSET(
                "DaemonSet",
                c -> ns -> n -> c.apps().daemonSets().inNamespace(ns).withName(n).get()),
        DEPLOYMENT(
                "Deployment",
                c -> ns -> n -> c.apps().deployments().inNamespace(ns).withName(n).get()),
        // FIXME DeploymentConfig is OpenShift-specific
        DEPLOYMENTCONFIG("DeploymentConfig"),
        REPLICASET(
                "ReplicaSet",
                c -> ns -> n -> c.apps().replicaSets().inNamespace(ns).withName(n).get()),
        REPLICATIONCONTROLLER(
                "ReplicationController",
                c -> ns -> n -> c.replicationControllers().inNamespace(ns).withName(n).get()),
        POD("Pod", c -> ns -> n -> c.pods().inNamespace(ns).withName(n).get()),
        ENDPOINT("Endpoint", c -> ns -> n -> c.endpoints().inNamespace(ns).withName(n).get()),
        ;

        private final String kubernetesKind;
        private final transient Function<
                        KubernetesClient, Function<String, Function<String, ? extends HasMetadata>>>
                getFn;

        KubernetesNodeType(String kubernetesKind) {
            this(kubernetesKind, client -> namespace -> name -> null);
        }

        KubernetesNodeType(
                String kubernetesKind,
                Function<
                                KubernetesClient,
                                Function<String, Function<String, ? extends HasMetadata>>>
                        getFn) {
            this.kubernetesKind = kubernetesKind;
            this.getFn = getFn;
        }

        @Override
        public String getKind() {
            return kubernetesKind;
        }

        public Function<KubernetesClient, Function<String, Function<String, ? extends HasMetadata>>>
                getQueryFunction() {
            return getFn;
        }

        public static KubernetesNodeType fromKubernetesKind(String kubernetesKind) {
            if (kubernetesKind == null) {
                return null;
            }
            for (KubernetesNodeType nt : values()) {
                if (kubernetesKind.equalsIgnoreCase(nt.kubernetesKind)) {
                    return nt;
                }
            }
            return null;
        }

        @Override
        public String toString() {
            return getKind();
        }
    }
}
