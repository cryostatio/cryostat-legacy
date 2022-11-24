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

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import itest.bases.StandardSelfTest;
import itest.util.http.JvmIdWebRequest;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(OrderAnnotation.class)
public class CustomTargetsIT extends StandardSelfTest {

    private final ExecutorService worker = ForkJoinPool.commonPool();
    private String itestJvmId;

    @BeforeEach
    void setup() throws InterruptedException, ExecutionException, TimeoutException {
        itestJvmId =
                JvmIdWebRequest.jvmIdRequest(
                        "service:jmx:rmi:///jndi/rmi://cryostat-itests:9091/jmxrmi");
    }

    @Test
    @Order(1)
    void shouldBeAbleToTestTargetConnection() throws InterruptedException, ExecutionException {
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("connectUrl", "localhost:0");
        form.add("alias", "self");

        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        webClient
                .post("/api/v2/targets?dryrun=true")
                .sendForm(
                        form,
                        ar -> {
                            assertRequestStatus(ar, response);
                            response.complete(ar.result().bodyAsJsonObject());
                        });
        JsonObject body = response.get().getJsonObject("data").getJsonObject("result");
        MatcherAssert.assertThat(body.getString("connectUrl"), Matchers.equalTo("localhost:0"));
        MatcherAssert.assertThat(body.getString("alias"), Matchers.equalTo("self"));
    }

    @Test
    @Order(2)
    void targetShouldNotAppearInListing() throws InterruptedException, ExecutionException {
        CompletableFuture<JsonArray> response = new CompletableFuture<>();
        webClient
                .get("/api/v1/targets")
                .send(
                        ar -> {
                            assertRequestStatus(ar, response);
                            response.complete(ar.result().bodyAsJsonArray());
                        });
        JsonArray body = response.get();
        MatcherAssert.assertThat(body, Matchers.notNullValue());
        MatcherAssert.assertThat(body.size(), Matchers.equalTo(1));

        JsonObject selfJdp =
                new JsonObject(
                        Map.of(
                                "jvmId",
                                itestJvmId,
                                "alias",
                                "io.cryostat.Cryostat",
                                "connectUrl",
                                "service:jmx:rmi:///jndi/rmi://cryostat-itests:9091/jmxrmi",
                                "labels",
                                Map.of(),
                                "annotations",
                                Map.of(
                                        "cryostat",
                                        Map.of(
                                                "REALM",
                                                "JDP",
                                                "HOST",
                                                "cryostat-itests",
                                                "PORT",
                                                "9091",
                                                "JAVA_MAIN",
                                                "io.cryostat.Cryostat"),
                                        "platform",
                                        Map.of())));
        MatcherAssert.assertThat(body, Matchers.contains(selfJdp));
    }

    @Test
    @Order(3)
    void shouldBeAbleToDefineTarget()
            throws TimeoutException, ExecutionException, InterruptedException {
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("connectUrl", "localhost:0");
        form.add("alias", "self");

        CountDownLatch latch = new CountDownLatch(2);

        worker.submit(
                () -> {
                    try {
                        expectNotification("TargetJvmDiscovery", 5, TimeUnit.SECONDS)
                                .thenAcceptAsync(
                                        notification -> {
                                            JsonObject event =
                                                    notification
                                                            .getJsonObject("message")
                                                            .getJsonObject("event");
                                            MatcherAssert.assertThat(
                                                    event.getString("kind"),
                                                    Matchers.equalTo("FOUND"));
                                            MatcherAssert.assertThat(
                                                    event.getJsonObject("serviceRef")
                                                            .getString("connectUrl"),
                                                    Matchers.equalTo("localhost:0"));
                                            MatcherAssert.assertThat(
                                                    event.getJsonObject("serviceRef")
                                                            .getString("alias"),
                                                    Matchers.equalTo("self"));
                                            latch.countDown();
                                        })
                                .get();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        webClient
                .post("/api/v2/targets")
                .sendForm(
                        form,
                        ar -> {
                            assertRequestStatus(ar, response);
                            response.complete(ar.result().bodyAsJsonObject());
                            latch.countDown();
                        });
        JsonObject body = response.get().getJsonObject("data").getJsonObject("result");
        MatcherAssert.assertThat(body.getString("connectUrl"), Matchers.equalTo("localhost:0"));
        MatcherAssert.assertThat(body.getString("alias"), Matchers.equalTo("self"));

        latch.await(5, TimeUnit.SECONDS);
    }

    @Test
    @Order(4)
    void targetShouldAppearInListing()
            throws ExecutionException, InterruptedException, TimeoutException {
        CompletableFuture<JsonArray> response = new CompletableFuture<>();
        webClient
                .get("/api/v1/targets")
                .send(
                        ar -> {
                            assertRequestStatus(ar, response);
                            response.complete(ar.result().bodyAsJsonArray());
                        });
        JsonArray body = response.get();
        MatcherAssert.assertThat(body, Matchers.notNullValue());
        MatcherAssert.assertThat(body.size(), Matchers.equalTo(2));

        JsonObject selfJdp =
                new JsonObject(
                        Map.of(
                                "jvmId",
                                itestJvmId,
                                "alias",
                                "io.cryostat.Cryostat",
                                "connectUrl",
                                "service:jmx:rmi:///jndi/rmi://cryostat-itests:9091/jmxrmi",
                                "labels",
                                Map.of(),
                                "annotations",
                                Map.of(
                                        "cryostat",
                                        Map.of(
                                                "REALM",
                                                "JDP",
                                                "HOST",
                                                "cryostat-itests",
                                                "PORT",
                                                "9091",
                                                "JAVA_MAIN",
                                                "io.cryostat.Cryostat"),
                                        "platform",
                                        Map.of())));
        JsonObject selfCustom =
                new JsonObject(
                        Map.of(
                                "jvmId",
                                itestJvmId,
                                "alias",
                                "self",
                                "connectUrl",
                                "localhost:0",
                                "labels",
                                Map.of(),
                                "annotations",
                                Map.of(
                                        "cryostat",
                                        Map.of("REALM", "Custom Targets"),
                                        "platform",
                                        Map.of())));
        MatcherAssert.assertThat(body, Matchers.containsInAnyOrder(selfJdp, selfCustom));
    }

    @Test
    @Order(5)
    void shouldBeAbleToDeleteTarget()
            throws TimeoutException, ExecutionException, InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);

        worker.submit(
                () -> {
                    try {
                        expectNotification("TargetJvmDiscovery", 5, TimeUnit.SECONDS)
                                .thenAcceptAsync(
                                        notification -> {
                                            JsonObject event =
                                                    notification
                                                            .getJsonObject("message")
                                                            .getJsonObject("event");
                                            MatcherAssert.assertThat(
                                                    event.getString("kind"),
                                                    Matchers.equalTo("LOST"));
                                            MatcherAssert.assertThat(
                                                    event.getJsonObject("serviceRef")
                                                            .getString("connectUrl"),
                                                    Matchers.equalTo("localhost:0"));
                                            MatcherAssert.assertThat(
                                                    event.getJsonObject("serviceRef")
                                                            .getString("alias"),
                                                    Matchers.equalTo("self"));
                                            latch.countDown();
                                        })
                                .get();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });

        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        webClient
                .delete("/api/v2/targets/localhost:0")
                .send(
                        ar -> {
                            assertRequestStatus(ar, response);
                            response.complete(null);
                            latch.countDown();
                        });

        latch.await(5, TimeUnit.SECONDS);
    }

    @Test
    @Order(6)
    void targetShouldNoLongerAppearInListing() throws ExecutionException, InterruptedException {
        CompletableFuture<JsonArray> response = new CompletableFuture<>();
        webClient
                .get("/api/v1/targets")
                .send(
                        ar -> {
                            assertRequestStatus(ar, response);
                            response.complete(ar.result().bodyAsJsonArray());
                        });
        JsonArray body = response.get();
        MatcherAssert.assertThat(body, Matchers.notNullValue());
        MatcherAssert.assertThat(body.size(), Matchers.equalTo(1));

        JsonObject selfJdp =
                new JsonObject(
                        Map.of(
                                "jvmId",
                                itestJvmId,
                                "alias",
                                "io.cryostat.Cryostat",
                                "connectUrl",
                                "service:jmx:rmi:///jndi/rmi://cryostat-itests:9091/jmxrmi",
                                "labels",
                                Map.of(),
                                "annotations",
                                Map.of(
                                        "cryostat",
                                        Map.of(
                                                "REALM",
                                                "JDP",
                                                "HOST",
                                                "cryostat-itests",
                                                "PORT",
                                                "9091",
                                                "JAVA_MAIN",
                                                "io.cryostat.Cryostat"),
                                        "platform",
                                        Map.of())));
        MatcherAssert.assertThat(body, Matchers.contains(selfJdp));
    }
}
