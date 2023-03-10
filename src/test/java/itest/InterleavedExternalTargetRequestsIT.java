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
package itest;

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.cryostat.MainModule;
import io.cryostat.core.log.Logger;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.ServiceRef.AnnotationKey;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import itest.bases.ExternalTargetsTest;
import itest.util.ITestCleanupFailedException;
import itest.util.Podman;
import itest.util.http.JvmIdWebRequest;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(OrderAnnotation.class)
class InterleavedExternalTargetRequestsIT extends ExternalTargetsTest {

    private static final Gson gson = MainModule.provideGson(Logger.INSTANCE);

    static final int BASE_PORT = 9093;
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
                            FIB_DEMO_IMAGESPEC, Map.of("JMX_PORT", String.valueOf(BASE_PORT + i))));
        }
        for (int i = 0; i < NUM_AUTH_EXT_CONTAINERS; i++) {
            specs.add(
                    new Podman.ImageSpec(
                            FIB_DEMO_IMAGESPEC,
                            Map.of(
                                    "JMX_PORT",
                                    String.valueOf(BASE_PORT + NUM_EXT_CONTAINERS + i),
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
                                    Podman.POD_NAME, BASE_PORT + i));
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
                            Integer.toString(BASE_PORT + i)));
            expected.add(ext);
        }
        MatcherAssert.assertThat(actual, Matchers.equalTo(expected));
    }

    @Test
    @Order(2)
    public void testInterleavedRequests() throws Exception {
        long start = System.nanoTime();

        createInMemoryRecordings(false);

        verifyInMemoryRecordingsCreated(false);

        deleteInMemoryRecordings(false);

        verifyInMemoryRecordingsDeleted(false);

        // Assertions.assertThrows(
        //         ExecutionException.class,
        //         () -> {
        createInMemoryRecordings(true);

        verifyInMemoryRecordingsCreated(true);

        deleteInMemoryRecordings(true);

        verifyInMemoryRecordingsDeleted(true);
        // });

        long stop = System.nanoTime();
        long elapsed = stop - start;
        System.out.println(
                String.format("Elapsed time: %dms", TimeUnit.NANOSECONDS.toMillis(elapsed)));
    }

    @Test
    @Order(2)
    public void testInterleavedRequestsWithAuth() throws Exception {
        long start = System.nanoTime();

        createInMemoryRecordings(true);

        verifyInMemoryRecordingsCreated(true);

        deleteInMemoryRecordings(true);

        verifyInMemoryRecordingsDeleted(true);

        long stop = System.nanoTime();
        long elapsed = stop - start;
        System.out.println(
                String.format("Elapsed time: %dms", TimeUnit.NANOSECONDS.toMillis(elapsed)));
    }

    private void createInMemoryRecordings(boolean useAuth) throws Exception {
        List<CompletableFuture<Void>> cfs = new ArrayList<>();
        int TARGET_PORT_NUMBER_START = useAuth ? (BASE_PORT + NUM_EXT_CONTAINERS) : BASE_PORT;
        for (int i = 0; i < (useAuth ? NUM_AUTH_EXT_CONTAINERS : NUM_EXT_CONTAINERS); i++) {
            final int fi = i;
            CompletableFuture<Void> cf = new CompletableFuture<>();
            cfs.add(cf);
            Podman.POOL.submit(
                    () -> {
                        MultiMap form = MultiMap.caseInsensitiveMultiMap();
                        form.add("recordingName", "interleaved-" + fi);
                        form.add("events", "template=Continuous");
                        HttpRequest<Buffer> req =
                                webClient.post(
                                        String.format(
                                                "/api/v1/targets/%s/recordings",
                                                Podman.POD_NAME
                                                        + ":"
                                                        + (TARGET_PORT_NUMBER_START + fi)));
                        if (useAuth) {
                            req =
                                    req.putHeader(
                                            "X-JMX-Authorization",
                                            "Basic "
                                                    + Base64.getUrlEncoder()
                                                            .encodeToString(
                                                                    "admin:adminpass123"
                                                                            .getBytes()));
                        }
                        req.sendForm(
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
        int TARGET_PORT_NUMBER_START = useAuth ? (BASE_PORT + NUM_EXT_CONTAINERS) : BASE_PORT;
        for (int i = 0; i < (useAuth ? NUM_AUTH_EXT_CONTAINERS : NUM_EXT_CONTAINERS); i++) {
            final int fi = i;
            CompletableFuture<Void> cf = new CompletableFuture<>();
            cfs.add(cf);
            HttpRequest<Buffer> req =
                    webClient.get(
                            String.format(
                                    "/api/v1/targets/%s/recordings",
                                    Podman.POD_NAME + ":" + (TARGET_PORT_NUMBER_START + fi)));
            if (useAuth) {
                req =
                        req.putHeader(
                                "X-JMX-Authorization",
                                "Basic "
                                        + Base64.getUrlEncoder()
                                                .encodeToString("admin:adminpass123".getBytes()));
            }
            req.send(
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
                                    recordingInfo.getString("state"), Matchers.equalTo("RUNNING"));

                            cf.complete(null);
                        }
                    });
        }
        CompletableFuture.allOf(cfs.toArray(new CompletableFuture[0]))
                .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void deleteInMemoryRecordings(boolean useAuth) throws Exception {
        List<CompletableFuture<Void>> cfs = new ArrayList<>();
        int TARGET_PORT_NUMBER_START = useAuth ? (BASE_PORT + NUM_EXT_CONTAINERS) : BASE_PORT;
        for (int i = 0; i < (useAuth ? NUM_AUTH_EXT_CONTAINERS : NUM_EXT_CONTAINERS); i++) {
            final int fi = i;
            CompletableFuture<Void> cf = new CompletableFuture<>();
            cfs.add(cf);
            Podman.POOL.submit(
                    () -> {
                        MultiMap form = MultiMap.caseInsensitiveMultiMap();
                        HttpRequest<Buffer> req =
                                webClient.delete(
                                        String.format(
                                                "/api/v1/targets/%s/recordings/%s",
                                                Podman.POD_NAME
                                                        + ":"
                                                        + (TARGET_PORT_NUMBER_START + fi),
                                                "interleaved-" + fi));
                        if (useAuth) {
                            req =
                                    req.putHeader(
                                            "X-JMX-Authorization",
                                            "Basic "
                                                    + Base64.getUrlEncoder()
                                                            .encodeToString(
                                                                    "admin:adminpass123"
                                                                            .getBytes()));
                        }
                        req.sendForm(
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
        int TARGET_PORT_NUMBER_START = useAuth ? (BASE_PORT + NUM_EXT_CONTAINERS) : BASE_PORT;
        for (int i = 0; i < (useAuth ? NUM_AUTH_EXT_CONTAINERS : NUM_EXT_CONTAINERS); i++) {
            final int fi = i;
            CompletableFuture<Void> cf = new CompletableFuture<>();
            cfs.add(cf);
            HttpRequest<Buffer> req =
                    webClient.get(
                            String.format(
                                    "/api/v1/targets/%s/recordings",
                                    Podman.POD_NAME + ":" + (TARGET_PORT_NUMBER_START + fi)));
            if (useAuth) {
                req =
                        req.putHeader(
                                "X-JMX-Authorization",
                                "Basic "
                                        + Base64.getUrlEncoder()
                                                .encodeToString("admin:adminpass123".getBytes()));
            }
            req.send(
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
