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

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.jmc.serialization.HyperlinkedSerializableRecordingDescriptor;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.recordings.RecordingTargetHelper;
import io.cryostat.recordings.RecordingTargetHelper.SnapshotCreationException;

import com.google.gson.Gson;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.exception.ExceptionUtils;

class TargetSnapshotPostHandler
        extends AbstractV2RequestHandler<HyperlinkedSerializableRecordingDescriptor> {

    private final RecordingTargetHelper recordingTargetHelper;

    @Inject
    TargetSnapshotPostHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            RecordingTargetHelper recordingTargetHelper,
            Gson gson) {
        super(auth, credentialsManager, gson);
        this.recordingTargetHelper = recordingTargetHelper;
    }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.V2;
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
    public List<HttpMimeType> produces() {
        return List.of(HttpMimeType.PLAINTEXT);
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public IntermediateResponse<HyperlinkedSerializableRecordingDescriptor> handle(
            RequestParameters requestParams) throws Exception {
        ConnectionDescriptor connectionDescriptor =
                getConnectionDescriptorFromParams(requestParams);

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
            return new IntermediateResponse<HyperlinkedSerializableRecordingDescriptor>()
                    .statusCode(202)
                    .statusMessage(
                            String.format(
                                    "Snapshot %s failed to create: The resultant recording was"
                                        + " unreadable for some reason, likely due to a lack of"
                                        + " Active, non-Snapshot source recordings to take event"
                                        + " data from.",
                                    snapshotName))
                    .body(null);
        } else {
            return new IntermediateResponse<HyperlinkedSerializableRecordingDescriptor>()
                    .statusCode(201)
                    .addHeader(HttpHeaders.LOCATION, snapshotDescriptor.getDownloadUrl())
                    .body(snapshotDescriptor);
        }
    }

    private void handleExecutionException(ExecutionException e) throws ExecutionException {
        Throwable cause = ExceptionUtils.getRootCause(e);
        if (cause instanceof SnapshotCreationException) {
            throw new ApiException(500, cause.getMessage());
        }
        throw e;
    }
}
