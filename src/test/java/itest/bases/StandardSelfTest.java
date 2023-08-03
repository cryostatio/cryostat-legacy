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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.cryostat.util.HttpStatusCodeIdentifier;

import io.vertx.core.AsyncResult;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.WebSocket;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.HttpException;
import itest.util.Podman;
import itest.util.Utils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.client.utils.URLEncodedUtils;

public abstract class StandardSelfTest {

    public static final String SELF_REFERENCE_TARGET_ID =
            URLEncodedUtils.formatSegments(
                    String.format("service:jmx:rmi:///jndi/rmi://%s:9091/jmxrmi", Podman.POD_NAME));
    public static final Pair<String, String> VERTX_FIB_CREDENTIALS =
            Pair.of("admin", "adminpass123");

    public static final int REQUEST_TIMEOUT_SECONDS = 30;
    public static final WebClient webClient = Utils.getWebClient();

    public static CompletableFuture<JsonObject> expectNotification(
            String category, long timeout, TimeUnit unit)
            throws TimeoutException, ExecutionException, InterruptedException {
        CompletableFuture<JsonObject> future = new CompletableFuture<>();

        Utils.HTTP_CLIENT.webSocket(
                getNotificationsUrl().get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                ar -> {
                    if (ar.failed()) {
                        future.completeExceptionally(ar.cause());
                        return;
                    }
                    WebSocket ws = ar.result();

                    ws.handler(
                                    m -> {
                                        JsonObject resp = m.toJsonObject();
                                        JsonObject meta = resp.getJsonObject("meta");
                                        String c = meta.getString("category");
                                        if (Objects.equals(c, category)) {
                                            ws.end(unused -> future.complete(resp));
                                            ws.close();
                                        }
                                    })
                            // just to initialize the connection - Cryostat expects
                            // clients to send a message after the connection opens
                            // to authenticate themselves, but in itests we don't
                            // use auth
                            .writeTextMessage("");
                });

        return future.orTimeout(timeout, unit);
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
            future.completeExceptionally(
                    new HttpException(response.statusCode(), response.statusMessage()));
            return false;
        }
        return true;
    }

    private static Future<String> getNotificationsUrl() {
        CompletableFuture<String> future = new CompletableFuture<>();
        webClient
                .get("/api/v1/notifications_url")
                .send(
                        ar -> {
                            if (ar.succeeded()) {
                                future.complete(
                                        ar.result()
                                                .bodyAsJsonObject()
                                                .getString("notificationsUrl"));
                            } else {
                                future.completeExceptionally(ar.cause());
                            }
                        });
        return future;
    }

    public static CompletableFuture<Path> downloadFile(String url, String name, String suffix) {
        return fireDownloadRequest(
                webClient.get(url), name, suffix, MultiMap.caseInsensitiveMultiMap());
    }

    public static CompletableFuture<Path> downloadFileAbs(String url, String name, String suffix) {
        return fireDownloadRequest(
                webClient.getAbs(url), name, suffix, MultiMap.caseInsensitiveMultiMap());
    }

    public static CompletableFuture<Path> downloadFileAbs(
            String url, String name, String suffix, MultiMap headers) {
        return fireDownloadRequest(webClient.getAbs(url), name, suffix, headers);
    }

    private static CompletableFuture<Path> fireDownloadRequest(
            HttpRequest<Buffer> request, String filename, String fileSuffix, MultiMap headers) {
        CompletableFuture<Path> future = new CompletableFuture<>();
        request.putHeaders(headers)
                .send(
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
