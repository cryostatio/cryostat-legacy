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

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import itest.bases.StandardSelfTest;
import itest.util.Utils;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

// Disabled - broken by WebSocket Notification channel noise, but the CommandChannel is deprecated
// in favour of the HTTP API anyway. The WebSocket connection will only be used for the Notification
// channel going forward. FIXME add Notification channel tests.
@Disabled
public class BasicCommandChannelIT extends StandardSelfTest {

    @Test
    public void shouldGetPingResponse() throws Exception {
        JsonObject resp = sendMessage("ping").get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertResponseStatus(resp);
    }

    @Test
    public void shouldGetIPResponse() throws Exception {
        JsonObject resp = sendMessage("ip").get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertResponseStatus(resp);
        String ip = resp.getString("payload");
        Assertions.assertDoesNotThrow(() -> InetAddress.getByName(ip));
    }

    @Test
    public void shouldGetHostnameResponse() throws Exception {
        JsonObject resp = sendMessage("hostname").get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertResponseStatus(resp);
        String hostname = resp.getString("payload");
        MatcherAssert.assertThat(hostname, Matchers.equalTo("cryostat"));
    }

    @Test
    public void shouldGetUrlResponse() throws Exception {
        JsonObject resp = sendMessage("url").get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertResponseStatus(resp);
        String url = resp.getString("payload");
        MatcherAssert.assertThat(
                url,
                Matchers.equalTo(String.format("http://%s:%d", Utils.WEB_HOST, Utils.WEB_PORT)));
    }

    @Test
    public void shouldGetScanTargetsResponse() throws Exception {
        CompletableFuture<Boolean> f1 = new CompletableFuture<>();
        webClient
                .get("/api/v1/targets")
                .send(
                        ar -> {
                            // first request is just to "prime" the JDP discovery
                            if (ar.failed()) {
                                f1.completeExceptionally(ar.cause());
                                return;
                            }
                            f1.complete(ar.succeeded());
                        });
        Assertions.assertTrue(f1.get(10, TimeUnit.SECONDS));
        Thread.sleep(10_000);

        CompletableFuture<Boolean> f2 = new CompletableFuture<>();
        webClient
                .get("/api/v1/targets")
                .send(
                        ar -> {
                            if (ar.failed()) {
                                f2.completeExceptionally(ar.cause());
                                return;
                            }
                            JsonArray targets = ar.result().bodyAsJsonArray();
                            JsonObject selfRef =
                                    new JsonObject(
                                            Map.of(
                                                    "connectUrl",
                                                    "service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi",
                                                    "alias",
                                                    "io.cryostat.Cryostat"));
                            MatcherAssert.assertThat(
                                    targets, Matchers.equalTo(new JsonArray(List.of(selfRef))));
                            f2.complete(ar.succeeded());
                        });
        Assertions.assertTrue(f2.get(10, TimeUnit.SECONDS));
    }
}
