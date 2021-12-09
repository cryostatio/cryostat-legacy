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
package io.cryostat.net.web.http.api.v2;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;

import com.google.gson.Gson;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.ext.web.codec.BodyCodec;

class PodmanApiPostHandler extends AbstractV2RequestHandler<String> {

    private final WebClient webClient;
    private final Logger logger;

    @Inject
    PodmanApiPostHandler(AuthManager auth, Gson gson, Vertx vertx, Logger logger) {
        super(auth, gson);
        this.webClient =
                WebClient.create(
                        vertx,
                        new WebClientOptions()
                                .setTcpFastOpen(true)
                                .setTcpNoDelay(true)
                                .setTcpQuickAck(true));
        this.logger = logger;
    }

    @Override
    public String path() {
        return basePath() + "podman";
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.POST;
    }

    @Override
    public HttpMimeType mimeType() {
        return HttpMimeType.JSON;
    }

    @Override
    public boolean requiresAuthentication() {
        return false;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return ResourceAction.NONE;
    }

    @Override
    public IntermediateResponse<String> handle(RequestParameters requestParams) throws Exception {
        String podmanPath = requestParams.getFormAttributes().get("podmanPath");
        if (podmanPath == null) {
            podmanPath = "/libpod/info";
        }
        String requestPath = String.format("http://d/v3.0.0/%s", podmanPath);
        requestPath = URI.create(requestPath).normalize().toString();
        logger.info("requestPath: {}", requestPath);

        CompletableFuture<String> future = new CompletableFuture<>();
        webClient
                .request(
                        HttpMethod.GET,
                        // FIXME replace 0 with lookup for actual user ID
                        SocketAddress.domainSocketAddress("/run/user/1000/podman/podman.sock"),
                        80,
                        "localhost",
                        requestPath)
                .timeout(5_000L)
                .expect(ResponsePredicate.SC_ACCEPTED)
                .as(BodyCodec.jsonObject())
                .send(
                        ar -> {
                            if (ar.failed()) {
                                Throwable t = ar.cause();
                                t.printStackTrace();
                                future.completeExceptionally(t);
                                return;
                            }
                            HttpResponse<JsonObject> response = ar.result();
                            future.complete(response.body().encodePrettily());
                        });

        return new IntermediateResponse<String>().body(future.get(5_000, TimeUnit.MILLISECONDS));
    }
}
