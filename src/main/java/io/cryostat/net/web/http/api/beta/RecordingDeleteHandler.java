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
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;

import com.google.gson.Gson;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.net.web.http.api.v2.AbstractV2RequestHandler;
import io.cryostat.net.web.http.api.v2.ApiException;
import io.cryostat.net.web.http.api.v2.IntermediateResponse;
import io.cryostat.net.web.http.api.v2.RequestParameters;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingMetadataManager.Metadata;
import io.cryostat.recordings.RecordingMetadataManager.SecurityContext;
import io.cryostat.recordings.RecordingNotFoundException;
import io.cryostat.rules.ArchivedRecordingInfo;
import io.vertx.core.http.HttpMethod;

public class RecordingDeleteHandler extends AbstractV2RequestHandler<Void> {

    static final String PATH = "recordings/:sourceTarget/:recordingName";

    private final RecordingArchiveHelper recordingArchiveHelper;

    @Inject
    RecordingDeleteHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            Gson gson,
            RecordingArchiveHelper recordingArchiveHelper) {
        super(auth, credentialsManager, gson);
        this.recordingArchiveHelper = recordingArchiveHelper;
    }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.BETA;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.DELETE;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(ResourceAction.DELETE_RECORDING);
    }

    @Override
    public String path() {
        return basePath() + PATH;
    }

    @Override
    public List<HttpMimeType> produces() {
        return List.of(HttpMimeType.JSON);
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public SecurityContext securityContext(RequestParameters params) {
        String sourceTarget = params.getPathParams().get("sourceTarget");
        String recordingName = params.getPathParams().get("recordingName");
        try {
            return recordingArchiveHelper
                    .getRecordings(sourceTarget)
                    .get()
                    .stream()
                    .filter(r -> r.getName().equals(recordingName))
                    .findFirst()
                    .map(ArchivedRecordingInfo::getMetadata)
                    .map(Metadata::getSecurityContext)
                    .orElse(SecurityContext.DEFAULT);
        } catch (InterruptedException | ExecutionException e) {
            return null;
        }
    }

    @Override
    public IntermediateResponse<Void> handle(RequestParameters params) throws Exception {
        String sourceTarget = params.getPathParams().get("sourceTarget");
        String recordingName = params.getPathParams().get("recordingName");
        try {
            recordingArchiveHelper.deleteRecording(sourceTarget, recordingName).get();
            return new IntermediateResponse<Void>().body(null);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RecordingNotFoundException) {
                throw new ApiException(404, e.getMessage(), e);
            }
            throw e;
        }
    }
}
