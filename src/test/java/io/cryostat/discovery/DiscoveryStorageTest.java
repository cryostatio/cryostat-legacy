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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import javax.inject.Singleton;

import io.cryostat.MainModule;
import io.cryostat.MockVertx;
import io.cryostat.VerticleDeployer;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.discovery.JvmDiscoveryClient.EventKind;
import io.cryostat.discovery.DiscoveryStorage.NotFoundException;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.TargetDiscoveryEvent;
import io.cryostat.platform.discovery.AbstractNode;
import io.cryostat.platform.discovery.BaseNodeType;
import io.cryostat.platform.discovery.EnvironmentNode;
import io.cryostat.platform.discovery.TargetNode;
import io.cryostat.recordings.JvmIdHelper;
import io.cryostat.recordings.JvmIdHelper.JvmIdGetException;

import com.google.gson.Gson;
import dagger.Component;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class DiscoveryStorageTest {

    @Mock VerticleDeployer deployer;
    @Mock BuiltInDiscovery builtin;
    @Mock PluginInfoDao dao;
    @Mock JvmIdHelper jvmIdHelper;
    @Mock WebClient http;
    @Mock Logger logger;
    Vertx vertx = MockVertx.vertx();
    Gson gson = MainModule.provideGson(logger);

    @Singleton
    @Component(modules = {MainModule.class})
    interface Client {
        Gson gson();

        @Component.Builder
        interface Builder {
            Client build();
        }
    }

    DiscoveryStorage storage;

    @BeforeEach
    void setup() {
        Client client = DaggerDiscoveryStorageTest_Client.builder().build();
        this.gson = client.gson();
        this.storage =
                new DiscoveryStorage(
                        deployer,
                        Duration.ofMinutes(5),
                        () -> builtin,
                        dao,
                        () -> jvmIdHelper,
                        gson,
                        http,
                        logger);
        this.storage.init(vertx, null);
    }

    @Nested
    class OnStart {

        @Test
        void immediatelyDeploysBuiltinIfDaoEmpty() throws Exception {
            Mockito.when(dao.getAll()).thenReturn(List.of());
            Mockito.when(deployer.deploy(Mockito.any(), Mockito.anyBoolean()))
                    .thenReturn(Future.succeededFuture());
            Mockito.verifyNoInteractions(deployer);

            CompletableFuture<Void> f = new CompletableFuture<>();
            Promise<Void> p = Promise.promise();
            storage.start(p);
            p.future()
                    .onComplete(
                            ar -> {
                                if (ar.failed()) {
                                    f.completeExceptionally(ar.cause());
                                    return;
                                }
                                Mockito.verify(deployer, Mockito.times(1)).deploy(builtin, true);
                                f.complete(null);
                            });
            f.join();
        }

        @Test
        void failsStartupIfDeployerFails() throws Exception {
            Mockito.when(dao.getAll()).thenReturn(List.of());
            Mockito.when(deployer.deploy(Mockito.any(), Mockito.anyBoolean()))
                    .thenReturn(Future.failedFuture("test failure"));
            Mockito.verifyNoInteractions(deployer);

            CompletableFuture<Void> f = new CompletableFuture<>();
            Promise<Void> p = Promise.promise();
            p.future()
                    .onComplete(
                            ar -> {
                                Mockito.verify(deployer, Mockito.times(1)).deploy(builtin, true);
                                MatcherAssert.assertThat(ar.cause(), Matchers.notNullValue());
                                f.complete(null);
                            });
            storage.start(p);
            f.join();
        }

        @Test
        void removesPluginsIfCallbackRejected() throws Exception {
            Mockito.when(deployer.deploy(Mockito.any(), Mockito.anyBoolean()))
                    .thenReturn(Future.succeededFuture());
            EnvironmentNode realm =
                    new EnvironmentNode("realm", BaseNodeType.REALM, Map.of(), Set.of());
            PluginInfo plugin =
                    new PluginInfo(
                            "test-realm", URI.create("http://example.com"), gson.toJson(realm));
            UUID id = UUID.randomUUID();
            plugin.setId(id);
            Mockito.when(dao.get(id)).thenReturn(Optional.of(plugin));
            Mockito.when(dao.getAll()).thenReturn(List.of(plugin));

            HttpRequest<Buffer> req = Mockito.mock(HttpRequest.class);
            Mockito.when(
                            http.request(
                                    Mockito.any(HttpMethod.class),
                                    Mockito.anyInt(),
                                    Mockito.anyString(),
                                    Mockito.anyString()))
                    .thenReturn(req);
            Mockito.when(req.ssl(Mockito.anyBoolean())).thenReturn(req);
            Mockito.when(req.timeout(Mockito.anyLong())).thenReturn(req);
            Mockito.when(req.followRedirects(Mockito.anyBoolean())).thenReturn(req);

            HttpResponse<Buffer> res = Mockito.mock(HttpResponse.class);
            Mockito.when(res.statusCode()).thenReturn(500);
            Future<HttpResponse<Buffer>> future = Future.succeededFuture(res);
            Mockito.when(req.send()).thenReturn(future);

            CompletableFuture<Void> f = new CompletableFuture<>();
            Promise<Void> p = Promise.promise();
            p.future().onComplete(ar -> f.complete(null));
            storage.start(p);
            f.join();

            Mockito.verify(dao).delete(plugin.getId());
        }

        @Test
        void removesPluginsIfCallbackFails() throws Exception {
            Mockito.when(deployer.deploy(Mockito.any(), Mockito.anyBoolean()))
                    .thenReturn(Future.succeededFuture());
            EnvironmentNode realm =
                    new EnvironmentNode("realm", BaseNodeType.REALM, Map.of(), Set.of());
            PluginInfo plugin =
                    new PluginInfo(
                            "test-realm", URI.create("http://example.com"), gson.toJson(realm));
            UUID id = UUID.randomUUID();
            plugin.setId(id);
            Mockito.when(dao.get(id)).thenReturn(Optional.of(plugin));
            Mockito.when(dao.getAll()).thenReturn(List.of(plugin));

            HttpRequest<Buffer> req = Mockito.mock(HttpRequest.class);
            Mockito.when(
                            http.request(
                                    Mockito.any(HttpMethod.class),
                                    Mockito.anyInt(),
                                    Mockito.anyString(),
                                    Mockito.anyString()))
                    .thenReturn(req);
            Mockito.when(req.ssl(Mockito.anyBoolean())).thenReturn(req);
            Mockito.when(req.timeout(Mockito.anyLong())).thenReturn(req);
            Mockito.when(req.followRedirects(Mockito.anyBoolean())).thenReturn(req);

            HttpResponse<Buffer> res = Mockito.mock(HttpResponse.class);
            Future<HttpResponse<Buffer>> future = Future.failedFuture("test failure");
            Mockito.when(req.send()).thenReturn(future);

            CompletableFuture<Void> f = new CompletableFuture<>();
            Promise<Void> p = Promise.promise();
            p.future().onComplete(ar -> f.complete(null));
            storage.start(p);
            f.join();

            Mockito.verify(dao).delete(plugin.getId());
        }

        @Test
        void retainsPluginIfCallbackSucceeds() throws Exception {
            Mockito.when(deployer.deploy(Mockito.any(), Mockito.anyBoolean()))
                    .thenReturn(Future.succeededFuture());
            PluginInfo plugin =
                    new PluginInfo("test-realm", URI.create("http://example.com"), "[]");
            plugin.setId(UUID.randomUUID());
            Mockito.when(dao.getAll()).thenReturn(List.of(plugin));

            HttpRequest<Buffer> req = Mockito.mock(HttpRequest.class);
            Mockito.when(
                            http.request(
                                    Mockito.any(HttpMethod.class),
                                    Mockito.anyInt(),
                                    Mockito.anyString(),
                                    Mockito.anyString()))
                    .thenReturn(req);
            Mockito.when(req.ssl(Mockito.anyBoolean())).thenReturn(req);
            Mockito.when(req.timeout(Mockito.anyLong())).thenReturn(req);
            Mockito.when(req.followRedirects(Mockito.anyBoolean())).thenReturn(req);

            HttpResponse<Buffer> res = Mockito.mock(HttpResponse.class);
            Mockito.when(res.statusCode()).thenReturn(200);
            Future<HttpResponse<Buffer>> future = Future.succeededFuture(res);
            Mockito.when(req.send()).thenReturn(future);

            CompletableFuture<Void> f = new CompletableFuture<>();
            Promise<Void> p = Promise.promise();
            p.future().onComplete(ar -> f.complete(null));
            storage.start(p);
            f.join();

            Mockito.verify(dao, Mockito.never()).delete(plugin.getId());
        }
    }

    @Nested
    class Registration {

        @Test
        void wrapsExceptions() throws RegistrationException {
            Mockito.when(
                            dao.save(
                                    Mockito.anyString(),
                                    Mockito.any(URI.class),
                                    Mockito.any(EnvironmentNode.class)))
                    .thenThrow(ConstraintViolationException.class);

            HttpRequest<Buffer> req = Mockito.mock(HttpRequest.class);
            Mockito.when(
                            http.request(
                                    Mockito.any(HttpMethod.class),
                                    Mockito.anyInt(),
                                    Mockito.anyString(),
                                    Mockito.anyString()))
                    .thenReturn(req);
            Mockito.when(req.ssl(Mockito.anyBoolean())).thenReturn(req);
            Mockito.when(req.timeout(Mockito.anyLong())).thenReturn(req);
            Mockito.when(req.followRedirects(Mockito.anyBoolean())).thenReturn(req);

            HttpResponse<Buffer> res = Mockito.mock(HttpResponse.class);
            Mockito.when(res.statusCode()).thenReturn(200);
            Future<HttpResponse<Buffer>> future = Future.succeededFuture(res);
            Mockito.when(req.send()).thenReturn(future);

            Exception ex =
                    Assertions.assertThrows(
                            Exception.class,
                            () -> storage.register("test-realm", URI.create("http://example.com")));
            MatcherAssert.assertThat(ex, Matchers.isA(RegistrationException.class));
            MatcherAssert.assertThat(
                    ex.getCause(), Matchers.isA(ConstraintViolationException.class));
        }

        @Test
        void storesInDaoAndReturnsId() throws RegistrationException {
            Mockito.when(
                            dao.save(
                                    Mockito.anyString(),
                                    Mockito.any(URI.class),
                                    Mockito.any(EnvironmentNode.class)))
                    .thenAnswer(
                            new Answer<PluginInfo>() {
                                @Override
                                public PluginInfo answer(InvocationOnMock invocation)
                                        throws Throwable {
                                    String realm = (String) invocation.getArgument(0);
                                    URI callback = (URI) invocation.getArgument(1);
                                    EnvironmentNode subtree =
                                            (EnvironmentNode) invocation.getArgument(2);
                                    UUID id = UUID.randomUUID();
                                    PluginInfo plugin =
                                            new PluginInfo(realm, callback, gson.toJson(subtree));
                                    plugin.setId(id);
                                    return plugin;
                                }
                            });
            Mockito.when(dao.update(Mockito.any(UUID.class), Mockito.any(EnvironmentNode.class)))
                    .thenAnswer(
                            new Answer<PluginInfo>() {

                                @Override
                                public PluginInfo answer(InvocationOnMock invocation)
                                        throws Throwable {
                                    UUID id = invocation.getArgument(0);
                                    EnvironmentNode subtree = invocation.getArgument(1);
                                    PluginInfo plugin =
                                            new PluginInfo(
                                                    "updated-realm", null, gson.toJson(subtree));
                                    plugin.setId(id);
                                    return plugin;
                                }
                            });

            HttpRequest<Buffer> req = Mockito.mock(HttpRequest.class);
            Mockito.when(
                            http.request(
                                    Mockito.any(HttpMethod.class),
                                    Mockito.anyInt(),
                                    Mockito.anyString(),
                                    Mockito.anyString()))
                    .thenReturn(req);
            Mockito.when(req.ssl(Mockito.anyBoolean())).thenReturn(req);
            Mockito.when(req.timeout(Mockito.anyLong())).thenReturn(req);
            Mockito.when(req.followRedirects(Mockito.anyBoolean())).thenReturn(req);

            HttpResponse<Buffer> res = Mockito.mock(HttpResponse.class);
            Mockito.when(res.statusCode()).thenReturn(200);
            Future<HttpResponse<Buffer>> future = Future.succeededFuture(res);
            Mockito.when(req.send()).thenReturn(future);

            UUID id = storage.register("test-realm", URI.create("http://example.com"));
            MatcherAssert.assertThat(id, Matchers.notNullValue());

            ArgumentCaptor<String> realmCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<URI> callbackCaptor = ArgumentCaptor.forClass(URI.class);
            ArgumentCaptor<EnvironmentNode> subtreeCaptor =
                    ArgumentCaptor.forClass(EnvironmentNode.class);

            Mockito.verify(dao)
                    .save(realmCaptor.capture(), callbackCaptor.capture(), subtreeCaptor.capture());

            String realm = realmCaptor.getValue();
            URI callback = callbackCaptor.getValue();
            EnvironmentNode subtree = subtreeCaptor.getValue();

            MatcherAssert.assertThat(realm, Matchers.equalTo("test-realm"));
            MatcherAssert.assertThat(callback, Matchers.equalTo(URI.create("http://example.com")));
            MatcherAssert.assertThat(subtree.getName(), Matchers.equalTo("test-realm"));
            MatcherAssert.assertThat(subtree.getNodeType(), Matchers.equalTo(BaseNodeType.REALM));
            MatcherAssert.assertThat(subtree.getLabels().entrySet(), Matchers.empty());
            MatcherAssert.assertThat(subtree.getChildren(), Matchers.empty());
        }
    }

    @Nested
    class Updating {

        @Test
        void throwsIfUuidNull() {
            Mockito.when(dao.get(Mockito.isNull())).thenThrow(NullPointerException.class);
            Assertions.assertThrows(
                    NullPointerException.class, () -> storage.update(null, Set.of()));
        }

        @Test
        void throwsIfInvalidIdGiven() {
            Assertions.assertThrows(
                    NotFoundException.class, () -> storage.update(UUID.randomUUID(), Set.of()));
        }

        @Test
        void throwsIfChildrenNull() {
            UUID id = UUID.randomUUID();
            Assertions.assertThrows(NullPointerException.class, () -> storage.update(id, null));
        }

        @Test
        void updatesDaoAndEmitsFoundAndLostNotifications() throws Exception {
            ServiceRef prevServiceRef =
                    new ServiceRef(
                            "id",
                            URI.create("service:jmx:rmi:///jndi/rmi://localhost/jmxrmi"),
                            "prevServiceRef");

            TargetNode prevTarget = new TargetNode(BaseNodeType.JVM, prevServiceRef);
            EnvironmentNode prev =
                    new EnvironmentNode("prev", BaseNodeType.REALM, Map.of(), Set.of(prevTarget));

            ServiceRef nextServiceRef =
                    new ServiceRef(
                            "id",
                            URI.create("service:jmx:rmi:///jndi/rmi://localhost/jmxrmi"),
                            "nextServiceRef");
            TargetNode nextTarget = new TargetNode(BaseNodeType.JVM, nextServiceRef);
            EnvironmentNode next =
                    new EnvironmentNode("next", BaseNodeType.REALM, Map.of(), Set.of(nextTarget));
            Mockito.when(jvmIdHelper.resolveId(Mockito.any())).thenReturn(nextServiceRef);

            UUID id = UUID.randomUUID();
            PluginInfo prevPlugin =
                    new PluginInfo(
                            "test-realm", URI.create("http://example.com"), gson.toJson(prev));
            PluginInfo nextPlugin =
                    new PluginInfo(
                            "test-realm", URI.create("http://example.com"), gson.toJson(next));
            Mockito.when(dao.get(Mockito.eq(id))).thenReturn(Optional.of(prevPlugin));
            Mockito.when(dao.update(Mockito.any(), Mockito.any(Collection.class)))
                    .thenReturn(nextPlugin);

            List<TargetDiscoveryEvent> discoveryEvents = new ArrayList<>();
            storage.addTargetDiscoveryListener(discoveryEvents::add);

            List<? extends AbstractNode> updatedChildren = storage.update(id, List.of(nextTarget));

            MatcherAssert.assertThat(updatedChildren, Matchers.equalTo(List.of(nextTarget)));
            MatcherAssert.assertThat(discoveryEvents, Matchers.hasSize(2));

            TargetDiscoveryEvent foundEvent =
                    new TargetDiscoveryEvent(EventKind.FOUND, nextServiceRef);
            TargetDiscoveryEvent lostEvent =
                    new TargetDiscoveryEvent(EventKind.LOST, prevServiceRef);
            MatcherAssert.assertThat(
                    discoveryEvents, Matchers.containsInRelativeOrder(foundEvent, lostEvent));
        }
    }

    @Nested
    class Deregistration {

        @Test
        void throwsIfUuidNull() {
            Mockito.when(dao.get(Mockito.isNull())).thenThrow(NullPointerException.class);
            Assertions.assertThrows(NullPointerException.class, () -> storage.deregister(null));
        }

        @Test
        void throwsIfUuidInvalid() {
            Mockito.when(dao.get(Mockito.any(UUID.class))).thenReturn(Optional.empty());
            Assertions.assertThrows(
                    NotFoundException.class, () -> storage.deregister(UUID.randomUUID()));
        }

        @Test
        void updatesDaoAndEmitsLostNotification() {
            UUID id = UUID.randomUUID();

            ServiceRef serviceRef1 =
                    new ServiceRef(
                            "id",
                            URI.create("service:jmx:rmi:///jndi/rmi://localhost:1/jmxrmi"),
                            "serviceRef1");
            ServiceRef serviceRef2 =
                    new ServiceRef(
                            "id",
                            URI.create("service:jmx:rmi:///jndi/rmi://localhost:2/jmxrmi"),
                            "serviceRef2");
            TargetNode target1 = new TargetNode(BaseNodeType.JVM, serviceRef1);
            TargetNode target2 = new TargetNode(BaseNodeType.JVM, serviceRef2);
            EnvironmentNode subtree =
                    new EnvironmentNode(
                            "next", BaseNodeType.REALM, Map.of(), Set.of(target1, target2));
            PluginInfo plugin = new PluginInfo();
            plugin.setSubtree(gson.toJson(subtree));

            Mockito.when(dao.get(id)).thenReturn(Optional.of(plugin));

            List<TargetDiscoveryEvent> discoveryEvents = new ArrayList<>();
            storage.addTargetDiscoveryListener(discoveryEvents::add);

            PluginInfo updatedPlugin = storage.deregister(id);
            MatcherAssert.assertThat(updatedPlugin, Matchers.sameInstance(plugin));

            MatcherAssert.assertThat(discoveryEvents, Matchers.hasSize(2));
            TargetDiscoveryEvent lostEvent1 = new TargetDiscoveryEvent(EventKind.LOST, serviceRef1);
            TargetDiscoveryEvent lostEvent2 = new TargetDiscoveryEvent(EventKind.LOST, serviceRef2);
            MatcherAssert.assertThat(
                    discoveryEvents, Matchers.containsInAnyOrder(lostEvent1, lostEvent2));
        }
    }

    @Nested
    class DiscoveryTree {

        @Test
        void returnsEmptyUniverseIfDaoEmpty() {
            Mockito.when(dao.getAll()).thenReturn(List.of());

            EnvironmentNode tree = storage.getDiscoveryTree();

            MatcherAssert.assertThat(tree.getName(), Matchers.equalTo("Universe"));
            MatcherAssert.assertThat(tree.getNodeType(), Matchers.equalTo(BaseNodeType.UNIVERSE));
            MatcherAssert.assertThat(tree.getLabels(), Matchers.equalTo(Map.of()));
            MatcherAssert.assertThat(tree.getChildren(), Matchers.equalTo(List.of()));
        }

        @Test
        void returnsExpectedSubtree() {
            PluginInfo plugin1 = new PluginInfo();
            TargetNode leaf1 =
                    new TargetNode(
                            BaseNodeType.JVM,
                            new ServiceRef(
                                    "id",
                                    URI.create("service:jmx:rmi:///jndi/rmi://leaf:1/jmxrmi"),
                                    "leaf1"));
            TargetNode leaf2 =
                    new TargetNode(
                            BaseNodeType.JVM,
                            new ServiceRef(
                                    "id",
                                    URI.create("service:jmx:rmi:///jndi/rmi://leaf:2/jmxrmi"),
                                    "leaf2"));
            EnvironmentNode realm1 =
                    new EnvironmentNode(
                            "realm1", BaseNodeType.REALM, Map.of(), Set.of(leaf1, leaf2));
            plugin1.setSubtree(gson.toJson(realm1));

            PluginInfo plugin2 = new PluginInfo();
            TargetNode leaf3 =
                    new TargetNode(
                            BaseNodeType.JVM,
                            new ServiceRef(
                                    "id",
                                    URI.create("service:jmx:rmi:///jndi/rmi://leaf:3/jmxrmi"),
                                    "leaf3"));
            TargetNode leaf4 =
                    new TargetNode(
                            BaseNodeType.JVM,
                            new ServiceRef(
                                    "id",
                                    URI.create("service:jmx:rmi:///jndi/rmi://leaf:4/jmxrmi"),
                                    "leaf4"));
            EnvironmentNode realm2 =
                    new EnvironmentNode(
                            "realm1", BaseNodeType.REALM, Map.of(), Set.of(leaf3, leaf4));
            plugin2.setSubtree(gson.toJson(realm2));

            Mockito.when(dao.getAll()).thenReturn(List.of(plugin1, plugin2));

            EnvironmentNode tree = storage.getDiscoveryTree();

            MatcherAssert.assertThat(tree.getName(), Matchers.equalTo("Universe"));
            MatcherAssert.assertThat(tree.getNodeType(), Matchers.equalTo(BaseNodeType.UNIVERSE));
            MatcherAssert.assertThat(tree.getLabels(), Matchers.equalTo(Map.of()));
            MatcherAssert.assertThat(tree.getChildren(), Matchers.equalTo(List.of(realm1, realm2)));
        }
    }

    @Nested
    class ListServices {

        @Test
        void returnsEmptyIfDaoEmpty() {
            Mockito.when(dao.getAll()).thenReturn(List.of());

            List<ServiceRef> services = storage.listDiscoverableServices();

            MatcherAssert.assertThat(services, Matchers.empty());
        }

        @Test
        void listsAllTreeLeaves() {
            ServiceRef sr1 =
                    new ServiceRef(
                            "id", URI.create("service:jmx:rmi:///jndi/rmi://leaf:1/jmxrmi"), "sr1");
            TargetNode leaf1 = new TargetNode(BaseNodeType.JVM, sr1);
            ServiceRef sr2 =
                    new ServiceRef(
                            "id", URI.create("service:jmx:rmi:///jndi/rmi://leaf:2/jmxrmi"), "sr2");
            TargetNode leaf2 = new TargetNode(BaseNodeType.JVM, sr2);
            EnvironmentNode realm1 =
                    new EnvironmentNode(
                            "realm1", BaseNodeType.REALM, Map.of(), Set.of(leaf1, leaf2));
            PluginInfo plugin1 = new PluginInfo();
            plugin1.setSubtree(gson.toJson(realm1));

            ServiceRef sr3 =
                    new ServiceRef(
                            "id", URI.create("service:jmx:rmi:///jndi/rmi://leaf:3/jmxrmi"), "sr3");
            TargetNode leaf3 = new TargetNode(BaseNodeType.JVM, sr3);
            ServiceRef sr4 =
                    new ServiceRef(
                            "id", URI.create("service:jmx:rmi:///jndi/rmi://leaf:4/jmxrmi"), "sr4");
            TargetNode leaf4 = new TargetNode(BaseNodeType.JVM, sr4);
            EnvironmentNode realm2 =
                    new EnvironmentNode(
                            "realm2", BaseNodeType.REALM, Map.of(), Set.of(leaf3, leaf4));
            PluginInfo plugin2 = new PluginInfo();
            plugin2.setSubtree(gson.toJson(realm2));

            Mockito.when(dao.getAll()).thenReturn(List.of(plugin1, plugin2));

            List<ServiceRef> servicesList = storage.listDiscoverableServices();

            MatcherAssert.assertThat(servicesList, Matchers.hasSize(4));
            MatcherAssert.assertThat(servicesList, Matchers.containsInAnyOrder(sr1, sr2, sr3, sr4));
        }
    }

    @Test
    void updatesDaoWithModifiedJvmIds() throws Exception {
        UUID id = UUID.randomUUID();

        Mockito.when(jvmIdHelper.resolveId(Mockito.any(ServiceRef.class)))
                .thenAnswer(
                        new Answer<ServiceRef>() {
                            @Override
                            public ServiceRef answer(InvocationOnMock invocation) throws Throwable {
                                ServiceRef ref = invocation.getArgument(0);
                                // use alias as jvmId in test
                                return new ServiceRef(
                                        ref.getAlias().get(),
                                        ref.getServiceUri(),
                                        ref.getAlias().orElse(null));
                            }
                        });

        ServiceRef serviceRef1 =
                new ServiceRef(
                        null,
                        URI.create("service:jmx:rmi:///jndi/rmi://localhost:1/jmxrmi"),
                        "serviceRef1");
        ServiceRef serviceRef2 =
                new ServiceRef(
                        null,
                        URI.create("service:jmx:rmi:///jndi/rmi://localhost:2/jmxrmi"),
                        "serviceRef2");
        ServiceRef serviceRef3 =
                new ServiceRef(
                        null,
                        URI.create("service:jmx:rmi:///jndi/rmi://localhost:3/jmxrmi"),
                        "serviceRef3");
        ServiceRef serviceRef4 =
                new ServiceRef(
                        null,
                        URI.create("service:jmx:rmi:///jndi/rmi://localhost:4/jmxrmi"),
                        "serviceRef4");
        TargetNode target1 = new TargetNode(BaseNodeType.JVM, serviceRef1);
        TargetNode target2 = new TargetNode(BaseNodeType.JVM, serviceRef2);
        TargetNode target3 = new TargetNode(BaseNodeType.JVM, serviceRef3);
        TargetNode target4 = new TargetNode(BaseNodeType.JVM, serviceRef4);

        EnvironmentNode agent =
                new EnvironmentNode("agent-47", BaseNodeType.AGENT, Map.of(), Set.of(target3));
        EnvironmentNode realm1 =
                new EnvironmentNode("next", BaseNodeType.REALM, Map.of(), Set.of(target4));
        EnvironmentNode realm2 =
                new EnvironmentNode(
                        "next", BaseNodeType.REALM, Map.of(), Set.of(target1, target2, agent));

        PluginInfo prevPlugin =
                new PluginInfo("test-realm", URI.create("http://example.com"), gson.toJson(realm1));

        Mockito.when(dao.get(Mockito.eq(id))).thenReturn(Optional.of(prevPlugin));
        Mockito.when(dao.update(Mockito.any(UUID.class), Mockito.any(Collection.class)))
                .thenAnswer(
                        new Answer<PluginInfo>() {
                            @Override
                            public PluginInfo answer(InvocationOnMock invocation) throws Throwable {
                                List<AbstractNode> subtree = invocation.getArgument(1);
                                EnvironmentNode next =
                                        new EnvironmentNode(
                                                "next", BaseNodeType.REALM, Map.of(), subtree);
                                return new PluginInfo(
                                        "test-realm",
                                        URI.create("http://example.com"),
                                        gson.toJson(next));
                            }
                        });

        var updatedSubtree = storage.update(id, List.of(realm2));
        MatcherAssert.assertThat(updatedSubtree, Matchers.notNullValue());
        MatcherAssert.assertThat(updatedSubtree, Matchers.hasSize(1));
        for (AbstractNode node : updatedSubtree) {

            if (node instanceof TargetNode) {
                TargetNode target = (TargetNode) node;
                MatcherAssert.assertThat(
                        target.getTarget().getAlias().isPresent(), Matchers.is(true));
                MatcherAssert.assertThat(target.getTarget().getJvmId(), Matchers.notNullValue());
                MatcherAssert.assertThat(
                        target.getTarget().getJvmId(),
                        Matchers.equalTo(target.getTarget().getAlias().get()));
            } else {
                MatcherAssert.assertThat(node, Matchers.instanceOf(EnvironmentNode.class));
                EnvironmentNode env = (EnvironmentNode) node;
                for (AbstractNode nested : env.getChildren()) {
                    if (nested instanceof TargetNode) {
                        TargetNode target = (TargetNode) nested;
                        MatcherAssert.assertThat(
                                target.getTarget().getAlias().isPresent(), Matchers.is(true));
                        MatcherAssert.assertThat(
                                target.getTarget().getJvmId(), Matchers.notNullValue());
                        MatcherAssert.assertThat(
                                target.getTarget().getJvmId(),
                                Matchers.equalTo(target.getTarget().getAlias().get()));
                    }
                }
            }
        }
}

        @Test 
        void testIgnoreNodesIfNonSSLAuthExceptions() throws Exception {
                UUID id = UUID.randomUUID();
        
                ServiceRef serviceRef1 =
                        new ServiceRef(
                                null,
                                URI.create("service:jmx:rmi:///jndi/rmi://localhost:1/jmxrmi"),
                                "serviceRef1");
                ServiceRef serviceRef2 =
                        new ServiceRef(
                                null,
                                URI.create("service:jmx:rmi:///jndi/rmi://localhost:2/jmxrmi"),
                                "serviceRef2");
                TargetNode target1 = new TargetNode(BaseNodeType.JVM, serviceRef1);
                TargetNode target2 = new TargetNode(BaseNodeType.JVM, serviceRef2);
        
                EnvironmentNode realm1 =
                        new EnvironmentNode("next", BaseNodeType.REALM, Map.of(), Set.of(target1));
                EnvironmentNode realm2 =
                        new EnvironmentNode(
                                "next", BaseNodeType.REALM, Map.of(), Set.of(target1, target2));
        
                PluginInfo prevPlugin =
                        new PluginInfo("test-realm", URI.create("http://example.com"), gson.toJson(realm1));
        
                Mockito.when(dao.get(Mockito.eq(id))).thenReturn(Optional.of(prevPlugin));
                Mockito.when(dao.update(Mockito.any(UUID.class), Mockito.any(Collection.class)))
                        .thenAnswer(
                                new Answer<PluginInfo>() {
                                    @Override
                                    public PluginInfo answer(InvocationOnMock invocation) throws Throwable {
                                        List<AbstractNode> subtree = invocation.getArgument(1);
                                        EnvironmentNode next =
                                                new EnvironmentNode(
                                                        "next", BaseNodeType.REALM, Map.of(), subtree);
                                        return new PluginInfo(
                                                "test-realm",
                                                URI.create("http://example.com"),
                                                gson.toJson(next));
                                    }
                                });
                JvmIdGetException jige = Mockito.mock(JvmIdGetException.class);
                ExecutionException ex = Mockito.mock(ExecutionException.class);
                Mockito.when(jige.getCause()).thenReturn(ex);
                Mockito.when(ex.getCause()).thenReturn(new SecurityException("test"));
                
                Mockito.when(jvmIdHelper.resolveId(Mockito.any(ServiceRef.class)))
                        .thenThrow(jige)
                        .thenReturn(new ServiceRef("serviceRef2", serviceRef2.getServiceUri(),"serviceRef2"));
        
                var updatedSubtree = storage.update(id, List.of(realm2));
                MatcherAssert.assertThat(updatedSubtree, Matchers.notNullValue());
                MatcherAssert.assertThat(updatedSubtree, Matchers.hasSize(1));
                for (AbstractNode node : updatedSubtree) {
                        MatcherAssert.assertThat(node, Matchers.instanceOf(EnvironmentNode.class));
                        EnvironmentNode env = (EnvironmentNode) node;
                        MatcherAssert.assertThat(env.getChildren(), Matchers.hasSize(2));
                        for (AbstractNode nested : env.getChildren()) {
                            if (nested instanceof TargetNode) {
                                TargetNode target = (TargetNode) nested;
                                MatcherAssert.assertThat(target, Matchers.instanceOf(TargetNode.class));
                                if (target.getTarget().getAlias().get().equals("serviceRef1")) {
                                        MatcherAssert.assertThat(
                                                target.getTarget().getJvmId(), Matchers.nullValue());
                                }
                                else if (target.getTarget().getAlias().get().equals("serviceRef2")) {
                                        MatcherAssert.assertThat(
                                                target.getTarget().getJvmId(), Matchers.equalTo("serviceRef2"));
                                }
                                else {
                                        throw new IllegalStateException("Unexpected alias");
                                }
                            }
                        }
                }
        
        }
}
