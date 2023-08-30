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

import java.util.concurrent.ExecutionException;

import javax.inject.Inject;

import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.recordings.EmptyRecordingException;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingNotFoundException;

import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;
import org.apache.commons.lang3.exception.ExceptionUtils;

class TargetRecordingPatchSave {

    private final RecordingArchiveHelper recordingArchiveHelper;

    @Inject
    TargetRecordingPatchSave(RecordingArchiveHelper recordingArchiveHelper) {
        this.recordingArchiveHelper = recordingArchiveHelper;
    }

    void handle(RoutingContext ctx, ConnectionDescriptor connectionDescriptor) throws Exception {
        String recordingName = ctx.pathParam("recordingName");

        try {
            String saveName =
                    recordingArchiveHelper
                            .saveRecording(connectionDescriptor, recordingName)
                            .get()
                            .getName();
            ctx.response().end(saveName);
        } catch (ExecutionException e) {
            if (ExceptionUtils.getRootCause(e) instanceof RecordingNotFoundException) {
                throw new HttpException(404, e.getMessage(), e);
            } else if (e.getCause() instanceof EmptyRecordingException) {
                ctx.response().setStatusCode(204);
                ctx.response().end();
            }
            throw e;
        }
    }
}
