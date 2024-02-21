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
package itest;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import io.cryostat.MainModule;
import io.cryostat.core.log.Logger;
import io.cryostat.platform.ServiceRef;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import itest.bases.ExternalTargetsTest;
import itest.util.Podman;
import itest.util.http.JvmIdWebRequest;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(OrderAnnotation.class)
class InterleavedExternalTargetRequestsIT extends ExternalTargetsTest {

    private static final Gson gson = MainModule.provideGson(Logger.INSTANCE);

    static final int NUM_EXT_CONTAINERS = 4;
    static final int NUM_AUTH_EXT_CONTAINERS = 4;
    static final int NUM_EXT_CONTAINERS_TOTAL = NUM_EXT_CONTAINERS + NUM_AUTH_EXT_CONTAINERS;

    @BeforeAll
    static void setup() throws Exception {
        List<Podman.ImageSpec> specs = new ArrayList<>(NUM_EXT_CONTAINERS_TOTAL);
        for (int i = 0; i < NUM_EXT_CONTAINERS; i++) {
            Podman.ImageSpec spec =
                    new Podman.ImageSpec(
                            "vertx-fib-demo-" + i,
                            FIB_DEMO_IMAGESPEC,
                            Map.of("JMX_PORT", String.valueOf(9093 + i)));
            specs.add(spec);
        }
        for (int i = 0; i < NUM_AUTH_EXT_CONTAINERS; i++) {
            Podman.ImageSpec spec =
                    new Podman.ImageSpec(
                            "vertx-fib-demo-" + NUM_EXT_CONTAINERS + i,
                            FIB_DEMO_IMAGESPEC,
                            Map.of(
                                    "JMX_PORT",
                                    String.valueOf(9093 + NUM_EXT_CONTAINERS + i),
                                    "USE_AUTH",
                                    "true"));
            specs.add(spec);
        }
        for (int i = 0; i < NUM_EXT_CONTAINERS_TOTAL; i++) {
            CONTAINERS.add(Podman.runAppWithAgent(10_000 + i, specs.get(i)));
        }
        waitForDiscovery(NUM_EXT_CONTAINERS_TOTAL);
    }

    @Test
    @Order(1)
    void testOtherContainersFound() throws Exception {
        // FIXME don't use ServiceRefs or Gson (de)serialization in these tests. This should be
        // as independent as possible from Cryostat internal implementation details.
        CompletableFuture<Set<ServiceRef>> resp = new CompletableFuture<>();
        webClient
                .get("/api/v1/targets")
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, resp)) {
                                resp.complete(
                                        gson.fromJson(
                                                ar.result().bodyAsString(),
                                                new TypeToken<Set<ServiceRef>>() {}.getType()));
                            }
                        });
        Set<ServiceRef> actual = resp.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        // ordering may not be guaranteed so use a Set, but there should be no duplicates and so
        // size should not change
        MatcherAssert.assertThat(actual.size(), Matchers.equalTo(NUM_EXT_CONTAINERS_TOTAL + 1));
        MatcherAssert.assertThat(
                actual,
                Matchers.hasItem(
                        Matchers.allOf(
                                Matchers.hasProperty(
                                        "serviceUri",
                                        Matchers.equalTo(URI.create(SELF_REFERENCE_JMX_URL))),
                                Matchers.hasProperty(
                                        "jvmId",
                                        Matchers.equalTo(
                                                JvmIdWebRequest.jvmIdRequest(
                                                        SELF_REFERENCE_JMX_URL))))));
        for (int i = 0; i < NUM_EXT_CONTAINERS_TOTAL; i++) {
            String serviceUri =
                    String.format(
                            "service:jmx:rmi:///jndi/rmi://%s:%d/jmxrmi",
                            Podman.POD_NAME, 9093 + i);
            String jvmId =
                    JvmIdWebRequest.jvmIdRequest(URI.create(serviceUri), VERTX_FIB_CREDENTIALS);
            MatcherAssert.assertThat(
                    actual,
                    Matchers.hasItem(
                            Matchers.allOf(
                                    Matchers.hasProperty(
                                            "serviceUri", Matchers.equalTo(URI.create(serviceUri))),
                                    Matchers.hasProperty("jvmId", Matchers.equalTo(jvmId)))));
        }
    }

    @Test
    @Order(2)
    public void testInterleavedRequests() throws Exception {
        /* FIXME: Fix front-end credentials handling with JMX auth and jvmIds so test can be re-enabled */
        /* See https://github.com/cryostatio/cryostat-web/issues/656 */
        long start = System.nanoTime();

        createInMemoryRecordings(false);

        verifyInMemoryRecordingsCreated(false);

        deleteInMemoryRecordings(false);

        verifyInMemoryRecordingsDeleted(false);

        Assertions.assertThrows(
                ExecutionException.class,
                () -> {
                    createInMemoryRecordings(true);

                    verifyInMemoryRecordingsCreated(true);

                    deleteInMemoryRecordings(true);

                    verifyInMemoryRecordingsDeleted(true);
                });

        long stop = System.nanoTime();
        long elapsed = stop - start;
        System.out.println(
                String.format("Elapsed time: %dms", TimeUnit.NANOSECONDS.toMillis(elapsed)));
    }

    private void createInMemoryRecordings(boolean useAuth) throws Exception {
        List<CompletableFuture<Void>> cfs = new ArrayList<>();
        int TARGET_PORT_NUMBER_START = useAuth ? (9093 + NUM_EXT_CONTAINERS) : 9093;
        for (int i = 0; i < (useAuth ? NUM_AUTH_EXT_CONTAINERS : NUM_EXT_CONTAINERS); i++) {
            final int fi = i;
            CompletableFuture<Void> cf = new CompletableFuture<>();
            cfs.add(cf);
            Podman.POOL.submit(
                    () -> {
                        MultiMap form = MultiMap.caseInsensitiveMultiMap();
                        form.add("recordingName", "interleaved-" + fi);
                        form.add("events", "template=Continuous");
                        webClient
                                .post(
                                        String.format(
                                                "/api/v1/targets/%s/recordings",
                                                Podman.POD_NAME
                                                        + ":"
                                                        + (TARGET_PORT_NUMBER_START + fi)))
                                .putHeader(
                                        "X-JMX-Authorization",
                                        "Basic "
                                                + Base64.getUrlEncoder()
                                                        .encodeToString(
                                                                "admin:adminpass123".getBytes()))
                                .sendForm(
                                        form,
                                        ar -> {
                                            if (assertRequestStatus(ar, cf)) {
                                                cf.complete(null);
                                            }
                                        });
                    });
        }
        CompletableFuture.allOf(cfs.toArray(new CompletableFuture[0]))
                .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void verifyInMemoryRecordingsCreated(boolean useAuth) throws Exception {
        List<CompletableFuture<Void>> cfs = new ArrayList<>();
        int TARGET_PORT_NUMBER_START = useAuth ? (9093 + NUM_EXT_CONTAINERS) : 9093;
        for (int i = 0; i < (useAuth ? NUM_AUTH_EXT_CONTAINERS : NUM_EXT_CONTAINERS); i++) {
            final int fi = i;
            CompletableFuture<Void> cf = new CompletableFuture<>();
            cfs.add(cf);
            webClient
                    .get(
                            String.format(
                                    "/api/v1/targets/%s/recordings",
                                    Podman.POD_NAME + ":" + (TARGET_PORT_NUMBER_START + fi)))
                    .putHeader(
                            "X-JMX-Authorization",
                            "Basic "
                                    + Base64.getUrlEncoder()
                                            .encodeToString("admin:adminpass123".getBytes()))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, cf)) {
                                    JsonArray listResp = ar.result().bodyAsJsonArray();
                                    MatcherAssert.assertThat(
                                            "list should have size 1 after recording creation",
                                            listResp.size(),
                                            Matchers.equalTo(1));
                                    JsonObject recordingInfo = listResp.getJsonObject(0);
                                    MatcherAssert.assertThat(
                                            recordingInfo.getString("name"),
                                            Matchers.equalTo("interleaved-" + fi));
                                    MatcherAssert.assertThat(
                                            recordingInfo.getString("state"),
                                            Matchers.equalTo("RUNNING"));

                                    cf.complete(null);
                                }
                            });
        }
        CompletableFuture.allOf(cfs.toArray(new CompletableFuture[0]))
                .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void deleteInMemoryRecordings(boolean useAuth) throws Exception {
        List<CompletableFuture<Void>> cfs = new ArrayList<>();
        int TARGET_PORT_NUMBER_START = useAuth ? (9093 + NUM_EXT_CONTAINERS) : 9093;
        for (int i = 0; i < (useAuth ? NUM_AUTH_EXT_CONTAINERS : NUM_EXT_CONTAINERS); i++) {
            final int fi = i;
            CompletableFuture<Void> cf = new CompletableFuture<>();
            cfs.add(cf);
            Podman.POOL.submit(
                    () -> {
                        MultiMap form = MultiMap.caseInsensitiveMultiMap();
                        webClient
                                .delete(
                                        String.format(
                                                "/api/v1/targets/%s/recordings/%s",
                                                Podman.POD_NAME
                                                        + ":"
                                                        + (TARGET_PORT_NUMBER_START + fi),
                                                "interleaved-" + fi))
                                .putHeader(
                                        "X-JMX-Authorization",
                                        "Basic "
                                                + Base64.getUrlEncoder()
                                                        .encodeToString(
                                                                "admin:adminpass123".getBytes()))
                                .sendForm(
                                        form,
                                        ar -> {
                                            if (assertRequestStatus(ar, cf)) {
                                                cf.complete(null);
                                            }
                                        });
                    });
        }
        CompletableFuture.allOf(cfs.toArray(new CompletableFuture[0]))
                .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void verifyInMemoryRecordingsDeleted(boolean useAuth) throws Exception {
        List<CompletableFuture<Void>> cfs = new ArrayList<>();
        int TARGET_PORT_NUMBER_START = useAuth ? (9093 + NUM_EXT_CONTAINERS) : 9093;
        for (int i = 0; i < (useAuth ? NUM_AUTH_EXT_CONTAINERS : NUM_EXT_CONTAINERS); i++) {
            final int fi = i;
            CompletableFuture<Void> cf = new CompletableFuture<>();
            cfs.add(cf);
            webClient
                    .get(
                            String.format(
                                    "/api/v1/targets/%s/recordings",
                                    Podman.POD_NAME + ":" + (TARGET_PORT_NUMBER_START + fi)))
                    .putHeader(
                            "X-JMX-Authorization",
                            "Basic "
                                    + Base64.getUrlEncoder()
                                            .encodeToString("admin:adminpass123".getBytes()))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, cf)) {
                                    JsonArray listResp = ar.result().bodyAsJsonArray();
                                    MatcherAssert.assertThat(
                                            "list should have size 0 after recording deletion",
                                            listResp.size(),
                                            Matchers.equalTo(0));
                                    cf.complete(null);
                                }
                            });
        }
        CompletableFuture.allOf(cfs.toArray(new CompletableFuture[0]))
                .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}
