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
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.jmc.serialization.HyperlinkedSerializableRecordingDescriptor;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.recordings.RecordingTargetHelper;
import io.cryostat.recordings.RecordingTargetHelper.SnapshotCreationException;

import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;
import org.apache.commons.lang3.exception.ExceptionUtils;

class TargetSnapshotPostHandler extends AbstractAuthenticatedRequestHandler {

    private final RecordingTargetHelper recordingTargetHelper;

    @Inject
    TargetSnapshotPostHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            RecordingTargetHelper recordingTargetHelper,
            Logger logger) {
        super(auth, credentialsManager, logger);
        this.recordingTargetHelper = recordingTargetHelper;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.V1;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.POST;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(ResourceAction.READ_TARGET, ResourceAction.UPDATE_RECORDING);
    }

    @Override
    public String path() {
        return basePath() + "targets/:targetId/snapshot";
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
    public void handleAuthenticated(RoutingContext ctx) throws Exception {
        ConnectionDescriptor connectionDescriptor = getConnectionDescriptorFromContext(ctx);

        HyperlinkedSerializableRecordingDescriptor snapshotDescriptor = null;
        try {
            snapshotDescriptor = recordingTargetHelper.createSnapshot(connectionDescriptor).get();
        } catch (ExecutionException e) {
            handleExecutionException(e);
        }
        String snapshotName = snapshotDescriptor.getName();

        boolean verificationSuccessful = false;
        try {
            verificationSuccessful =
                    recordingTargetHelper
                            .verifySnapshot(connectionDescriptor, snapshotDescriptor)
                            .get();
        } catch (ExecutionException e) {
            handleExecutionException(e);
        }

        if (!verificationSuccessful) {
            ctx.response().setStatusCode(202);
            ctx.response()
                    .end(
                            String.format(
                                    "Snapshot %s failed to create: The resultant recording was"
                                        + " unreadable for some reason, likely due to a lack of"
                                        + " Active, non-Snapshot source recordings to take event"
                                        + " data from.",
                                    snapshotName));
        } else {
            ctx.response().setStatusCode(200);
            ctx.response().end(snapshotName);
        }
    }

    private void handleExecutionException(ExecutionException e) throws ExecutionException {
        Throwable cause = ExceptionUtils.getRootCause(e);
        if (cause instanceof SnapshotCreationException) {
            throw new HttpException(500, cause.getMessage());
        }
        throw e;
    }
}
