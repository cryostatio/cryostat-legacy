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
package itest.bases;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.vertx.core.json.JsonArray;
import org.junit.jupiter.api.AfterAll;

public abstract class ExternalTargetsTest extends StandardSelfTest {

    protected static final String FIB_DEMO_IMAGESPEC = "quay.io/andrewazores/vertx-fib-demo:0.13.0";

    static final int DISCOVERY_POLL_PERIOD_MS =
            Integer.parseInt(System.getProperty("cryostat.itest.jdp.poll.period", "2500"));
    static final int STABILITY_COUNT =
            Integer.parseInt(System.getProperty("cryostat.itest.jdp.poll.count", "1"));
    static final int DISCOVERY_BASE_MS =
            Integer.parseInt(System.getProperty("cryostat.itest.jdp.poll.timeout", "20000"));
    static final int DISCOVERY_TIMEOUT_MS =
            DISCOVERY_BASE_MS + (STABILITY_COUNT * DISCOVERY_POLL_PERIOD_MS);

    @AfterAll
    static void waitForExternalTargetsRemoval() throws Exception {
        // if the subclass doesn't clean itself up then this will time out and fail
        waitForDiscovery(0);
    }

    public static void waitForDiscovery(int expectedTargets) throws Exception {
        // Repeatedly query targets, waiting until we have discovered the expected number JDP
        // (expectedTargets, + 1 for Cryostat itself).
        long startTime = System.currentTimeMillis();
        int successes = 0;
        while (true) {
            int numTargets = queryTargets().get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS).size();
            if (numTargets == expectedTargets + 1) {
                System.out.println(
                        String.format(
                                "expected target count (%d) observed, counting success %d/%d",
                                expectedTargets + 1, ++successes, STABILITY_COUNT));
                if (successes >= STABILITY_COUNT) {
                    System.out.println("discovery complete");
                    break;
                }
                Thread.sleep(DISCOVERY_POLL_PERIOD_MS);
            } else if (numTargets < expectedTargets + 1) {
                System.err.println(
                        String.format(
                                "%d/%d targets found - waiting for discovery to complete",
                                numTargets, expectedTargets + 1));
                if (System.currentTimeMillis() > startTime + DISCOVERY_TIMEOUT_MS) {
                    throw new Exception("discovery failed - timed out");
                }
                successes = 0;
                Thread.sleep(DISCOVERY_POLL_PERIOD_MS);
            } else {
                if (System.currentTimeMillis() > startTime + DISCOVERY_TIMEOUT_MS) {
                    throw new Exception(
                            String.format(
                                    "%d targets found - too many (expected %d) after timeout!",
                                    numTargets, expectedTargets + 1));
                }
                System.err.println(
                        String.format(
                                "%d targets found - too many (expected %d)! Waiting to see if JDP"
                                        + " settles...",
                                numTargets, expectedTargets + 1));
                successes = 0;
                Thread.sleep(DISCOVERY_POLL_PERIOD_MS);
            }
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
