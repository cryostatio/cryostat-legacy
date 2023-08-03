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

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;

import io.cryostat.MainModule;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.recordings.RecordingArchiveHelper;

import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

public class RecordingsFromIdPostBodyHandler extends AbstractAuthenticatedRequestHandler {

    private final BodyHandler bodyHandler;

    @Inject
    RecordingsFromIdPostBodyHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            @Named(MainModule.RECORDINGS_PATH) Path recordingsPath,
            FileSystem fs,
            Logger logger) {
        super(auth, credentialsManager, logger);
        Path fileUploads = recordingsPath.resolve(RecordingArchiveHelper.TEMP_UPLOADS_SUBDIRECTORY);
        this.bodyHandler =
                BodyHandler.create(true)
                        .setUploadsDirectory(fileUploads.toAbsolutePath().toString());
    }

    @Override
    public int getPriority() {
        return DEFAULT_PRIORITY - 1;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.BETA;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.POST;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return ResourceAction.NONE;
    }

    @Override
    public String path() {
        return basePath() + RecordingsFromIdPostHandler.PATH;
    }

    @Override
    public List<HttpMimeType> produces() {
        return List.of(HttpMimeType.JSON);
    }

    @Override
    public List<HttpMimeType> consumes() {
        return List.of(HttpMimeType.MULTIPART_FORM);
    }

    @Override
    public void handleAuthenticated(RoutingContext ctx) throws Exception {
        this.bodyHandler.handle(ctx);
    }
}
