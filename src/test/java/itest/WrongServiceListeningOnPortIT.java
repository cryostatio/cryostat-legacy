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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.handler.HttpException;
import itest.bases.ExternalTargetsTest;
import itest.util.Podman;
import org.apache.http.client.utils.URLEncodedUtils;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class WrongServiceListeningOnPortIT extends ExternalTargetsTest {

    static final int TARGET_HTTP_PORT = 8081;
    static final int TARGET_JMX_PORT = 9093;
    static final String BAD_TARGET_CONNECT_URL =
            String.format(
                    "service:jmx:rmi:///jndi/rmi://%s:%d/jmxrmi",
                    Podman.POD_NAME,
                    // intentionally wrong - trying to connect to the target HTTP port instead of
                    // its JMX port
                    TARGET_HTTP_PORT);
    static final String BAD_TARGET_CONNECT_URL_ENCODED =
            URLEncodedUtils.formatSegments(BAD_TARGET_CONNECT_URL);

    static final int NUM_EXT_CONTAINERS = 1;
    static final List<String> CONTAINERS = new ArrayList<>();

    @BeforeAll
    static void setup() throws Exception {
        Podman.ImageSpec spec =
                new Podman.ImageSpec(
                        FIB_DEMO_IMAGESPEC,
                        Map.of(
                                "JMX_PORT",
                                String.valueOf(TARGET_JMX_PORT),
                                "HTTP_PORT",
                                String.valueOf(TARGET_HTTP_PORT)));
        String id = Podman.run(spec);
        CONTAINERS.add(id);
        Podman.waitForContainerState(id, "running");
        waitForDiscovery(1);
    }

    @AfterAll
    static void cleanup() throws Exception {
        for (String id : CONTAINERS) {
            Podman.kill(id);
        }
        CONTAINERS.clear();
    }

    @Test
    public void testConnectionFailsAsExpected() throws Exception {
        CompletableFuture<JsonArray> response = new CompletableFuture<>();
        webClient
                .get(String.format("/api/v1/targets/%s/recordings", BAD_TARGET_CONNECT_URL_ENCODED))
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, response)) {
                                response.complete(ar.result().bodyAsJsonArray());
                            }
                        });
        ExecutionException ex =
                Assertions.assertThrows(ExecutionException.class, () -> response.get());
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(504));
    }
}
