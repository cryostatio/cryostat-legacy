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
package io.cryostat.rules;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Function;

import io.cryostat.core.log.Logger;
import io.cryostat.core.net.Credentials;
import io.cryostat.platform.ServiceRef;
import io.cryostat.util.HttpStatusCodeIdentifier;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.apache.http.client.utils.URLEncodedUtils;

class PeriodicArchiver implements Runnable {

    private final ServiceRef serviceRef;
    private final Credentials credentials;
    private final Rule rule;
    private final WebClient webClient;
    private final String archiveRequestPath;
    private final String deleteRequestPath;
    private final Function<Credentials, MultiMap> headersFactory;
    private final Logger logger;

    private final Queue<String> previousRecordings;

    PeriodicArchiver(
            ServiceRef serviceRef,
            Credentials credentials,
            Rule rule,
            WebClient webClient,
            String archiveRequestPath,
            String deleteRequestPath,
            Function<Credentials, MultiMap> headersFactory,
            Logger logger) {
        this.webClient = webClient;
        this.archiveRequestPath = archiveRequestPath;
        this.deleteRequestPath = deleteRequestPath;
        this.serviceRef = serviceRef;
        this.credentials = credentials;
        this.rule = rule;
        this.headersFactory = headersFactory;
        this.logger = logger;

        // FIXME this needs to be populated at startup by scanning the existing archived recordings,
        // in case we have been restarted and already previously processed archival for this rule
        this.previousRecordings = new ArrayDeque<>(this.rule.getPreservedArchives());
    }

    @Override
    public void run() {
        logger.trace("PeriodicArchiver for {} running", rule.getRecordingName());

        try {
            while (this.previousRecordings.size() > this.rule.getPreservedArchives() - 1) {
                pruneArchive(this.previousRecordings.remove()).get();
            }

            performArchival();
        } catch (InterruptedException | ExecutionException e) {
            logger.error(e);
        }
    }

    void performArchival() throws InterruptedException, ExecutionException {
        // FIXME using an HTTP request to localhost here works well enough, but is needlessly
        // complex. The API handler targeted here should be refactored to extract the logic that
        // creates the recording from the logic that simply figures out the recording parameters
        // from the POST form, path param, and headers. Then the handler should consume the API
        // exposed by this refactored chunk, and this refactored chunk can also be consumed here
        // rather than firing HTTP requests to ourselves

        URI path =
                URI.create(
                                archiveRequestPath
                                        .replaceAll(
                                                ":targetId",
                                                URLEncodedUtils.formatSegments(
                                                        serviceRef.getJMXServiceUrl().toString()))
                                        .replaceAll(
                                                ":recordingName",
                                                URLEncodedUtils.formatSegments(
                                                        rule.getRecordingName())))
                        .normalize();

        CompletableFuture<String> future = new CompletableFuture<>();
        this.webClient
                .patch(path.toString())
                .putHeaders(headersFactory.apply(credentials))
                .sendBuffer(
                        Buffer.buffer("save"),
                        ar -> {
                            if (ar.failed()) {
                                this.logger.error(
                                        new IOException("Periodic archival failed", ar.cause()));
                                future.completeExceptionally(ar.cause());
                                return;
                            }
                            HttpResponse<Buffer> resp = ar.result();
                            if (!HttpStatusCodeIdentifier.isSuccessCode(resp.statusCode())) {
                                this.logger.error(resp.bodyAsString());
                                future.completeExceptionally(new IOException(resp.bodyAsString()));
                                return;
                            }
                            future.complete(resp.bodyAsString());
                        });
        this.previousRecordings.add(future.get());
    }

    Future<Boolean> pruneArchive(String recordingName) {
        logger.trace("Pruning {}", recordingName);
        URI path =
                URI.create(
                                deleteRequestPath.replaceAll(
                                        ":recordingName",
                                        URLEncodedUtils.formatSegments(recordingName)))
                        .normalize();

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        this.webClient
                .delete(path.toString())
                .putHeaders(headersFactory.apply(credentials))
                .send(
                        ar -> {
                            if (ar.failed()) {
                                this.logger.error(
                                        new IOException("Archival prune failed", ar.cause()));
                                future.completeExceptionally(ar.cause());
                                return;
                            }
                            HttpResponse<Buffer> resp = ar.result();
                            if (!HttpStatusCodeIdentifier.isSuccessCode(resp.statusCode())) {
                                this.logger.error(resp.bodyAsString());
                                future.completeExceptionally(new IOException(resp.bodyAsString()));
                                return;
                            }
                            previousRecordings.remove(recordingName);
                            future.complete(true);
                        });
        return future;
    }
}
