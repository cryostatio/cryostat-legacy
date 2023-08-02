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
package io.cryostat.net.web.http.api.v2;

import java.io.InputStream;
import java.util.EnumSet;
import java.util.Objects;
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
import io.cryostat.net.security.jwt.AssetJwtHelper;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.util.OutputToReadStream;

import com.nimbusds.jwt.JWT;
import dagger.Lazy;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;

class TargetRecordingGetHandler extends AbstractAssetJwtConsumingHandler {
    protected static final int WRITE_BUFFER_SIZE = 64 * 1024; // 64 KB

    private final TargetConnectionManager targetConnectionManager;
    private final Vertx vertx;

    @Inject
    TargetRecordingGetHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            AssetJwtHelper jwtFactory,
            Lazy<WebServer> webServer,
            HttpServer httpServer,
            TargetConnectionManager targetConnectionManager,
            Logger logger) {
        super(auth, credentialsManager, jwtFactory, webServer, logger);
        this.targetConnectionManager = targetConnectionManager;
        this.vertx = httpServer.getVertx();
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.V2_1;
    }

    @Override
    public String path() {
        return basePath() + "targets/:targetId/recordings/:recordingName";
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
    public boolean isAsync() {
        return false;
    }

    @Override
    public boolean isOrdered() {
        return true;
    }

    @Override
    public void handleWithValidJwt(RoutingContext ctx, JWT jwt) throws Exception {
        String recordingName = ctx.pathParam("recordingName");
        if (recordingName != null && recordingName.endsWith(".jfr")) {
            recordingName = recordingName.substring(0, recordingName.length() - 4);
        }
        handleRecordingDownloadRequest(ctx, jwt, recordingName);
    }

    void handleRecordingDownloadRequest(RoutingContext ctx, JWT jwt, String recordingName)
            throws Exception {
        ConnectionDescriptor connectionDescriptor = getConnectionDescriptorFromJwt(ctx, jwt);
        Optional<InputStream> stream =
                targetConnectionManager.executeConnectedTask(
                        connectionDescriptor,
                        conn ->
                                conn.getService().getAvailableRecordings().stream()
                                        .filter(r -> Objects.equals(recordingName, r.getName()))
                                        .map(
                                                desc -> {
                                                    try {
                                                        return conn.getService()
                                                                .openStream(desc, false);
                                                    } catch (Exception e) {
                                                        logger.error(e);
                                                        throw new ApiException(500, e);
                                                    }
                                                })
                                        .filter(Objects::nonNull)
                                        .findFirst());
        if (stream.isEmpty()) {
            throw new ApiException(404, String.format("%s not found", recordingName));
        }

        ctx.response().setChunked(true);
        ctx.response()
                .putHeader(
                        HttpHeaders.CONTENT_DISPOSITION,
                        String.format("attachment; filename=\"%s.jfr\"", recordingName));
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
            try {
                future.get();
            } catch (Exception e) {
                throw new ApiException(500, e);
            }
        }
    }
}
