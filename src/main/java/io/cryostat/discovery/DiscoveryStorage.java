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
package io.cryostat.discovery;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;

import javax.script.ScriptException;

import io.cryostat.VerticleDeployer;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.configuration.StoredCredentials;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.discovery.JvmDiscoveryClient.EventKind;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.ServiceRef.AnnotationKey;
import io.cryostat.platform.discovery.AbstractNode;
import io.cryostat.platform.discovery.BaseNodeType;
import io.cryostat.platform.discovery.EnvironmentNode;
import io.cryostat.platform.discovery.TargetNode;
import io.cryostat.recordings.JvmIdHelper;
import io.cryostat.recordings.JvmIdHelper.JvmIdGetException;
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
    private final ExecutorService executor;
    private final Lazy<BuiltInDiscovery> builtin;
    private final PluginInfoDao dao;
    private final Lazy<JvmIdHelper> jvmIdHelper;
    private final Lazy<CredentialsManager> credentialsManager;
    private final Lazy<MatchExpressionEvaluator> matchExpressionEvaluator;
    private final Gson gson;
    private final WebClient http;
    private final Logger logger;
    private long pluginPruneTimerId = -1L;
    private long targetRetryTimeId = -1L;

    private final Map<TargetNode, UUID> nonConnectableTargets = new HashMap<>();

    public static final String DISCOVERY_STARTUP_ADDRESS = "discovery-startup";

    DiscoveryStorage(
            VerticleDeployer deployer,
            ExecutorService executor,
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
        this.executor = executor;
        this.pingPeriod = pingPeriod;
        this.builtin = builtin;
        this.dao = dao;
        this.jvmIdHelper = jvmIdHelper;
        this.credentialsManager = credentialsManager;
        this.matchExpressionEvaluator = matchExpressionEvaluator;
        this.gson = gson;
        this.http = http;
        this.logger = logger;
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

        this.pluginPruneTimerId = getVertx().setPeriodic(pingPeriod.toMillis(), i -> pingPrune());
        this.targetRetryTimeId =
                getVertx()
                        .setPeriodic(
                                // TODO make this configurable, use an exponential backoff, have a
                                // maximum retry policy, etc.
                                15_000,
                                i -> {
                                    testNonConnectedTargets(
                                            entry -> {
                                                TargetNode targetNode = entry.getKey();
                                                try {
                                                    String id =
                                                            jvmIdHelper
                                                                    .get()
                                                                    .resolveId(
                                                                            targetNode.getTarget())
                                                                    .getJvmId();
                                                    return StringUtils.isNotBlank(id);
                                                } catch (JvmIdGetException e) {
                                                    logger.info(
                                                            "Retain null jvmId for node [{}]",
                                                            targetNode.getName());
                                                    logger.info(e);
                                                    return false;
                                                }
                                            });
                                });
        this.credentialsManager
                .get()
                .addListener(
                        event -> {
                            switch (event.getEventType()) {
                                case ADDED:
                                    testNonConnectedTargets(
                                            entry -> {
                                                try {
                                                    return matchExpressionEvaluator
                                                            .get()
                                                            .applies(
                                                                    event.getPayload(),
                                                                    entry.getKey().getTarget());
                                                } catch (ScriptException e) {
                                                    logger.error(e);
                                                    return false;
                                                }
                                            });
                                    break;
                                case REMOVED:
                                    break;
                                default:
                                    throw new UnsupportedOperationException(
                                            event.getEventType().toString());
                            }
                        });
    }

    private void testNonConnectedTargets(Predicate<Entry<TargetNode, UUID>> predicate) {
        executor.execute(
                () -> {
                    Map<TargetNode, UUID> copy = new HashMap<>(nonConnectableTargets);
                    for (var entry : copy.entrySet()) {
                        try {
                            if (predicate.test(entry)) {
                                nonConnectableTargets.remove(entry.getKey());
                                UUID id = entry.getValue();
                                PluginInfo plugin = getById(id).orElseThrow();
                                EnvironmentNode original =
                                        gson.fromJson(plugin.getSubtree(), EnvironmentNode.class);
                                update(id, original.getChildren());
                            }
                        } catch (JsonSyntaxException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
    }

    @Override
    public void stop() {
        getVertx().cancelTimer(pluginPruneTimerId);
        getVertx().cancelTimer(targetRetryTimeId);
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
        Optional<StoredCredentials> opt = getStoredCredentials(uri);
        if (opt.isPresent()) {
            StoredCredentials credentials = opt.get();
            logger.info(
                    "Using stored credentials id:{} referenced in ping callback userinfo",
                    credentials.getId());
            req =
                    req.authentication(
                            new UsernamePasswordCredentials(
                                    credentials.getCredentials().getUsername(),
                                    credentials.getCredentials().getPassword()));
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

    private Optional<StoredCredentials> getStoredCredentials(URI uri) {
        if (uri == NO_CALLBACK) {
            return Optional.empty();
        }
        String userInfo = uri.getUserInfo();
        if (StringUtils.isNotBlank(userInfo) && userInfo.contains(":")) {
            String[] parts = userInfo.split(":");
            if ("storedcredentials".equals(parts[0])) {

                Optional<StoredCredentials> opt =
                        credentialsManager.get().getById(Integer.parseInt(parts[1]));
                if (opt.isEmpty()) {
                    logger.warn("Could not find stored credentials with id:{} !", parts[1]);
                }
                return opt;
            }
        }
        return Optional.empty();
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
                    child = new TargetNode(child.getNodeType(), ref, child.getLabels());
                } catch (Exception e) {
                    logger.info("Update node [{}] with null jvmId", child.getName());
                    logger.info(e);
                    nonConnectableTargets.putIfAbsent((TargetNode) child, id);
                }
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
            UUID id, Collection<? extends AbstractNode> children) {
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
        getStoredCredentials(plugin.getCallback())
                .ifPresent(sc -> credentialsManager.get().delete(sc.getId()));
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
