/*
 * Copyright The Cryostat Authors
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software (each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.cryostat.discovery;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import javax.script.ScriptException;

import io.cryostat.VerticleDeployer;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.configuration.StoredCredentials;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.discovery.JvmDiscoveryClient.EventKind;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.ServiceRef.AnnotationKey;
import io.cryostat.platform.discovery.AbstractNode;
import io.cryostat.platform.discovery.BaseNodeType;
import io.cryostat.platform.discovery.EnvironmentNode;
import io.cryostat.platform.discovery.TargetNode;
import io.cryostat.recordings.JvmIdHelper;
import io.cryostat.rules.MatchExpressionEvaluator;
import io.cryostat.util.HttpStatusCodeIdentifier;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import dagger.Lazy;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.auth.authentication.UsernamePasswordCredentials;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

public class DiscoveryStorage extends AbstractPlatformClientVerticle {

    public static final URI NO_CALLBACK = null;
    private final Duration pingPeriod;
    private final VerticleDeployer deployer;
    private final Lazy<BuiltInDiscovery> builtin;
    private final PluginInfoDao dao;
    private final Lazy<JvmIdHelper> jvmIdHelper;
    private final Lazy<CredentialsManager> credentialsManager;
    private final Lazy<MatchExpressionEvaluator> matchExpressionEvaluator;
    private final Gson gson;
    private final WebClient http;
    private final Logger logger;
    private long timerId = -1L;

    private final Map<TargetNode, UUID> targetsToUpdate = new HashMap<>();
    private final Map<String, ServiceRef> serviceRefReverseLookup;

    public static final String DISCOVERY_STARTUP_ADDRESS = "discovery-startup";

    DiscoveryStorage(
            VerticleDeployer deployer,
            Duration pingPeriod,
            Lazy<BuiltInDiscovery> builtin,
            PluginInfoDao dao,
            Lazy<JvmIdHelper> jvmIdHelper,
            Lazy<CredentialsManager> credentialsManager,
            Lazy<MatchExpressionEvaluator> matchExpressionEvaluator,
            Gson gson,
            WebClient http,
            Logger logger) {
        this.deployer = deployer;
        this.pingPeriod = pingPeriod;
        this.builtin = builtin;
        this.dao = dao;
        this.jvmIdHelper = jvmIdHelper;
        this.credentialsManager = credentialsManager;
        this.matchExpressionEvaluator = matchExpressionEvaluator;
        this.gson = gson;
        this.http = http;
        this.logger = logger;
        this.serviceRefReverseLookup = new ConcurrentHashMap<>();
        this.addTargetDiscoveryListener(
                tde -> {
                    ServiceRef sr = tde.getServiceRef();
                    switch (tde.getEventKind()) {
                        case FOUND:
                            serviceRefReverseLookup.put(sr.getServiceUri().toString(), sr);
                            break;
                        case LOST:
                            serviceRefReverseLookup.remove(sr.getServiceUri().toString());
                            break;
                        default:
                            throw new IllegalStateException(tde.getEventKind().name());
                    }
                });
    }

    @Override
    public void start(Promise<Void> future) throws Exception {
        pingPrune()
                .onSuccess(
                        cf ->
                                deployer.deploy(builtin.get(), true)
                                        .onSuccess(ar -> future.complete())
                                        .onFailure(t -> future.fail((Throwable) t))
                                        .eventually(
                                                m ->
                                                        getVertx()
                                                                .eventBus()
                                                                .send(
                                                                        DISCOVERY_STARTUP_ADDRESS,
                                                                        "Discovery storage"
                                                                                + " deployed")))
                .onFailure(future::fail);

        this.getLeafNodes().forEach(target -> {
            ServiceRef sr = target.getTarget();
            serviceRefReverseLookup.put(sr.getServiceUri().toString(), sr);
        });

        this.timerId = getVertx().setPeriodic(pingPeriod.toMillis(), i -> pingPrune());
        this.credentialsManager
                .get()
                .addListener(
                        event -> {
                            switch (event.getEventType()) {
                                case ADDED:
                                    Map<TargetNode, UUID> copy = new HashMap<>(targetsToUpdate);
                                    for (var entry : copy.entrySet()) {
                                        try {
                                            if (matchExpressionEvaluator
                                                    .get()
                                                    .applies(
                                                            event.getPayload(),
                                                            entry.getKey().getTarget())) {
                                                targetsToUpdate.remove(entry.getKey());
                                                UUID id = entry.getValue();
                                                PluginInfo plugin = getById(id).orElseThrow();
                                                EnvironmentNode original =
                                                        gson.fromJson(
                                                                plugin.getSubtree(),
                                                                EnvironmentNode.class);
                                                update(id, original.getChildren());
                                            }
                                        } catch (JsonSyntaxException | ScriptException e) {
                                            throw new RuntimeException(e);
                                        }
                                    }
                                    break;
                                case REMOVED:
                                    break;
                                default:
                                    throw new UnsupportedOperationException(
                                            event.getEventType().toString());
                            }
                        });
    }

    @Override
    public void stop() {
        getVertx().cancelTimer(timerId);
    }

    private CompositeFuture pingPrune() {
        List<Future> futures =
                dao.getAll().stream()
                        .map(
                                plugin -> {
                                    UUID key = plugin.getId();
                                    URI uri = plugin.getCallback();
                                    return (Future)
                                            ping(HttpMethod.POST, uri)
                                                    .onSuccess(
                                                            res -> {
                                                                if (!Boolean.TRUE.equals(res)) {
                                                                    removePlugin(key, uri);
                                                                }
                                                            });
                                })
                        .toList();
        return CompositeFuture.join(futures);
    }

    private Future<Boolean> ping(HttpMethod mtd, URI uri) {
        if (Objects.equals(uri, NO_CALLBACK)) {
            return Future.succeededFuture(true);
        }
        HttpRequest<Buffer> req =
                http.request(mtd, uri.getPort(), uri.getHost(), uri.getPath())
                        .ssl("https".equals(uri.getScheme()))
                        .timeout(1_000)
                        .followRedirects(true);
        String userInfo = uri.getUserInfo();
        if (StringUtils.isNotBlank(userInfo) && userInfo.contains(":")) {
            String[] parts = userInfo.split(":");
            if ("storedcredentials".equals(parts[0])) {
                logger.info(
                        "Using stored credentials id:{} referenced in ping callback userinfo",
                        parts[1]);
                Optional<StoredCredentials> opt =
                        credentialsManager.get().getById(Integer.parseInt(parts[1]));
                if (opt.isEmpty()) {
                    logger.warn("Could not find such credentials!");
                    return Future.succeededFuture(false);
                }
                StoredCredentials credentials = opt.get();
                req =
                        req.authentication(
                                new UsernamePasswordCredentials(
                                        credentials.getCredentials().getUsername(),
                                        credentials.getCredentials().getPassword()));
            }
        }
        return req.send()
                .onComplete(
                        ar -> {
                            if (ar.failed()) {
                                logger.info(
                                        "{} {} failed: {}",
                                        mtd,
                                        uri,
                                        ExceptionUtils.getStackTrace(ar.cause()));
                                return;
                            }
                            logger.info(
                                    "{} {} status {}: {}",
                                    mtd,
                                    uri,
                                    ar.result().statusCode(),
                                    ar.result().statusMessage());
                        })
                .map(HttpResponse::statusCode)
                .map(HttpStatusCodeIdentifier::isSuccessCode)
                .otherwise(false);
    }

    public Optional<ServiceRef> lookupServiceByTargetId(String targetId) {
        return Optional.ofNullable(serviceRefReverseLookup.get(targetId));
    }

    public Optional<ServiceRef> lookupServiceByConnectUrl(URI connectUrl) {
        return lookupServiceByTargetId(connectUrl.toString());
    }

    private void removePlugin(UUID uuid, Object label) {
        deregister(uuid);
        logger.info("Stale discovery service {} removed", label);
    }

    public Optional<PluginInfo> getById(UUID id) {
        return dao.get(id);
    }

    public UUID register(String realm, URI callback) throws RegistrationException {
        // FIXME this method should return a Future and be performed async
        Objects.requireNonNull(realm, "realm");
        try {
            CompletableFuture<Boolean> cf = new CompletableFuture<>();
            ping(HttpMethod.GET, callback)
                    .onComplete(ar -> cf.complete(ar.succeeded() && ar.result()));
            if (!cf.get()) {
                throw new Exception("callback ping failure");
            }
            // FIXME it's not great to perform this action as two separate database calls, but we
            // want to have the ID embedded within the node object. The ID is generated by the
            // database when we create the plugin registration record, and the node object is
            // serialized into a column of that record.
            EnvironmentNode initial = new EnvironmentNode(realm, BaseNodeType.REALM);
            UUID id = dao.save(realm, callback, initial).getId();
            EnvironmentNode update =
                    new EnvironmentNode(
                            realm,
                            initial.getNodeType(),
                            mergeLabels(
                                    initial.getLabels(),
                                    Map.of(AnnotationKey.REALM.name(), id.toString())),
                            initial.getChildren());
            PluginInfo updated = dao.update(id, update);
            logger.trace("Discovery Registration: \"{}\" [{}]", realm, id);
            return updated.getId();
        } catch (Exception e) {
            throw new RegistrationException(realm, callback, e, e.getMessage());
        }
    }

    private Map<String, String> mergeLabels(
            Map<String, String> original, Map<String, String> toAdd) {
        Map<String, String> merged = new HashMap<>(original);
        toAdd.entrySet().forEach(entry -> merged.put(entry.getKey(), entry.getValue()));
        return merged;
    }

    private List<AbstractNode> modifyChildrenWithJvmIds(
            UUID id, Collection<? extends AbstractNode> children) {
        List<AbstractNode> modifiedChildren = new ArrayList<>();
        for (AbstractNode child : children) {
            if (child instanceof TargetNode) {
                ServiceRef ref = ((TargetNode) child).getTarget();
                try {
                    ref = jvmIdHelper.get().resolveId(ref);
                } catch (Exception e) {
                    // if Exception is of SSL or JMX Auth, ignore warning and use null jvmId
                    if (!(AbstractAuthenticatedRequestHandler.isJmxAuthFailure(e)
                            || AbstractAuthenticatedRequestHandler.isJmxSslFailure(e))) {
                        logger.info("Ignoring target node [{}]", child.getName());
                        continue;
                    }
                    logger.info("Update node [{}] with null jvmId", child.getName());
                    targetsToUpdate.putIfAbsent((TargetNode) child, id);
                }
                child = new TargetNode(child.getNodeType(), ref, child.getLabels());
                modifiedChildren.add(child);
            } else if (child instanceof EnvironmentNode) {
                modifiedChildren.add(
                        new EnvironmentNode(
                                child.getName(),
                                child.getNodeType(),
                                child.getLabels(),
                                modifyChildrenWithJvmIds(
                                        id, ((EnvironmentNode) child).getChildren())));
            } else {
                throw new IllegalArgumentException(child.getClass().getCanonicalName());
            }
        }
        return modifiedChildren;
    }

    public List<? extends AbstractNode> update(
            UUID id, Collection<? extends AbstractNode> children, boolean notify) {
        // FIXME what about required SecurityContext fields on TargetNodes? We find the JVM ID here
        // and use the JMX Service URL for the source, but we assume there is a NAMESPACE annotation
        // that the AuthManager can use to reference the context. The PlatformClients through
        // built-in discovery can be made to always supply this but what if some external plugin
        // does not?
        var updatedChildren =
                modifyChildrenWithJvmIds(id, Objects.requireNonNull(children, "children"));

        PluginInfo plugin = dao.get(id).orElseThrow(() -> new NotFoundException(id));

        EnvironmentNode originalTree = gson.fromJson(plugin.getSubtree(), EnvironmentNode.class);
        plugin = dao.update(id, updatedChildren);
        logger.trace("Discovery Update {} ({}): {}", id, plugin.getRealm(), updatedChildren);
        EnvironmentNode currentTree = gson.fromJson(plugin.getSubtree(), EnvironmentNode.class);

        List<ServiceRef> previousRefs = getRefsFromLeaves(findLeavesFrom(originalTree));
        List<ServiceRef> currentRefs = getRefsFromLeaves(findLeavesFrom(currentTree));

        ServiceRef.compare(previousRefs).to(currentRefs).updated().stream()
                .forEach(sr -> notifyAsyncTargetDiscovery(EventKind.MODIFIED, sr));

        ServiceRef.compare(previousRefs).to(currentRefs).added().stream()
                .forEach(sr -> notifyAsyncTargetDiscovery(EventKind.FOUND, sr));

        ServiceRef.compare(previousRefs).to(currentRefs).removed().stream()
                .forEach(sr -> notifyAsyncTargetDiscovery(EventKind.LOST, sr));
        ;

        return currentTree.getChildren();
    }

    public PluginInfo deregister(UUID id) {
        PluginInfo plugin = dao.get(id).orElseThrow(() -> new NotFoundException(id));
        dao.delete(id);
        findLeavesFrom(gson.fromJson(plugin.getSubtree(), EnvironmentNode.class)).stream()
                .map(TargetNode::getTarget)
                .forEach(sr -> notifyAsyncTargetDiscovery(EventKind.LOST, sr));
        return plugin;
    }

    public EnvironmentNode getDiscoveryTree() {
        List<EnvironmentNode> realms =
                dao.getAll().stream()
                        .map(PluginInfo::getSubtree)
                        .map(s -> gson.fromJson(s, EnvironmentNode.class))
                        .sorted((s1, s2) -> s1.compareTo(s2))
                        .toList();
        return new EnvironmentNode(
                "Universe", BaseNodeType.UNIVERSE, Collections.emptyMap(), realms);
    }

    private List<TargetNode> getLeafNodes() {
        return findLeavesFrom(getDiscoveryTree());
    }

    @Override
    public List<ServiceRef> listDiscoverableServices() {
        return getLeafNodes().stream().map(TargetNode::getTarget).toList();
    }

    public Optional<PluginInfo> getBuiltInPluginByRealm(String realm) {
        return dao.getByRealm(realm).stream()
                .filter(plugin -> plugin.getRealm().equals(realm))
                .filter(plugin -> Objects.equals(plugin.getCallback(), NO_CALLBACK))
                .findFirst();
    }

    public List<ServiceRef> listDiscoverableServices(PluginInfo plugin) {
        return findLeavesFrom(gson.fromJson(plugin.getSubtree(), EnvironmentNode.class)).stream()
                .map(TargetNode::getTarget)
                .toList();
    }

    private List<TargetNode> findLeavesFrom(AbstractNode node) {
        if (node instanceof TargetNode) {
            return List.of((TargetNode) node);
        }
        if (node instanceof EnvironmentNode) {
            EnvironmentNode environment = (EnvironmentNode) node;
            List<TargetNode> targets = new ArrayList<>();
            environment.getChildren().stream().map(this::findLeavesFrom).forEach(targets::addAll);
            return targets;
        }
        throw new IllegalArgumentException(node.getClass().getCanonicalName());
    }

    public List<ServiceRef> getRefsFromLeaves(List<TargetNode> leaves) {
        final List<ServiceRef> refs = new ArrayList<>();
        leaves.stream().map(TargetNode::getTarget).forEach(r -> refs.add(r));
        return refs;
    }

    public static class NotFoundException extends RuntimeException {
        NotFoundException(UUID id) {
            super(String.format("Unknown registration id: [%s]", id.toString()));
        }
    }
}
