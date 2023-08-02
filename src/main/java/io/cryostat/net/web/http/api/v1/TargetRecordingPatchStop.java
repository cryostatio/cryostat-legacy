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

import javax.inject.Inject;

import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.recordings.RecordingNotFoundException;
import io.cryostat.recordings.RecordingTargetHelper;

import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;

class TargetRecordingPatchStop {

    private final RecordingTargetHelper recordingTargetHelper;

    @Inject
    TargetRecordingPatchStop(RecordingTargetHelper recordingTargetHelper) {
        this.recordingTargetHelper = recordingTargetHelper;
    }

    void handle(RoutingContext ctx, ConnectionDescriptor connectionDescriptor) throws Exception {
        String recordingName = ctx.pathParam("recordingName");

        try {
            recordingTargetHelper.stopRecording(connectionDescriptor, recordingName);
        } catch (RecordingNotFoundException rnfe) {
            throw new HttpException(404, rnfe);
        }
        ctx.response().setStatusCode(200);
        ctx.response().end();
    }
}
