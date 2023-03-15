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
package io.cryostat.net;

import java.net.URI;
import java.time.Duration;

import javax.script.ScriptException;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.Credentials;
import io.cryostat.core.net.MBeanMetrics;
import io.cryostat.util.HttpStatusCodeIdentifier;

import com.google.gson.Gson;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.auth.authentication.UsernamePasswordCredentials;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;
import org.apache.commons.lang3.exception.ExceptionUtils;

class AgentClient {

    private final Vertx vertx;
    private final Gson gson;
    private final long httpTimeout;
    private final WebClient webClient;
    private final CredentialsManager credentialsManager;
    private final URI agentUri;
    private final Logger logger;

    AgentClient(
            Vertx vertx,
            Gson gson,
            long httpTimeout,
            WebClient webClient,
            CredentialsManager credentialsManager,
            URI agentUri,
            Logger logger) {
        this.vertx = vertx;
        this.gson = gson;
        this.httpTimeout = httpTimeout;
        this.webClient = webClient;
        this.credentialsManager = credentialsManager;
        this.agentUri = agentUri;
        this.logger = logger;
    }

    URI getUri() {
        return agentUri;
    }

    Future<Boolean> ping() {
        Future<HttpResponse<Void>> f = invoke(HttpMethod.GET, "/", BodyCodec.none());
        return f.map(HttpResponse::statusCode).map(HttpStatusCodeIdentifier::isSuccessCode);
    }

    Future<MBeanMetrics> mbeanMetrics() {
        Future<HttpResponse<String>> f =
                invoke(HttpMethod.GET, "/mbean-metrics", BodyCodec.string());
        return f.map(HttpResponse::body)
                .map(
                        s -> {
                            logger.info("mbean response: {}", s);
                            return gson.fromJson(s, MBeanMetrics.class);
                        });
    }

    private <T> Future<HttpResponse<T>> invoke(HttpMethod mtd, String path, BodyCodec<T> codec) {
        return vertx.executeBlocking(
                promise -> {
                    logger.info("{} {} {}", mtd, agentUri, path);
                    HttpRequest<T> req =
                            webClient
                                    .request(mtd, agentUri.getPort(), agentUri.getHost(), path)
                                    .ssl("https".equals(agentUri.getScheme()))
                                    .timeout(Duration.ofSeconds(httpTimeout).toMillis())
                                    .followRedirects(true)
                                    .as(codec);
                    try {
                        Credentials credentials =
                                credentialsManager.getCredentialsByTargetId(agentUri.toString());
                        req =
                                req.authentication(
                                        new UsernamePasswordCredentials(
                                                credentials.getUsername(),
                                                credentials.getPassword()));
                    } catch (ScriptException se) {
                        promise.fail(se);
                        return;
                    }

                    req.send()
                            .onComplete(
                                    ar -> {
                                        if (ar.failed()) {
                                            logger.warn(
                                                    "{} {}{} failed: {}",
                                                    mtd,
                                                    agentUri,
                                                    path,
                                                    ExceptionUtils.getStackTrace(ar.cause()));
                                            promise.fail(ar.cause());
                                            return;
                                        }
                                        logger.info(
                                                "{} {}{} status {}: {}",
                                                mtd,
                                                agentUri,
                                                path,
                                                ar.result().statusCode(),
                                                ar.result().statusMessage());
                                        promise.complete(ar.result());
                                    });
                });
    }

    static class Factory {

        private final Vertx vertx;
        private final Gson gson;
        private final long httpTimeout;
        private final WebClient webClient;
        private final CredentialsManager credentialsManager;
        private final Logger logger;

        Factory(
                Vertx vertx,
                Gson gson,
                long httpTimeout,
                WebClient webClient,
                CredentialsManager credentialsManager,
                Logger logger) {
            this.vertx = vertx;
            this.gson = gson;
            this.httpTimeout = httpTimeout;
            this.webClient = webClient;
            this.credentialsManager = credentialsManager;
            this.logger = logger;
        }

        AgentClient create(URI agentUri) {
            return new AgentClient(
                    vertx, gson, httpTimeout, webClient, credentialsManager, agentUri, logger);
        }
    }
}
