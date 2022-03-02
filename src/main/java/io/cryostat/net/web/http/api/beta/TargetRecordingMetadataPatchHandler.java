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

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;

import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.recordings.RecordingMetadataManager;
import io.cryostat.recordings.RecordingNotFoundException;
import io.cryostat.recordings.RecordingTargetHelper;

import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public class TargetRecordingMetadataPatchHandler extends AbstractAuthenticatedRequestHandler {

    static final String PATH = "targets/:targetId/recordings/:recordingName/metadata";

    private final TargetConnectionManager targetConnectionManager;
    private final RecordingTargetHelper recordingTargetHelper;
    private final RecordingMetadataManager recordingMetadataManager;
    private final NotificationFactory notificationFactory;

    @Inject
    TargetRecordingMetadataPatchHandler(
            AuthManager auth,
            TargetConnectionManager targetConnectionManager,
            RecordingTargetHelper recordingTargetHelper,
            RecordingMetadataManager recordingMetadataManager,
            NotificationFactory notificationFactory) {
        super(auth);
        this.targetConnectionManager = targetConnectionManager;
        this.recordingTargetHelper = recordingTargetHelper;
        this.recordingMetadataManager = recordingMetadataManager;
        this.notificationFactory = notificationFactory;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.V2_1;
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
    public void handleAuthenticated(RoutingContext ctx) throws Exception {
        String recordingName = ctx.pathParam("recordingName");
        String targetId = ctx.pathParam("targetId");
        String labels = ctx.getBodyAsString();

        if (labels == null) {
            throw new HttpStatusException(400, "\"labels\" body data must be provided");
        }

        try {
            this.confirmTargetRecordingFound(
                    getConnectionDescriptorFromContext(ctx), recordingName);
        } catch (RecordingNotFoundException e) {
            throw new HttpStatusException(404, e);
        }

        String updatedLabels =
                recordingMetadataManager.addRecordingLabels(targetId, recordingName, labels).get();

        notificationFactory
                .createBuilder()
                .metaCategory(RecordingMetadataManager.NOTIFICATION_CATEGORY)
                .metaType(HttpMimeType.JSON)
                .message(
                        Map.of(
                                "recordingName",
                                recordingName,
                                "target",
                                targetId,
                                "labels",
                                labels))
                .build()
                .send();
        ctx.response().setStatusCode(200);
        ctx.response().end(updatedLabels);
    }

    private boolean confirmTargetRecordingFound(
            ConnectionDescriptor connectionDescriptor, String recordingName) throws Exception {
        return targetConnectionManager.executeConnectedTask(
                connectionDescriptor,
                connection -> {
                    Optional<IRecordingDescriptor> descriptor =
                            recordingTargetHelper.getDescriptorByName(connection, recordingName);
                    return descriptor.isPresent();
                },
                false);
    }
}
