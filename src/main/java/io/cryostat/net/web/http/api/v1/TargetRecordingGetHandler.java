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
package io.cryostat.net.web.http.api.v1;

import java.io.InputStream;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.HttpServer;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.recordings.RecordingTargetHelper;
import io.cryostat.util.OutputToReadStream;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;

class TargetRecordingGetHandler extends AbstractAuthenticatedRequestHandler {
    protected static final int WRITE_BUFFER_SIZE = 64 * 1024; // 64 KB

    protected final TargetConnectionManager targetConnectionManager;
    protected final RecordingTargetHelper recordingTargetHelper;

    private final Vertx vertx;

    @Inject
    TargetRecordingGetHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            TargetConnectionManager targetConnectionManager,
            HttpServer httpServer,
            RecordingTargetHelper recordingTargetHelper,
            Logger logger) {
        super(auth, credentialsManager, logger);
        this.targetConnectionManager = targetConnectionManager;
        this.recordingTargetHelper = recordingTargetHelper;
        this.vertx = httpServer.getVertx();
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
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(ResourceAction.READ_TARGET, ResourceAction.READ_RECORDING);
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
    public List<HttpMimeType> produces() {
        return List.of(HttpMimeType.OCTET_STREAM);
    }

    @Override
    public void handleAuthenticated(RoutingContext ctx) throws Exception {
        String recordingName = ctx.pathParam("recordingName");
        if (recordingName != null && recordingName.endsWith(".jfr")) {
            recordingName = recordingName.substring(0, recordingName.length() - 4);
        }
        handleRecordingDownloadRequest(ctx, recordingName);
    }

    void handleRecordingDownloadRequest(RoutingContext ctx, String recordingName) throws Exception {
        ConnectionDescriptor connectionDescriptor = getConnectionDescriptorFromContext(ctx);
        Optional<InputStream> stream =
                recordingTargetHelper.getRecording(connectionDescriptor, recordingName).get();

        if (stream.isEmpty()) {
            throw new HttpException(404, String.format("%s not found", recordingName));
        }

        ctx.response().setChunked(true);
        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.OCTET_STREAM.mime());

        try (final InputStream is = stream.get();
                final OutputToReadStream otrs =
                        new OutputToReadStream(
                                vertx, targetConnectionManager, connectionDescriptor)) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            otrs.pipeFromInput(
                    is,
                    ctx.response(),
                    res -> {
                        if (res.succeeded()) {
                            future.complete(null);
                        } else {
                            future.completeExceptionally(res.cause());
                        }
                    });
            future.get();
        }
    }
}
