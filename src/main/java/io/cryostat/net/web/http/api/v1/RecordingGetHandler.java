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

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.DeprecatedApi;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingNotFoundException;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;

@DeprecatedApi(
        deprecated = @Deprecated(forRemoval = true),
        alternateLocation = "/api/beta/recordings/:sourceTarget/:recordingName")
class RecordingGetHandler extends AbstractAuthenticatedRequestHandler {

    private final RecordingArchiveHelper recordingArchiveHelper;

    @Inject
    RecordingGetHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            RecordingArchiveHelper recordingArchiveHelper,
            Logger logger) {
        super(auth, credentialsManager, logger);
        this.recordingArchiveHelper = recordingArchiveHelper;
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
    public List<HttpMimeType> produces() {
        return List.of(HttpMimeType.OCTET_STREAM);
    }

    @Override
    public void handleAuthenticated(RoutingContext ctx) throws Exception {
        String recordingName = ctx.pathParam("recordingName");
        try {
            Path archivedRecording = recordingArchiveHelper.getRecordingPath(recordingName).get();
            ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.OCTET_STREAM.mime());
            ctx.response()
                    .putHeader(
                            HttpHeaders.CONTENT_LENGTH,
                            Long.toString(archivedRecording.toFile().length()));
            ctx.response().sendFile(archivedRecording.toString());
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RecordingNotFoundException) {
                throw new HttpException(404, e.getMessage(), e);
            }
            throw e;
        }
    }
}
