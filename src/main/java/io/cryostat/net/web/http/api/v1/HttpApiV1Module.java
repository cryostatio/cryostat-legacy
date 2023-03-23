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

import io.cryostat.net.web.http.RequestHandler;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingTargetHelper;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;

@Module
public abstract class HttpApiV1Module {

    @Binds
    @IntoSet
    abstract RequestHandler<?> bindAuthPostHandler(AuthPostHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler<?> bindNotificationsUrlGetHandler(NotificationsUrlGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler<?> bindGrafanaDatasourceUrlGetHandler(
            GrafanaDatasourceUrlGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler<?> bindGrafanaDashboardUrlGetHandler(
            GrafanaDashboardUrlGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler<?> bindTargetRecordingGetHandler(TargetRecordingGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler<?> bindTargetRecordingPatchHandler(TargetRecordingPatchHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler<?> bindTargetRecordingDeleteHandler(
            TargetRecordingDeleteHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler<?> bindTargetRecordingUploadPostHandler(
            TargetRecordingUploadPostHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler<?> bindTargetRecordingPatchBodyHandler(
            TargetRecordingPatchBodyHandler handler);

    @Provides
    static TargetRecordingPatchSave provideTargetRecordingPatchSave(
            RecordingArchiveHelper recordingArchiveHelper) {
        return new TargetRecordingPatchSave(recordingArchiveHelper);
    }

    @Provides
    static TargetRecordingPatchStop provideTargetRecordingPatchStop(
            RecordingTargetHelper recordingTargetHelper) {
        return new TargetRecordingPatchStop(recordingTargetHelper);
    }

    @Binds
    @IntoSet
    abstract RequestHandler<?> bindRecordingGetHandler(RecordingGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler<?> bindRecordingDeleteHandler(RecordingDeleteHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler<?> bindRecordingUploadPostHandler(RecordingUploadPostHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler<?> bindTargetReportGetHandler(TargetReportGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler<?> bindReportGetHandler(ReportGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler<?> bindRecordingsGetHandler(RecordingsGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler<?> bindRecordingsPostBodyHandler(RecordingsPostBodyHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler<?> bindRecordingsPostHandler(RecordingsPostHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler<?> bindTargetsGetHandler(TargetsGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler<?> bindTargetRecordingsGetHandler(TargetRecordingsGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler<?> bindTargetRecordingsPostBodyHandler(
            TargetRecordingsPostBodyHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler<?> bindTargetRecordingsPostHandler(TargetRecordingsPostHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler<?> bindTargetTemplatesGetHandler(TargetTemplatesGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler<?> bindTargetTemplateGetHandler(TargetTemplateGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler<?> bindTemplatesPostBodyHandler(TemplatesPostBodyHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler<?> bindTemplatesPostHandler(TemplatesPostHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler<?> bindTemplateDeleteHandler(TemplateDeleteHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler<?> bindTargetEventsGetHandler(TargetEventsGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler<?> bindTargetSnapshotPostHandler(TargetSnapshotPostHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler<?> bindTargetRecordingOptionsPatchBodyHandler(
            TargetRecordingOptionsPatchBodyHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler<?> bindTargetRecordingOptionsPatchHandler(
            TargetRecordingOptionsPatchHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler<?> bindTargetRecordingOptionsGetHandler(
            TargetRecordingOptionsGetHandler handler);
}
