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

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import itest.util.Podman;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class InterleavedExternalTargetRequestsIT extends TestBase {

    static final int NUM_EXT_CONTAINERS = 8;
    static final List<String> CONTAINERS = new ArrayList<>();

    @BeforeAll
    static void setup() throws Exception {
        Set<Podman.ImageSpec> specs = new HashSet<>();
        for (int i = 0; i < NUM_EXT_CONTAINERS; i++) {
            specs.add(
                    new Podman.ImageSpec(
                            "quay.io/andrewazores/vertx-fib-demo:0.6.0",
                            Map.of("JMX_PORT", String.valueOf(9093 + i), "USE_AUTH", "true")));
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

        // Query every 5s for up to 6 queries, waiting until we have discovered the expected
        // number of targets via JDP (NUM_EXT_CONTAINERS, + 1 for Cryostat itself).
        int attempts = 0;
        while (true) {
            CompletableFuture<Integer> resp = new CompletableFuture<>();
            webClient
                    .get("/api/v1/targets")
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, resp)) {
                                    resp.complete(ar.result().bodyAsJsonArray().size());
                                }
                            });
            int numTargets = resp.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (numTargets == NUM_EXT_CONTAINERS + 1) {
                System.out.println("setup complete, continuing to tests");
                break;
            } else if (numTargets < NUM_EXT_CONTAINERS + 1) {
                System.err.println(String.format("%d targets found on attempt %d - waiting for setup to complete", numTargets, attempts + 1));
                if (attempts > 6) {
                    throw new Exception("setup failed");
                }
                Thread.sleep(5_000);
            } else {
                System.err.println(String.format("%d targets found on attempt %d - too many!", numTargets, attempts + 1));
                throw new Exception("setup failed");
            }
            attempts++;
        }
    }

    @AfterAll
    static void cleanup() throws Exception {
        for (String id : CONTAINERS) {
            Podman.kill(id);
        }

        // Query every 5s for up to 6 queries. If we still see additional targets other than
        // the Cryostat instance itself, bail out - teardown failed. JDP discovery may take some
        // time to notice that targets have disappeared after the processes/containers are killed,
        // but this should be only a few seconds.
        // https://github.com/cryostatio/cryostat/issues/501#issuecomment-856264316
        int attempts = 0;
        while (true) {
            CompletableFuture<Integer> resp = new CompletableFuture<>();
            webClient
                    .get("/api/v1/targets")
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, resp)) {
                                    resp.complete(ar.result().bodyAsJsonArray().size());
                                }
                            });
            int numTargets = resp.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (numTargets > 1) {
                System.err.println(String.format("%d targets found on attempt %d - waiting for teardown to complete", numTargets, attempts + 1));
                if (attempts++ > 6) {
                    throw new Exception("teardown failed");
                }
                Thread.sleep(5_000);
            } else if (numTargets == 0) {
                throw new Exception("teardown failed - all containers gone, including Cryostat");
            } else {
                System.out.println("teardown complete");
                break;
            }
        }
    }

    @Test
    void testOtherContainersFound() throws Exception {
        CompletableFuture<JsonArray> resp = new CompletableFuture<>();
        webClient
                .get("/api/v1/targets")
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, resp)) {
                                resp.complete(ar.result().bodyAsJsonArray());
                            }
                        });
        JsonArray listResp = resp.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        Set<Map<String, String>> actual = new HashSet<>(listResp.getList());
        // ordering may not be guaranteed so use a Set, but there should be no duplicates and so
        // size should not change
        MatcherAssert.assertThat(actual.size(), Matchers.equalTo(listResp.size()));
        Set<Map<String, String>> expected = new HashSet<>();
        expected.add(
                Map.of(
                        "connectUrl",
                        String.format(
                                "service:jmx:rmi:///jndi/rmi://%s:9091/jmxrmi", Podman.POD_NAME),
                        "alias",
                        "io.cryostat.Cryostat"));
        for (int i = 0; i < NUM_EXT_CONTAINERS; i++) {
            expected.add(
                    Map.of(
                            "connectUrl",
                            String.format(
                                    "service:jmx:rmi:///jndi/rmi://%s:%d/jmxrmi",
                                    Podman.POD_NAME, 9093 + i),
                            "alias",
                            "es.andrewazor.demo.Main"));
        }
        MatcherAssert.assertThat(actual, Matchers.equalTo(expected));
    }

    @Test
    public void testInterleavedRequests() throws Exception {
        long start = System.nanoTime();

        createInMemoryRecordings();

        verifyInMemoryRecordingsCreated();

        deleteInMemoryRecordings();

        verifyInmemoryRecordingsDeleted();

        long stop = System.nanoTime();
        long elapsed = stop - start;
        System.out.println(
                String.format("Elapsed time: %dms", TimeUnit.NANOSECONDS.toMillis(elapsed)));
    }

    private void createInMemoryRecordings() throws Exception {
        List<CompletableFuture<Void>> cfs = new ArrayList<>();
        for (int i = 0; i < CONTAINERS.size(); i++) {
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
                                                Podman.POD_NAME + ":" + (9093 + fi)))
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

    private void verifyInMemoryRecordingsCreated() throws Exception {
        List<CompletableFuture<Void>> cfs = new ArrayList<>();
        for (int i = 0; i < CONTAINERS.size(); i++) {
            final int fi = i;
            CompletableFuture<Void> cf = new CompletableFuture<>();
            cfs.add(cf);
            webClient
                    .get(
                            String.format(
                                    "/api/v1/targets/%s/recordings",
                                    Podman.POD_NAME + ":" + (9093 + fi)))
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

    private void deleteInMemoryRecordings() throws Exception {
        List<CompletableFuture<Void>> cfs = new ArrayList<>();
        for (int i = 0; i < CONTAINERS.size(); i++) {
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
                                                Podman.POD_NAME + ":" + (9093 + fi),
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

    private void verifyInmemoryRecordingsDeleted() throws Exception {
        List<CompletableFuture<Void>> cfs = new ArrayList<>();
        for (int i = 0; i < CONTAINERS.size(); i++) {
            final int fi = i;
            CompletableFuture<Void> cf = new CompletableFuture<>();
            cfs.add(cf);
            webClient
                    .get(
                            String.format(
                                    "/api/v1/targets/%s/recordings",
                                    Podman.POD_NAME + ":" + (9093 + fi)))
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
