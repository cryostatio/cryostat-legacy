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

import io.cryostat.util.HttpStatusCodeIdentifier;

import io.vertx.core.AsyncResult;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.WebSocket;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import itest.util.Podman;
import itest.util.Utils;
import org.apache.http.client.utils.URLEncodedUtils;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;

public abstract class StandardSelfTest {

    public static final String SELF_REFERENCE_TARGET_ID =
            URLEncodedUtils.formatSegments(
                    String.format("service:jmx:rmi:///jndi/rmi://%s:9091/jmxrmi", Podman.POD_NAME));

    public static final int REQUEST_TIMEOUT_SECONDS = 30;
    public static final WebClient webClient = Utils.getWebClient();

    public static CompletableFuture<JsonObject> sendMessage(String command, String... args)
            throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<JsonObject> future = new CompletableFuture<>();

        Utils.HTTP_CLIENT.webSocket(
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

    public static void assertResponseStatus(JsonObject response) {
        assertResponseStatus(response, 0);
    }

    public static void assertResponseStatus(JsonObject response, int status) {
        MatcherAssert.assertThat(response.getInteger("status"), Matchers.equalTo(status));
    }

    public static boolean assertRequestStatus(
            AsyncResult<HttpResponse<Buffer>> result, CompletableFuture<?> future) {
        if (result.failed()) {
            result.cause().printStackTrace();
            future.completeExceptionally(result.cause());
            return false;
        }
        HttpResponse<Buffer> response = result.result();
        if (!HttpStatusCodeIdentifier.isSuccessCode(response.statusCode())) {
            System.err.println("HTTP " + response.statusCode() + ": " + response.statusMessage());
            future.completeExceptionally(new Exception(response.statusMessage()));
            return false;
        }
        return true;
    }

    private static Future<String> getClientUrl() {
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

    public static CompletableFuture<Path> downloadFile(String url, String name, String suffix) {
        return fireDownloadRequest(webClient.get(url), name, suffix);
    }

    public static CompletableFuture<Path> downloadFileAbs(String url, String name, String suffix) {
        return fireDownloadRequest(webClient.getAbs(url), name, suffix);
    }

    private static CompletableFuture<Path> fireDownloadRequest(
            HttpRequest<Buffer> request, String filename, String fileSuffix) {
        CompletableFuture<Path> future = new CompletableFuture<>();
        request.send(
                ar -> {
                    if (ar.failed()) {
                        future.completeExceptionally(ar.cause());
                        return;
                    }
                    HttpResponse<Buffer> resp = ar.result();
                    if (resp.statusCode() != 200) {
                        future.completeExceptionally(
                                new Exception(String.format("HTTP %d", resp.statusCode())));
                        return;
                    }
                    FileSystem fs = Utils.getFileSystem();
                    String file = fs.createTempFileBlocking(filename, fileSuffix);
                    fs.writeFileBlocking(file, ar.result().body());
                    future.complete(Paths.get(file));
                });
        return future;
    }
}
