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
package itest.bases;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.vertx.core.json.JsonArray;
import itest.util.ITestCleanupFailedException;
import itest.util.Podman;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterAll;

public abstract class ExternalTargetsTest extends StandardSelfTest {

    protected static final String FIB_DEMO_IMAGESPEC = "quay.io/andrewazores/vertx-fib-demo:0.13.0";

    public static final Pair<String, String> VERTX_FIB_CREDENTIALS =
            Pair.of("admin", "adminpass123");

    static final int DISCOVERY_POLL_PERIOD_MS =
            Integer.parseInt(System.getProperty("cryostat.itest.discovery.poll.period", "500"));
    static final int STABILITY_COUNT =
            Integer.parseInt(System.getProperty("cryostat.itest.discovery.poll.count", "1"));
    static final int DISCOVERY_BASE_MS =
            Integer.parseInt(System.getProperty("cryostat.itest.discovery.poll.timeout", "30000"));
    static final int DISCOVERY_TIMEOUT_MS =
            DISCOVERY_BASE_MS + (STABILITY_COUNT * DISCOVERY_POLL_PERIOD_MS);

    protected static final List<String> CONTAINERS = new ArrayList<>();

    @AfterAll
    static void cleanup() throws ITestCleanupFailedException {
        try {
            for (String id : CONTAINERS) {
                try {
                    Podman.stop(id);
                } catch (Exception e) {
                    throw new ITestCleanupFailedException(
                            String.format("Failed to kill container instance with ID %s", id), e);
                }
            }
            try {
                waitForDiscovery(0);
            } catch (Exception e) {
                throw new ITestCleanupFailedException(
                        "Failed waiting for external targets to disappear", e);
            }
        } finally {
            CONTAINERS.clear();
        }
    }

    public static void waitForDiscovery(int expectedTargets) throws Exception {
        // Repeatedly query targets, waiting until we have discovered the expected number (plus 1
        // for Cryostat itself as a custom target)
        expectedTargets += 1;
        long startTime = System.currentTimeMillis();
        int successes = 0;
        int iterations = 1;
        while (true) {
            int numTargets = queryTargets().get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).size();
            if (numTargets == expectedTargets) {
                System.out.println(
                        String.format(
                                "expected target count (%d) observed, counting success %d/%d",
                                expectedTargets, ++successes, STABILITY_COUNT));
                if (successes >= STABILITY_COUNT) {
                    System.out.println("discovery complete");
                    break;
                }
                Thread.sleep(DISCOVERY_POLL_PERIOD_MS * iterations);
            } else if (numTargets < expectedTargets) {
                System.err.println(
                        String.format(
                                "%d/%d targets found - waiting for discovery to complete",
                                numTargets, expectedTargets));
                if (System.currentTimeMillis() > startTime + DISCOVERY_TIMEOUT_MS) {
                    throw new Exception("discovery failed - timed out");
                }
                successes = 0;
                Podman.runCommand("ps", "--all");
                Thread.sleep(DISCOVERY_POLL_PERIOD_MS * iterations);
            } else {
                if (System.currentTimeMillis() > startTime + DISCOVERY_TIMEOUT_MS) {
                    throw new Exception(
                            String.format(
                                    "%d targets found - too many (expected %d) after timeout!",
                                    numTargets, expectedTargets));
                }
                System.err.println(
                        String.format(
                                "%d targets found - too many (expected %d)! Waiting to see if"
                                        + " discovery settles...",
                                numTargets, expectedTargets));
                successes = 0;
                Thread.sleep(DISCOVERY_POLL_PERIOD_MS * iterations);
            }
            iterations *= 2;
        }
        System.out.println(
                String.format(
                        "discovery completed in %dms", System.currentTimeMillis() - startTime));
    }

    public static CompletableFuture<JsonArray> queryTargets() throws Exception {
        CompletableFuture<JsonArray> resp = new CompletableFuture<>();
        webClient
                .get("/api/v1/targets")
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, resp)) {
                                resp.complete(ar.result().bodyAsJsonArray());
                            }
                        });
        return resp;
    }
}
