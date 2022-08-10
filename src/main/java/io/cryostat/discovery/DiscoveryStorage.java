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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import io.cryostat.VerticleDeployer;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.discovery.JvmDiscoveryClient.EventKind;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.discovery.AbstractNode;
import io.cryostat.platform.discovery.BaseNodeType;
import io.cryostat.platform.discovery.EnvironmentNode;
import io.cryostat.platform.discovery.TargetNode;
import io.cryostat.util.HttpStatusCodeIdentifier;

import com.google.gson.Gson;
import dagger.Lazy;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.ext.web.client.WebClient;

public class DiscoveryStorage extends AbstractPlatformClientVerticle {

    public static final URI NO_CALLBACK = null;
    private final VerticleDeployer deployer;
    private final Lazy<BuiltInDiscovery> builtin;
    private final PluginInfoDao dao;
    private final Gson gson;
    private final WebClient http;
    private final Logger logger;

    DiscoveryStorage(
            VerticleDeployer deployer,
            Lazy<BuiltInDiscovery> builtin,
            PluginInfoDao dao,
            Gson gson,
            WebClient http,
            Logger logger) {
        this.deployer = deployer;
        this.builtin = builtin;
        this.dao = dao;
        this.gson = gson;
        this.http = http;
        this.logger = logger;
    }

    @Override
    public void start(Promise<Void> future) throws Exception {
        List<Future> futures =
                dao.getAll().stream()
                        .map(
                                plugin -> {
                                    UUID key = plugin.getId();
                                    URI uri = plugin.getCallback();
                                    if (uri == NO_CALLBACK) {
                                        removePlugin(key, key);
                                        return (Future) Future.succeededFuture();
                                    }
                                    return (Future)
                                            http.postAbs(uri.toString())
                                                    .timeout(1_000)
                                                    .followRedirects(true)
                                                    .send()
                                                    .onSuccess(
                                                            res -> {
                                                                if (!HttpStatusCodeIdentifier
                                                                        .isSuccessCode(
                                                                                res.statusCode())) {
                                                                    removePlugin(key, uri);
                                                                    return;
                                                                }
                                                                logger.info(
                                                                        "Found discovery service"
                                                                                + " {}",
                                                                        uri);
                                                            })
                                                    .onFailure(t -> removePlugin(key, uri))
                                                    .recover(t -> Future.succeededFuture());
                                })
                        .toList();
        CompositeFuture.join(futures)
                .onSuccess(
                        cf ->
                                deployer.deploy(builtin.get(), true)
                                        .onSuccess(ar -> future.complete())
                                        .onFailure(t -> future.fail((Throwable) t)))
                .onFailure(future::fail);
    }

    private void removePlugin(UUID uuid, Object label) {
        dao.delete(uuid);
        logger.info("Stale discovery service {} removed", label);
    }

    public UUID register(String realm, URI callback) throws RegistrationException {
        try {
            EnvironmentNode subtree = new EnvironmentNode(realm, BaseNodeType.REALM);
            UUID id = dao.save(realm, callback, subtree).getId();
            logger.trace("Discovery Registration: \"{}\" [{}]", realm, id);
            return id;
        } catch (Exception e) {
            throw new RegistrationException(e);
        }
    }

    public Set<? extends AbstractNode> update(UUID id, Set<? extends AbstractNode> children) {
        PluginInfo plugin = dao.get(id).orElseThrow(() -> new NotFoundException(id));
        logger.trace("Discovery Update {} ({}): {}", id, plugin.getRealm(), children);
        EnvironmentNode original = gson.fromJson(plugin.getSubtree(), EnvironmentNode.class);
        plugin = dao.update(id, Objects.requireNonNull(children));
        EnvironmentNode currentTree = gson.fromJson(plugin.getSubtree(), EnvironmentNode.class);

        Set<TargetNode> previousLeaves = findLeavesFrom(original);
        Set<TargetNode> currentLeaves = findLeavesFrom(currentTree);

        Set<TargetNode> added = new HashSet<>(currentLeaves);
        added.removeAll(previousLeaves);

        Set<TargetNode> removed = new HashSet<>(previousLeaves);
        removed.removeAll(currentLeaves);

        added.stream()
                .map(TargetNode::getTarget)
                .forEach(sr -> notifyAsyncTargetDiscovery(EventKind.FOUND, sr));
        removed.stream()
                .map(TargetNode::getTarget)
                .forEach(sr -> notifyAsyncTargetDiscovery(EventKind.LOST, sr));

        return original.getChildren();
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
                        .toList();
        return new EnvironmentNode(
                "Universe", BaseNodeType.UNIVERSE, Collections.emptyMap(), realms);
    }

    private Set<TargetNode> getLeafNodes() {
        return findLeavesFrom(getDiscoveryTree());
    }

    @Override
    public List<ServiceRef> listDiscoverableServices() {
        return getLeafNodes().stream().map(TargetNode::getTarget).toList();
    }

    private Set<TargetNode> findLeavesFrom(AbstractNode node) {
        if (node instanceof TargetNode) {
            return Set.of((TargetNode) node);
        }
        if (node instanceof EnvironmentNode) {
            EnvironmentNode environment = (EnvironmentNode) node;
            Set<TargetNode> targets = new HashSet<>();
            environment.getChildren().stream().map(this::findLeavesFrom).forEach(targets::addAll);
            return targets;
        }
        throw new IllegalArgumentException(node.getClass().getCanonicalName());
    }

    public static class NotFoundException extends RuntimeException {
        NotFoundException(UUID id) {
            super(String.format("Unknown registration id: [%s]", id.toString()));
        }
    }
}
