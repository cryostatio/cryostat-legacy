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
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.openjdk.jmc.common.unit.QuantityConversionException;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.jmc.serialization.HyperlinkedSerializableRecordingDescriptor;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.recordings.RecordingOptionsBuilderFactory;
import io.cryostat.recordings.RecordingTargetHelper;
import io.cryostat.recordings.RecordingTargetHelper.SnapshotCreationException;
import io.cryostat.recordings.RecordingTargetHelper.SnapshotMinimalDescriptor;

import com.google.gson.Gson;
import dagger.Lazy;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;

class TargetSnapshotPostHandler
        extends AbstractV2RequestHandler<HyperlinkedSerializableRecordingDescriptor> {

    private final TargetConnectionManager targetConnectionManager;
    private final Lazy<WebServer> webServer;
    private final RecordingTargetHelper recordingTargetHelper;

    @Inject
    TargetSnapshotPostHandler(
            AuthManager auth,
            TargetConnectionManager targetConnectionManager,
            Lazy<WebServer> webServer,
            RecordingTargetHelper recordingTargetHelper,
            Gson gson) {
        super(auth, gson);
        this.targetConnectionManager = targetConnectionManager;
        this.webServer = webServer;
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
    public HttpMimeType mimeType() {
        return HttpMimeType.PLAINTEXT;
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
        HyperlinkedSerializableRecordingDescriptor desc =
                targetConnectionManager.executeConnectedTask(
                        connectionDescriptor,
                        connection -> {
                            SnapshotMinimalDescriptor snapshot =
                                    recordingTargetHelper.createSnapshot(connection).get();
                            String rename = snapshot.getName();
                            return new SnapshotDescriptor(
                                    rename,
                                    snapshot.getOriginalDescriptor(),
                                    webServer.get().getDownloadURL(connection, rename),
                                    webServer.get().getReportURL(connection, rename));
                        });
        String snapshotName = desc.getName();

        boolean verificationSuccessful = false;
        try {
            verificationSuccessful =
                    recordingTargetHelper.verifySnapshot(connectionDescriptor, snapshotName).get();
        } catch (ExecutionException e) {
            if (ExceptionUtils.getRootCause(e) instanceof SnapshotCreationException) {
                throw new ApiException(
                    500,
                    String.format(
                            "An error occured during the creation of snapshot %s",
                            snapshotName));
            }
            throw e;
        }

        if (!verificationSuccessful) {
            return new IntermediateResponse<HyperlinkedSerializableRecordingDescriptor>()
                    .statusCode(202)
                    .statusMessage(
                            "Snapshot failed to create: The resultant recording was unreadable for some reason, possibly due to a lack of Active, non-Snapshot source recordings to take event data from")
                    .body(null);
        }

        return new IntermediateResponse<HyperlinkedSerializableRecordingDescriptor>()
                .statusCode(201)
                .addHeader(HttpHeaders.LOCATION, desc.getDownloadUrl())
                .body(desc);
    }

    static class SnapshotDescriptor extends HyperlinkedSerializableRecordingDescriptor {
        SnapshotDescriptor(
                String name, IRecordingDescriptor orig, String downloadUrl, String reportUrl)
                throws QuantityConversionException {
            super(orig, downloadUrl, reportUrl);
            this.name = name;
        }
    }
}
