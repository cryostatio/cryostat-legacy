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

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import itest.bases.ExternalTargetsTest;
import itest.util.ITestCleanupFailedException;
import itest.util.Podman;
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
}
