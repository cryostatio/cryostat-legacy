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
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.net.web.http.api.v2.AbstractV2RequestHandler;
import io.cryostat.net.web.http.api.v2.ApiException;
import io.cryostat.net.web.http.api.v2.IntermediateResponse;
import io.cryostat.net.web.http.api.v2.RequestParameters;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingMetadataManager;
import io.cryostat.recordings.RecordingMetadataManager.Metadata;
import io.cryostat.recordings.RecordingNotFoundException;

import com.google.gson.Gson;
import io.vertx.core.http.HttpMethod;

public class RecordingMetadataLabelsPostHandler extends AbstractV2RequestHandler<Metadata> {

    static final String PATH = "recordings/:sourceTarget/:recordingName/metadata/labels";

    private final RecordingArchiveHelper recordingArchiveHelper;
    private final RecordingMetadataManager recordingMetadataManager;

    @Inject
    RecordingMetadataLabelsPostHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            Gson gson,
            RecordingArchiveHelper recordingArchiveHelper,
            RecordingMetadataManager recordingMetadataManager) {
        super(auth, credentialsManager, gson);
        this.recordingArchiveHelper = recordingArchiveHelper;
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
        return Set.of(ResourceAction.READ_RECORDING, ResourceAction.UPDATE_RECORDING);
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
        String sourceTarget = params.getPathParams().get("sourceTarget");

        try {
            Metadata metadata =
                    new Metadata(recordingMetadataManager.parseRecordingLabels(params.getBody()));

            recordingArchiveHelper.getRecordingPath(recordingName).get();

            Metadata updatedMetadata =
                    recordingMetadataManager
                            .setRecordingMetadata(
                                    new ConnectionDescriptor(sourceTarget),
                                    recordingName,
                                    metadata,
                                    true)
                            .get();

            return new IntermediateResponse<Metadata>().body(updatedMetadata);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RecordingNotFoundException) {
                throw new ApiException(404, e);
            }
            throw new ApiException(500, e);
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, e);
        }
    }
}
