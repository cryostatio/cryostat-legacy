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

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpRequest;
import itest.bases.StandardSelfTest;
import itest.util.Utils;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NotificationsUrlIT extends StandardSelfTest {

    HttpRequest<Buffer> req;

    @BeforeEach
    void createRequest() {
        req = webClient.get("/api/v1/notifications_url");
    }

    @Test
    public void shouldSucceed() throws Exception {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        req.send(
                ar -> {
                    if (ar.succeeded()) {
                        future.complete(ar.result().statusCode());
                    } else {
                        future.completeExceptionally(ar.cause());
                    }
                });
        MatcherAssert.assertThat(
                future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS), Matchers.equalTo(200));
    }

    @Test
    public void shouldReturnOK() throws Exception {
        CompletableFuture<String> future = new CompletableFuture<>();
        req.send(
                ar -> {
                    if (ar.succeeded()) {
                        future.complete(ar.result().statusMessage());
                    } else {
                        future.completeExceptionally(ar.cause());
                    }
                });
        MatcherAssert.assertThat(
                future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS), Matchers.equalTo("OK"));
    }

    @Test
    public void shouldReturnContentTypeJson() throws Exception {
        CompletableFuture<String> future = new CompletableFuture<>();
        req.send(
                ar -> {
                    if (ar.succeeded()) {
                        future.complete(ar.result().getHeader("Content-Type"));
                    } else {
                        future.completeExceptionally(ar.cause());
                    }
                });
        MatcherAssert.assertThat(
                future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                Matchers.equalTo("application/json"));
    }

    @Test
    public void shouldReturnJsonMessage() throws Exception {
        CompletableFuture<String> future = new CompletableFuture<>();
        req.send(
                ar -> {
                    if (ar.succeeded()) {
                        future.complete(ar.result().bodyAsString());
                    } else {
                        future.completeExceptionally(ar.cause());
                    }
                });
        MatcherAssert.assertThat(
                future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                Matchers.equalTo(
                        String.format(
                                "{\"notificationsUrl\":\"ws://%s:%d/api/v1/notifications\"}",
                                Utils.WEB_HOST, Utils.WEB_PORT)));
    }
}
