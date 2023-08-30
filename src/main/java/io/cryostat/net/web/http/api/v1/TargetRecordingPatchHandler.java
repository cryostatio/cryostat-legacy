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

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;

import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;

public class TargetRecordingPatchHandler extends AbstractAuthenticatedRequestHandler {

    static final String PATH = "targets/:targetId/recordings/:recordingName";

    private final TargetRecordingPatchSave patchSave;
    private final TargetRecordingPatchStop patchStop;

    @Inject
    TargetRecordingPatchHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            TargetRecordingPatchSave patchSave,
            TargetRecordingPatchStop patchStop,
            Logger logger) {
        super(auth, credentialsManager, logger);
        this.patchSave = patchSave;
        this.patchStop = patchStop;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.V1;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.PATCH;
    }

    @Override
    public String path() {
        return basePath() + PATH;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(
                ResourceAction.READ_TARGET,
                ResourceAction.READ_RECORDING,
                ResourceAction.UPDATE_RECORDING);
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public List<HttpMimeType> produces() {
        return List.of(HttpMimeType.PLAINTEXT);
    }

    @Override
    public List<HttpMimeType> consumes() {
        return List.of(HttpMimeType.PLAINTEXT);
    }

    @Override
    public void handleAuthenticated(RoutingContext ctx) throws Exception {
        String mtd = ctx.body().asString();

        if (mtd == null) {
            throw new HttpException(400, "Unsupported null operation");
        }
        switch (mtd.toLowerCase()) {
            case "save":
                patchSave.handle(ctx, getConnectionDescriptorFromContext(ctx));
                break;
            case "stop":
                patchStop.handle(ctx, getConnectionDescriptorFromContext(ctx));
                break;
            default:
                throw new HttpException(400, "Unsupported operation " + mtd);
        }
    }
}
