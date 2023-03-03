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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import itest.bases.ExternalTargetsTest;
import itest.util.ITestCleanupFailedException;
import itest.util.Podman;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.Matcher;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class JmxAuthIT extends ExternalTargetsTest {

    private static final String TARGET_BASIC_CREDENTIALS =
            "Basic " + Base64.getEncoder().encodeToString("admin:adminpass123".getBytes());
    private static final String X_JMX_AUTHORIZATION = "X-JMX-Authorization";
    private static final Matcher<Integer> SC_OK_RANGE =
            Matchers.both(Matchers.greaterThanOrEqualTo(200)).and(Matchers.lessThan(300));
    private static final Matcher<Integer> SC_JMX_AUTH_FAIL = Matchers.equalTo(427);

    static final List<String> CONTAINERS = new ArrayList<>();

    final String jmxServiceUrl =
            String.format("service:jmx:rmi:///jndi/rmi://%s:9093/jmxrmi", Podman.POD_NAME);
    final String jmxServiceUrlEncoded = jmxServiceUrl.replaceAll("/", "%2F");

    @BeforeAll
    static void setup() throws Exception {
        CONTAINERS.add(
                Podman.run(
                        new Podman.ImageSpec(
                                FIB_DEMO_IMAGESPEC,
                                Map.of("JMX_PORT", "9093", "USE_AUTH", "true"))));
        waitForDiscovery(1);
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
    void checkStatusForRecordingsQueryWithCredentials() throws Exception {
        CompletableFuture<Integer> response = new CompletableFuture<>();
        webClient
                .get(String.format("/api/v1/targets/%s/recordings", jmxServiceUrlEncoded))
                .putHeader(X_JMX_AUTHORIZATION, TARGET_BASIC_CREDENTIALS)
                .send(ar -> response.complete(ar.result().statusCode()));
        MatcherAssert.assertThat(response.get(), SC_OK_RANGE);
    }

    @Test
    void checkStatusForRecordingsQueryWithoutCredentials() throws Exception {
        CompletableFuture<Integer> response = new CompletableFuture<>();
        webClient
                .get(String.format("/api/v1/targets/%s/recordings", jmxServiceUrlEncoded))
                .send(ar -> response.complete(ar.result().statusCode()));
        MatcherAssert.assertThat(response.get(), SC_JMX_AUTH_FAIL);
    }

    @Test
    void checkStatusForTemplatesQueryWithCredentials() throws Exception {
        CompletableFuture<Integer> response = new CompletableFuture<>();
        webClient
                .get(String.format("/api/v1/targets/%s/templates", jmxServiceUrlEncoded))
                .putHeader(X_JMX_AUTHORIZATION, TARGET_BASIC_CREDENTIALS)
                .send(ar -> response.complete(ar.result().statusCode()));
        MatcherAssert.assertThat(response.get(), SC_OK_RANGE);
    }

    @Test
    void checkStatusForTemplatesQueryWithoutCredentials() throws Exception {
        CompletableFuture<Integer> response = new CompletableFuture<>();
        webClient
                .get(String.format("/api/v1/targets/%s/templates", jmxServiceUrlEncoded))
                .send(ar -> response.complete(ar.result().statusCode()));
        MatcherAssert.assertThat(response.get(), SC_JMX_AUTH_FAIL);
    }

    @Test
    void checkStatusForV1EventsQueryWithCredentials() throws Exception {
        CompletableFuture<Integer> response = new CompletableFuture<>();
        webClient
                .get(String.format("/api/v1/targets/%s/events", jmxServiceUrlEncoded))
                .putHeader(X_JMX_AUTHORIZATION, TARGET_BASIC_CREDENTIALS)
                .send(ar -> response.complete(ar.result().statusCode()));
        MatcherAssert.assertThat(response.get(), SC_OK_RANGE);
    }

    @Test
    void checkStatusForV1EventsQueryWithoutCredentials() throws Exception {
        CompletableFuture<Integer> response = new CompletableFuture<>();
        webClient
                .get(String.format("/api/v1/targets/%s/events", jmxServiceUrlEncoded))
                .send(ar -> response.complete(ar.result().statusCode()));
        MatcherAssert.assertThat(response.get(), SC_JMX_AUTH_FAIL);
    }

    @Test
    void checkStatusForV2EventsQueryWithCredentials() throws Exception {
        CompletableFuture<Integer> response = new CompletableFuture<>();
        webClient
                .get(String.format("/api/v2/targets/%s/events", jmxServiceUrlEncoded))
                .putHeader(X_JMX_AUTHORIZATION, TARGET_BASIC_CREDENTIALS)
                .send(ar -> response.complete(ar.result().statusCode()));
        MatcherAssert.assertThat(response.get(), SC_OK_RANGE);
    }

    @Test
    void checkStatusForV2EventsQueryWithoutCredentials() throws Exception {
        CompletableFuture<Integer> response = new CompletableFuture<>();
        webClient
                .get(String.format("/api/v2/targets/%s/events", jmxServiceUrlEncoded))
                .send(ar -> response.complete(ar.result().statusCode()));
        MatcherAssert.assertThat(response.get(), SC_JMX_AUTH_FAIL);
    }

    @Test
    void checkStatusForGraphqlQueryWithCredentials() throws Exception {
        CompletableFuture<Pair<Integer, String>> response = new CompletableFuture<>();
        try (InputStream is =
                getClass().getClassLoader().getResourceAsStream("JmxAuthIT.query.graphql")) {
            String query = IOUtils.toString(is, StandardCharsets.UTF_8);
            webClient
                    .post("/api/v2.2/graphql")
                    .putHeader(X_JMX_AUTHORIZATION, TARGET_BASIC_CREDENTIALS)
                    .sendJsonObject(
                            new JsonObject(Map.of("query", query)),
                            ar -> {
                                HttpResponse<Buffer> result = ar.result();
                                response.complete(
                                        Pair.of(result.statusCode(), result.bodyAsString()));
                            });
            Pair<Integer, String> pair = response.get();
            MatcherAssert.assertThat(pair.getLeft(), SC_OK_RANGE);
            JsonObject result = new JsonObject(pair.getRight());
            long uptime =
                    result.getJsonObject("data")
                            .getJsonArray("targetNodes")
                            .getJsonObject(0)
                            .getJsonObject("mbeanMetrics")
                            .getJsonObject("runtime")
                            .getLong("uptime");
            MatcherAssert.assertThat(uptime, Matchers.greaterThan(0L));
        }
    }

    @Test
    void checkStatusForGraphqlQueryWithoutCredentials() throws Exception {
        CompletableFuture<Pair<Integer, String>> response = new CompletableFuture<>();
        try (InputStream is =
                getClass().getClassLoader().getResourceAsStream("JmxAuthIT.query.graphql")) {
            String query = IOUtils.toString(is, StandardCharsets.UTF_8);
            webClient
                    .post("/api/v2.2/graphql")
                    .sendJsonObject(
                            new JsonObject(Map.of("query", query)),
                            ar -> {
                                HttpResponse<Buffer> result = ar.result();
                                response.complete(
                                        Pair.of(result.statusCode(), result.bodyAsString()));
                            });
            Pair<Integer, String> pair = response.get();
            MatcherAssert.assertThat(pair.getLeft(), SC_OK_RANGE);
            MatcherAssert.assertThat(
                    pair.getRight(),
                    Matchers.containsString(
                            "Exception while fetching data (/targetNodes[0]/mbeanMetrics) :"
                                    + " org.openjdk.jmc.rjmx.ConnectionException caused by"
                                    + " java.lang.SecurityException: Authentication failed! Invalid"
                                    + " username or password"));
        }
    }
}
