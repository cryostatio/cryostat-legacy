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

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.jwt.AssetJwtHelper;
import io.cryostat.net.web.DeprecatedApi;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingNotFoundException;

import com.nimbusds.jwt.JWT;
import dagger.Lazy;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;

@DeprecatedApi(
        deprecated = @Deprecated(forRemoval = true),
        alternateLocation = "/api/beta/recordings/:sourceTarget/:recordingName")
class RecordingGetHandler extends AbstractAssetJwtConsumingHandler {

    private final RecordingArchiveHelper recordingArchiveHelper;

    @Inject
    RecordingGetHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            AssetJwtHelper jwtFactory,
            Lazy<WebServer> webServer,
            RecordingArchiveHelper recordingArchiveHelper,
            Logger logger) {
        super(auth, credentialsManager, jwtFactory, webServer, logger);
        this.recordingArchiveHelper = recordingArchiveHelper;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.V2_1;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.GET;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(ResourceAction.READ_RECORDING);
    }

    @Override
    public String path() {
        return basePath() + "recordings/:recordingName";
    }

    @Override
    public boolean isAsync() {
        return true;
    }

    @Override
    public void handleWithValidJwt(RoutingContext ctx, JWT jwt) throws Exception {
        String recordingName = ctx.pathParam("recordingName");
        try {
            Path archivedRecording = recordingArchiveHelper.getRecordingPath(recordingName).get();
            ctx.response()
                    .putHeader(
                            HttpHeaders.CONTENT_DISPOSITION,
                            String.format("attachment; filename=\"%s\"", recordingName));
            ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.OCTET_STREAM.mime());
            ctx.response()
                    .putHeader(
                            HttpHeaders.CONTENT_LENGTH,
                            Long.toString(archivedRecording.toFile().length()));
            ctx.response().sendFile(archivedRecording.toAbsolutePath().toString());
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RecordingNotFoundException) {
                throw new ApiException(404, e.getMessage(), e);
            }
            throw e;
        }
    }
}
