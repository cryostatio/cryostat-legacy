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

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import itest.bases.StandardSelfTest;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(OrderAnnotation.class)
class DiscoveryPluginIT extends StandardSelfTest {

    final String realm = getClass().getSimpleName();
    final URI callback = URI.create("http://localhost:8181/");
    private static volatile String id;
    private static volatile String token;

    @Test
    @Order(1)
    void shouldBeAbleToRegister() throws InterruptedException, ExecutionException {
        JsonObject body = new JsonObject(Map.of("realm", realm, "callback", callback));

        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        webClient
                .post("/api/v2.2/discovery")
                .putHeader("Authorization", "None")
                .sendJson(
                        body,
                        ar -> {
                            assertRequestStatus(ar, response);
                            response.complete(ar.result().bodyAsJsonObject());
                        });
        JsonObject resp = response.get();
        JsonObject info = resp.getJsonObject("data").getJsonObject("result");
        DiscoveryPluginIT.id = info.getString("id");
        DiscoveryPluginIT.token = info.getString("token");
        MatcherAssert.assertThat(id, Matchers.not(Matchers.emptyOrNullString()));
        MatcherAssert.assertThat(token, Matchers.not(Matchers.emptyOrNullString()));
    }

    @Test
    @Order(2)
    void shouldFailToRegisterWithNonUriCallback() throws InterruptedException, ExecutionException {
        JsonObject body = new JsonObject(Map.of("realm", realm, "callback", "not a valid URI"));

        CompletableFuture<Integer> response = new CompletableFuture<>();
        webClient
                .post("/api/v2.2/discovery")
                .putHeader("Authorization", "None")
                .sendJson(
                        body,
                        ar -> {
                            response.complete(ar.result().statusCode());
                        });
        int code = response.get();
        MatcherAssert.assertThat(code, Matchers.equalTo(400));
    }

    @Test
    @Order(3)
    void shouldBeAbleToUpdate() throws InterruptedException, ExecutionException {
        JsonObject service = new JsonObject(Map.of("connectUrl", callback, "alias", "mynode"));
        JsonObject target =
                new JsonObject(
                        Map.of(
                                "target",
                                service,
                                "name",
                                getClass().getSimpleName(),
                                "nodeType",
                                "JVM"));
        JsonArray subtree = new JsonArray(List.of(target));

        CompletableFuture<Buffer> response = new CompletableFuture<>();
        webClient
                .post("/api/v2.2/discovery/" + id)
                .addQueryParam("token", token)
                .sendJson(
                        subtree,
                        ar -> {
                            assertRequestStatus(ar, response);
                            response.complete(ar.result().body());
                        });
        response.get();
    }

    @Test
    @Order(4)
    void shouldFailToUpdateWithInvalidSubtreeJson()
            throws InterruptedException, ExecutionException {
        JsonObject service = new JsonObject(Map.of("connectUrl", callback, "alias", "mynode"));
        JsonObject target =
                new JsonObject(
                        Map.of(
                                "target",
                                service,
                                "name",
                                getClass().getSimpleName(),
                                "nodeType",
                                "JVM"));
        JsonArray subtree = new JsonArray(List.of(target));
        String body = subtree.encode().replaceAll("\\[", "").replaceAll("\\]", "");

        CompletableFuture<Integer> response = new CompletableFuture<>();
        webClient
                .post("/api/v2.2/discovery/" + id)
                .addQueryParam("token", token)
                .sendBuffer(
                        Buffer.buffer(body),
                        ar -> {
                            response.complete(ar.result().statusCode());
                        });
        int code = response.get();
        MatcherAssert.assertThat(code, Matchers.equalTo(400));
    }

    @Test
    @Order(5)
    void shouldFailToReregisterWithoutToken() throws InterruptedException, ExecutionException {
        JsonObject body = new JsonObject(Map.of("realm", realm, "callback", callback));

        CompletableFuture<Integer> response = new CompletableFuture<>();
        webClient
                .post("/api/v2.2/discovery")
                .putHeader("Authorization", "None")
                .sendJson(
                        body,
                        ar -> {
                            response.complete(ar.result().statusCode());
                        });
        int code = response.get();
        MatcherAssert.assertThat(code, Matchers.equalTo(400));
    }

    @Test
    @Order(6)
    void shouldBeAbleToRefreshToken() throws InterruptedException, ExecutionException {
        JsonObject body =
                new JsonObject(Map.of("realm", realm, "callback", callback, "token", token));

        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        webClient
                .post("/api/v2.2/discovery")
                // intentionally don't include this header on refresh - it should still work
                // .putHeader("Authorization", "None")
                .sendJson(
                        body,
                        ar -> {
                            assertRequestStatus(ar, response);
                            response.complete(ar.result().bodyAsJsonObject());
                        });
        JsonObject resp = response.get();
        JsonObject info = resp.getJsonObject("data").getJsonObject("result");
        String newId = info.getString("id");
        MatcherAssert.assertThat(newId, Matchers.equalTo(DiscoveryPluginIT.id));
        MatcherAssert.assertThat(newId, Matchers.not(Matchers.emptyOrNullString()));
        DiscoveryPluginIT.id = newId;

        String newToken = info.getString("token");
        MatcherAssert.assertThat(newToken, Matchers.not(Matchers.equalTo(DiscoveryPluginIT.token)));
        MatcherAssert.assertThat(token, Matchers.not(Matchers.emptyOrNullString()));
        DiscoveryPluginIT.token = newToken;
    }

    @Test
    @Order(7)
    void shouldBeAbleToDeregister() throws InterruptedException, ExecutionException {
        CompletableFuture<Integer> response = new CompletableFuture<>();
        webClient
                .delete("/api/v2.2/discovery/" + id)
                .addQueryParam("token", token)
                .send(
                        ar -> {
                            assertRequestStatus(ar, response);
                            response.complete(ar.result().statusCode());
                        });
        int code = response.get();
        MatcherAssert.assertThat(code, Matchers.equalTo(200));
    }

    @Test
    @Order(8)
    void shouldFailToDoubleDeregister() throws InterruptedException, ExecutionException {
        CompletableFuture<Integer> response = new CompletableFuture<>();
        webClient
                .delete("/api/v2.2/discovery/" + id)
                .addQueryParam("token", token)
                .send(
                        ar -> {
                            response.complete(ar.result().statusCode());
                        });
        int code = response.get();
        MatcherAssert.assertThat(code, Matchers.equalTo(404));
    }

    @Test
    @Order(9)
    void shouldFailToUpdateUnregisteredPluginID() throws InterruptedException, ExecutionException {
        JsonObject service = new JsonObject(Map.of("connectUrl", callback, "alias", "mynode"));
        JsonObject target =
                new JsonObject(
                        Map.of(
                                "target",
                                service,
                                "name",
                                getClass().getSimpleName(),
                                "nodeType",
                                "JVM"));
        JsonArray subtree = new JsonArray(List.of(target));

        CompletableFuture<Integer> response = new CompletableFuture<>();
        webClient
                .post("/api/v2.2/discovery/" + UUID.randomUUID())
                .addQueryParam("token", token)
                .sendJson(
                        subtree,
                        ar -> {
                            response.complete(ar.result().statusCode());
                        });
        int code = response.get();
        MatcherAssert.assertThat(code, Matchers.equalTo(404));
    }

    @Test
    @Order(10)
    void shouldFailToRegisterNullCallback() throws InterruptedException, ExecutionException {
        JsonObject body = new JsonObject(Map.of("realm", realm));

        CompletableFuture<Integer> response = new CompletableFuture<>();
        webClient
                .post("/api/v2.2/discovery")
                .putHeader("Authorization", "None")
                .sendJson(
                        body,
                        ar -> {
                            response.complete(ar.result().statusCode());
                        });
        int code = response.get();
        MatcherAssert.assertThat(code, Matchers.equalTo(400));
    }

    @Test
    @Order(11)
    void shouldFailToRegisterEmptyCallback() throws InterruptedException, ExecutionException {
        JsonObject body = new JsonObject(Map.of("realm", realm, "callback", ""));

        CompletableFuture<Integer> response = new CompletableFuture<>();
        webClient
                .post("/api/v2.2/discovery")
                .putHeader("Authorization", "None")
                .sendJson(
                        body,
                        ar -> {
                            response.complete(ar.result().statusCode());
                        });
        int code = response.get();
        MatcherAssert.assertThat(code, Matchers.equalTo(400));
    }

    @Test
    @Order(12)
    void shouldFailToRegisterNullRealm() throws InterruptedException, ExecutionException {
        JsonObject body = new JsonObject(Map.of("callback", callback));

        CompletableFuture<Integer> response = new CompletableFuture<>();
        webClient
                .post("/api/v2.2/discovery")
                .putHeader("Authorization", "None")
                .sendJson(
                        body,
                        ar -> {
                            response.complete(ar.result().statusCode());
                        });
        int code = response.get();
        MatcherAssert.assertThat(code, Matchers.equalTo(400));
    }

    @Test
    @Order(13)
    void shouldFailToRegisterEmptyRealm() throws InterruptedException, ExecutionException {
        JsonObject body = new JsonObject(Map.of("realm", "", "callback", callback));

        CompletableFuture<Integer> response = new CompletableFuture<>();
        webClient
                .post("/api/v2.2/discovery")
                .putHeader("Authorization", "None")
                .sendJson(
                        body,
                        ar -> {
                            response.complete(ar.result().statusCode());
                        });
        int code = response.get();
        MatcherAssert.assertThat(code, Matchers.equalTo(400));
    }
}
