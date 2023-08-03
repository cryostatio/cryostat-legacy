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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;

import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.net.web.http.api.v2.AbstractV2RequestHandler;
import io.cryostat.net.web.http.api.v2.ApiException;
import io.cryostat.net.web.http.api.v2.IntermediateResponse;
import io.cryostat.net.web.http.api.v2.RequestParameters;
import io.cryostat.recordings.RecordingMetadataManager;
import io.cryostat.recordings.RecordingMetadataManager.Metadata;
import io.cryostat.recordings.RecordingNotFoundException;
import io.cryostat.recordings.RecordingTargetHelper;

import com.google.gson.Gson;
import io.vertx.core.http.HttpMethod;

public class TargetRecordingMetadataLabelsPostHandler extends AbstractV2RequestHandler<Metadata> {

    static final String PATH = "targets/:targetId/recordings/:recordingName/metadata/labels";

    private final TargetConnectionManager targetConnectionManager;
    private final RecordingTargetHelper recordingTargetHelper;
    private final RecordingMetadataManager recordingMetadataManager;

    @Inject
    TargetRecordingMetadataLabelsPostHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            Gson gson,
            TargetConnectionManager targetConnectionManager,
            RecordingTargetHelper recordingTargetHelper,
            RecordingMetadataManager recordingMetadataManager) {
        super(auth, credentialsManager, gson);
        this.targetConnectionManager = targetConnectionManager;
        this.recordingTargetHelper = recordingTargetHelper;
        this.recordingMetadataManager = recordingMetadataManager;
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
    public String path() {
        return basePath() + PATH;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return Set.of(
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
        return List.of(HttpMimeType.JSON);
    }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }

    @Override
    public IntermediateResponse<Metadata> handle(RequestParameters params) throws Exception {
        String recordingName = params.getPathParams().get("recordingName");
        String targetId = params.getPathParams().get("targetId");

        try {
            Map<String, String> labels =
                    recordingMetadataManager.parseRecordingLabels(params.getBody());
            Metadata metadata = new Metadata(labels);

            ConnectionDescriptor connectionDescriptor = getConnectionDescriptorFromParams(params);

            if (!this.targetRecordingFound(connectionDescriptor, recordingName)) {
                throw new RecordingNotFoundException(targetId, recordingName);
            }

            Metadata updatedMetadata =
                    recordingMetadataManager
                            .setRecordingMetadata(
                                    connectionDescriptor, recordingName, metadata, true)
                            .get();

            return new IntermediateResponse<Metadata>().body(updatedMetadata);
        } catch (RecordingNotFoundException e) {
            throw new ApiException(404, e);
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, e);
        }
    }

    private boolean targetRecordingFound(
            ConnectionDescriptor connectionDescriptor, String recordingName) throws Exception {
        return targetConnectionManager.executeConnectedTask(
                connectionDescriptor,
                connection -> {
                    Optional<IRecordingDescriptor> descriptor =
                            recordingTargetHelper.getDescriptorByName(connection, recordingName);
                    return descriptor.isPresent();
                });
    }
}
