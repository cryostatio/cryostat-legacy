/*-
 * #%L
 * Container JFR
 * %%
 * Copyright (C) 2020 Red Hat, Inc.
 * %%
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
 * #L%
 */
package itest;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.WebSocket;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;

public abstract class ITestBase {

    static final int REQUEST_TIMEOUT_SECONDS = 30;
    static WebClient webClient = IntegrationTestUtils.getWebClient();

    CompletableFuture<JsonObject> sendMessage(String command, String... args)
            throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<JsonObject> future = new CompletableFuture<>();

        IntegrationTestUtils.HTTP_CLIENT.webSocket(
                getClientUrl().get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                ar -> {
                    if (ar.failed()) {
                        future.completeExceptionally(ar.cause());
                        return;
                    }
                    WebSocket ws = ar.result();

                    ws.handler(
                            m -> {
                                JsonObject resp = m.toJsonObject();
                                String commandName = resp.getString("commandName");
                                ws.end(
                                        unused -> {
                                            if (Objects.equals(command, commandName)) {
                                                future.complete(resp);
                                            } else {
                                                future.completeExceptionally(
                                                        new Exception(
                                                                String.format(
                                                                        "Unexpected command response %s for command %s",
                                                                        commandName, command)));
                                            }
                                        });
                            });

                    ws.writeTextMessage(
                            new JsonObject(Map.of("command", command, "args", Arrays.asList(args)))
                                    .toString(),
                            wsar -> {
                                if (wsar.failed()) {
                                    future.completeExceptionally(wsar.cause());
                                }
                            });
                });

        return future;
    }

    void assertResponseStatus(JsonObject response) {
        assertResponseStatus(response, 0);
    }

    void assertResponseStatus(JsonObject response, int status) {
        MatcherAssert.assertThat(response.getInteger("status"), Matchers.equalTo(status));
    }

    private Future<String> getClientUrl() {
        CompletableFuture<String> future = new CompletableFuture<>();
        webClient
                .get("/api/v1/clienturl")
                .send(
                        ar -> {
                            if (ar.succeeded()) {
                                future.complete(
                                        ar.result().bodyAsJsonObject().getString("clientUrl"));
                            } else {
                                future.completeExceptionally(ar.cause());
                            }
                        });
        return future;
    }

    CompletableFuture<Path> downloadFile(String url, String name, String suffix) {
        CompletableFuture<Path> dlFuture = new CompletableFuture<>();
        IntegrationTestUtils.getWebClient()
                .getAbs(url)
                .send(
                        ar -> {
                            if (ar.failed()) {
                                dlFuture.completeExceptionally(ar.cause());
                                return;
                            }
                            HttpResponse<Buffer> resp = ar.result();
                            if (resp.statusCode() != 200) {
                                dlFuture.completeExceptionally(
                                        new Exception(String.format("HTTP %d", resp.statusCode())));
                                return;
                            }
                            FileSystem fs = IntegrationTestUtils.getFileSystem();
                            String file = fs.createTempFileBlocking(name, suffix);
                            fs.writeFileBlocking(file, ar.result().body());
                            dlFuture.complete(Paths.get(file));
                        });
        return dlFuture;
    }
}
