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

import java.util.concurrent.ExecutionException;

import javax.inject.Inject;

import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.recordings.EmptyRecordingException;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingNotFoundException;

import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.impl.HttpStatusException;
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
                throw new HttpStatusException(404, e.getMessage(), e);
            } else if (e.getCause() instanceof EmptyRecordingException) {
                ctx.response().setStatusCode(204);
                ctx.response().end();
            }
            throw e;
        }
    }
}
