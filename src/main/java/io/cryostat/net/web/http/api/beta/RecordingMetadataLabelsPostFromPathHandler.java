/*
 * Copyright The Cryostat Authors
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software (each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.cryostat.net.web.http.api.beta;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;
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
import io.cryostat.net.security.SecurityContext;
import io.cryostat.recordings.RecordingNotFoundException;
import io.cryostat.rules.ArchivePathException;

import com.google.gson.Gson;
import io.vertx.core.http.HttpMethod;

public class RecordingMetadataLabelsPostFromPathHandler extends AbstractV2RequestHandler<Metadata> {

    static final String PATH = "fs/recordings/:subdirectoryName/:recordingName/metadata/labels";

    private final RecordingArchiveHelper recordingArchiveHelper;
    private final RecordingMetadataManager recordingMetadataManager;
    private final Logger logger;

    @Inject
    RecordingMetadataLabelsPostFromPathHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            Gson gson,
            RecordingArchiveHelper recordingArchiveHelper,
            RecordingMetadataManager recordingMetadataManager,
            Logger logger) {
        super(auth, credentialsManager, gson);
        this.recordingArchiveHelper = recordingArchiveHelper;
        this.recordingMetadataManager = recordingMetadataManager;
        this.logger = logger;
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
    public SecurityContext securityContext(RequestParameters params) {
        String subdirectoryName = params.getPathParams().get("subdirectoryName");
        String recordingName = params.getPathParams().get("recordingName");
        try {
            Metadata m =
                    recordingMetadataManager.getMetadataFromPathIfExists(
                            subdirectoryName, recordingName);
            return m.getSecurityContext();
        } catch (IOException ioe) {
            logger.error(ioe);
        }
        return SecurityContext.DEFAULT;
    }

    @Override
    public IntermediateResponse<Metadata> handle(RequestParameters params) throws Exception {
        String recordingName = params.getPathParams().get("recordingName");
        String subdirectoryName = params.getPathParams().get("subdirectoryName");

        try {
            Map<String, String> labels =
                    recordingMetadataManager.parseRecordingLabels(params.getBody());

            Metadata oldMetadata =
                    recordingMetadataManager.getMetadataFromPathIfExists(
                            subdirectoryName, recordingName);

            Metadata updatedMetadata = new Metadata(oldMetadata.getSecurityContext(), labels);

            recordingArchiveHelper.getRecordingPathFromPath(subdirectoryName, recordingName).get();

            updatedMetadata =
                    recordingMetadataManager
                            .setRecordingMetadataFromPath(
                                    subdirectoryName, recordingName, updatedMetadata)
                            .get();

            return new IntermediateResponse<Metadata>().body(updatedMetadata);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RecordingNotFoundException
                    || e.getCause() instanceof ArchivePathException) {
                throw new ApiException(404, e);
            }
            throw new ApiException(500, e);
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, e);
        }
    }
}
