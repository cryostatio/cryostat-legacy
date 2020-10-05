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
package com.redhat.rhjmc.containerjfr.net.web.http.api.v1;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;

import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.net.AuthManager;
import com.redhat.rhjmc.containerjfr.net.ConnectionDescriptor;
import com.redhat.rhjmc.containerjfr.net.TargetConnectionManager;
import com.redhat.rhjmc.containerjfr.net.web.WebServer.DownloadDescriptor;
import com.redhat.rhjmc.containerjfr.net.web.http.AbstractAuthenticatedRequestHandler;
import com.redhat.rhjmc.containerjfr.net.web.http.HttpMimeType;
import com.redhat.rhjmc.containerjfr.net.web.http.api.ApiVersion;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.impl.HttpStatusException;

class TargetRecordingGetHandler extends AbstractAuthenticatedRequestHandler {
    static final String USE_LOW_MEM_PRESSURE_STREAMING_ENV = "USE_LOW_MEM_PRESSURE_STREAMING";

    protected static final int WRITE_BUFFER_SIZE = 64 * 1024; // 64 KB

    protected final Environment env;
    protected final TargetConnectionManager targetConnectionManager;
    protected final Logger logger;

    @Inject
    TargetRecordingGetHandler(
            AuthManager auth,
            Environment env,
            TargetConnectionManager targetConnectionManager,
            Logger logger) {
        super(auth);
        this.env = env;
        this.targetConnectionManager = targetConnectionManager;
        this.logger = logger;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.V1;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.GET;
    }

    @Override
    public String path() {
        return basePath() + "targets/:targetId/recordings/:recordingName";
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public void handleAuthenticated(RoutingContext ctx) throws Exception {
        String recordingName = ctx.pathParam("recordingName");
        if (recordingName != null && recordingName.endsWith(".jfr")) {
            recordingName = recordingName.substring(0, recordingName.length() - 4);
        }
        handleRecordingDownloadRequest(ctx, recordingName);
    }

    // try-with-resources generates a "redundant" nullcheck in bytecode
    @SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE")
    void handleRecordingDownloadRequest(RoutingContext ctx, String recordingName) throws Exception {
        ConnectionDescriptor connectionDescriptor = getConnectionDescriptorFromContext(ctx);
        Optional<DownloadDescriptor> descriptor =
                getRecordingDescriptor(connectionDescriptor, recordingName);
        if (descriptor.isEmpty()) {
            throw new HttpStatusException(404, String.format("%s not found", recordingName));
        }

        ctx.response().setChunked(true);
        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.OCTET_STREAM.mime());
        descriptor
                .get()
                .bytes
                .ifPresent(
                        b ->
                                ctx.response()
                                        .putHeader(HttpHeaders.CONTENT_LENGTH, Long.toString(b)));
        try (InputStream stream = descriptor.get().stream) {
            if (env.hasEnv(USE_LOW_MEM_PRESSURE_STREAMING_ENV)) {
                writeInputStreamLowMemPressure(stream, ctx.response());
            } else {
                writeInputStream(stream, ctx.response());
            }
            ctx.response().end();
        } finally {
            descriptor
                    .get()
                    .resource
                    .ifPresent(
                            resource -> {
                                try {
                                    resource.close();
                                } catch (Exception e) {
                                    logger.warn(e);
                                }
                            });
        }
    }

    Optional<DownloadDescriptor> getRecordingDescriptor(
            ConnectionDescriptor connectionDescriptor, String recordingName) throws Exception {
        JFRConnection connection = targetConnectionManager.connect(connectionDescriptor);
        Optional<IRecordingDescriptor> desc =
                connection.getService().getAvailableRecordings().stream()
                        .filter(r -> Objects.equals(recordingName, r.getName()))
                        .findFirst();
        if (desc.isPresent()) {
            return Optional.of(
                    new DownloadDescriptor(
                            connection.getService().openStream(desc.get(), false),
                            null,
                            connection));
        } else {
            connection.close();
            return Optional.empty();
        }
    }

    HttpServerResponse writeInputStreamLowMemPressure(
            InputStream inputStream, HttpServerResponse response) throws IOException {
        // blocking function, must be called from a blocking handler
        byte[] buff = new byte[WRITE_BUFFER_SIZE];
        Buffer chunk = Buffer.buffer();

        ExecutorService worker = Executors.newSingleThreadExecutor();
        CompletableFuture<Void> future = new CompletableFuture<>();
        worker.submit(
                new Runnable() {
                    @Override
                    public void run() {
                        int n;
                        try {
                            n = inputStream.read(buff);
                        } catch (IOException e) {
                            future.completeExceptionally(e);
                            return;
                        }

                        if (n == -1) {
                            future.complete(null);
                            return;
                        }

                        chunk.setBytes(0, buff, 0, n);
                        response.write(
                                chunk.slice(0, n),
                                (res) -> {
                                    if (res.failed()) {
                                        future.completeExceptionally(res.cause());
                                        return;
                                    }
                                    worker.submit(this); // recursive call on this runnable itself
                                });
                    }
                });

        try {
            future.join();
            worker.shutdownNow();
        } catch (CompletionException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            } else {
                throw e;
            }
        }

        return response;
    }

    HttpServerResponse writeInputStream(InputStream inputStream, HttpServerResponse response)
            throws IOException {
        // blocking function, must be called from a blocking handler
        byte[] buff = new byte[WRITE_BUFFER_SIZE]; // 64 KB
        int n;
        while (true) {
            n = inputStream.read(buff);
            if (n == -1) {
                break;
            }
            response.write(Buffer.buffer().appendBytes(buff, 0, n));
        }

        return response;
    }
}
