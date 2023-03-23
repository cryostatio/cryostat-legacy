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
package io.cryostat.net.web.http.api.v1;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.jmc.serialization.HyperlinkedSerializableRecordingDescriptor;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.SecurityContext;
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
    private final DiscoveryStorage discoveryStorage;

    @Inject
    TargetSnapshotPostHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            DiscoveryStorage discoveryStorage,
            RecordingTargetHelper recordingTargetHelper,
            Logger logger) {
        super(auth, credentialsManager, logger);
        this.recordingTargetHelper = recordingTargetHelper;
        this.discoveryStorage = discoveryStorage;
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
    public SecurityContext securityContext(RoutingContext ctx) {
        ConnectionDescriptor cd = getConnectionDescriptorFromContext(ctx);
        return discoveryStorage
                .lookupServiceByTargetId(cd.getTargetId())
                .map(auth::contextFor)
                .orElseThrow(() -> new HttpException(404));
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
