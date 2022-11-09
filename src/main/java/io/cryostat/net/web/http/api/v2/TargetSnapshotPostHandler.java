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
package io.cryostat.net.web.http.api.v2;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.jmc.serialization.HyperlinkedSerializableRecordingDescriptor;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.net.security.SecurityContext;
import io.cryostat.recordings.RecordingTargetHelper;
import io.cryostat.recordings.RecordingTargetHelper.SnapshotCreationException;

import com.google.gson.Gson;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.exception.ExceptionUtils;

class TargetSnapshotPostHandler
        extends AbstractV2RequestHandler<HyperlinkedSerializableRecordingDescriptor> {

    private final RecordingTargetHelper recordingTargetHelper;
    private final DiscoveryStorage discoveryStorage;

    @Inject
    TargetSnapshotPostHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            RecordingTargetHelper recordingTargetHelper,
            DiscoveryStorage discoveryStorage,
            Gson gson) {
        super(auth, credentialsManager, gson);
        this.recordingTargetHelper = recordingTargetHelper;
        this.discoveryStorage = discoveryStorage;
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
    public SecurityContext securityContext(RequestParameters params) {
        ConnectionDescriptor cd = getConnectionDescriptorFromParams(params);
        return discoveryStorage
                .lookupServiceByTargetId(cd.getTargetId())
                .map(SecurityContext::new)
                .orElse(null);
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
