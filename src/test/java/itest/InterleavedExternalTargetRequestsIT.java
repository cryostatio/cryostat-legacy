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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.cryostat.MainModule;
import io.cryostat.core.log.Logger;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.ServiceRef.AnnotationKey;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import itest.bases.ExternalTargetsTest;
import itest.util.ITestCleanupFailedException;
import itest.util.Podman;
import itest.util.http.JvmIdWebRequest;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
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
    static final List<String> CONTAINERS = new ArrayList<>();

    @BeforeAll
    static void setup() throws Exception {
        Set<Podman.ImageSpec> specs = new HashSet<>();
        for (int i = 0; i < NUM_EXT_CONTAINERS; i++) {
            specs.add(
                    new Podman.ImageSpec(
                            FIB_DEMO_IMAGESPEC, Map.of("JMX_PORT", String.valueOf(9093 + i))));
        }
        for (int i = 0; i < NUM_AUTH_EXT_CONTAINERS; i++) {
            specs.add(
                    new Podman.ImageSpec(
                            FIB_DEMO_IMAGESPEC,
                            Map.of(
                                    "JMX_PORT",
                                    String.valueOf(9093 + NUM_EXT_CONTAINERS + i),
                                    "USE_AUTH",
                                    "true")));
        }
        for (Podman.ImageSpec spec : specs) {
            CONTAINERS.add(Podman.run(spec));
        }
        CompletableFuture.allOf(
                        CONTAINERS.stream()
                                .map(id -> Podman.waitForContainerState(id, "running"))
                                .collect(Collectors.toList())
                                .toArray(new CompletableFuture[0]))
                .join();
        waitForDiscovery(NUM_EXT_CONTAINERS_TOTAL);
    }

    @AfterAll
    static void cleanup() throws ITestCleanupFailedException {
        for (String id : CONTAINERS) {
            try {
                Podman.kill(id);
            } catch (Exception e) {
                throw new ITestCleanupFailedException(
                        String.format("Failed to kill container instance with ID %s", id), e);
            }
        }
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
        Set<ServiceRef> expected = new HashSet<>();
        String cryostatTargetId =
                String.format("service:jmx:rmi:///jndi/rmi://%s:9091/jmxrmi", Podman.POD_NAME);
        String cryostatJvmId = JvmIdWebRequest.jvmIdRequest(cryostatTargetId);
        ServiceRef cryostat =
                new ServiceRef(cryostatJvmId, new URI(cryostatTargetId), "io.cryostat.Cryostat");
        cryostat.setCryostatAnnotations(
                Map.of(
                        AnnotationKey.REALM,
                        "JDP",
                        AnnotationKey.JAVA_MAIN,
                        "io.cryostat.Cryostat",
                        AnnotationKey.HOST,
                        Podman.POD_NAME,
                        AnnotationKey.PORT,
                        "9091"));
        expected.add(cryostat);
        for (int i = 0; i < NUM_EXT_CONTAINERS_TOTAL; i++) {
            URI uri =
                    new URI(
                            String.format(
                                    "service:jmx:rmi:///jndi/rmi://%s:%d/jmxrmi",
                                    Podman.POD_NAME, 9093 + i));
            String jvmId = JvmIdWebRequest.jvmIdRequest(uri, VERTX_FIB_CREDENTIALS);
            ServiceRef ext = new ServiceRef(jvmId, uri, "es.andrewazor.demo.Main");
            ext.setCryostatAnnotations(
                    Map.of(
                            AnnotationKey.REALM,
                            "JDP",
                            AnnotationKey.JAVA_MAIN,
                            "es.andrewazor.demo.Main",
                            AnnotationKey.HOST,
                            Podman.POD_NAME,
                            AnnotationKey.PORT,
                            Integer.toString(9093 + i)));
            expected.add(ext);
        }
        MatcherAssert.assertThat(actual, Matchers.equalTo(expected));
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
