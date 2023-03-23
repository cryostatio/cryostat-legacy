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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled(
        "FIXME: since #1188 Security Context, looking up the security context for a given"
            + " targetoccurs first before attempting to connect to that target. The port number is"
            + " part of the targetlookup, so the lookup will fail with a 404 before the connection"
            + " attempt is made (which wouldproduce the expected 504).")
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
