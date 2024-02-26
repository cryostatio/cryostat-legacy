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
package io.cryostat.net.web.http.api.beta;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingArchiveHelper.ArchiveDirectory;
import io.cryostat.rules.ArchivePathException;

import com.google.gson.Gson;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;

class ArchivedDirectoriesGetHandler extends AbstractAuthenticatedRequestHandler {

    private final RecordingArchiveHelper recordingArchiveHelper;
    private final Gson gson;

    @Inject
    ArchivedDirectoriesGetHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            RecordingArchiveHelper recordingArchiveHelper,
            Gson gson) {
        super(auth, credentialsManager);
        this.recordingArchiveHelper = recordingArchiveHelper;
        this.gson = gson;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.BETA;
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
        return basePath() + "fs/recordings";
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public List<HttpMimeType> produces() {
        return List.of(HttpMimeType.JSON);
    }

    @Override
    public void handleAuthenticated(RoutingContext ctx) throws Exception {
        try {
            List<ArchiveDirectory> result =
                    recordingArchiveHelper.getRecordingsAndDirectories().get();
            ctx.response().end(gson.toJson(result));
        } catch (ExecutionException e) {
            if (e.getCause() instanceof ArchivePathException) {
                throw new HttpException(500, e.getMessage(), e);
            }
            throw e;
        }
    }
}
