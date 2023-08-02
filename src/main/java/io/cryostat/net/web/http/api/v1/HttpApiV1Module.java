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
    abstract RequestHandler bindAuthPostHandler(AuthPostHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindNotificationsUrlGetHandler(NotificationsUrlGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindGrafanaDatasourceUrlGetHandler(
            GrafanaDatasourceUrlGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindGrafanaDashboardUrlGetHandler(
            GrafanaDashboardUrlGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindTargetRecordingGetHandler(TargetRecordingGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindTargetRecordingPatchHandler(TargetRecordingPatchHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindTargetRecordingDeleteHandler(TargetRecordingDeleteHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindTargetRecordingUploadPostHandler(
            TargetRecordingUploadPostHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindTargetRecordingPatchBodyHandler(
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
    abstract RequestHandler bindRecordingGetHandler(RecordingGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindRecordingDeleteHandler(RecordingDeleteHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindRecordingUploadPostHandler(RecordingUploadPostHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindTargetReportGetHandler(TargetReportGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindReportGetHandler(ReportGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindRecordingsGetHandler(RecordingsGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindRecordingsPostBodyHandler(RecordingsPostBodyHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindRecordingsPostHandler(RecordingsPostHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindTargetsGetHandler(TargetsGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindTargetRecordingsGetHandler(TargetRecordingsGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindTargetRecordingsPostBodyHandler(
            TargetRecordingsPostBodyHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindTargetRecordingsPostHandler(TargetRecordingsPostHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindTargetTemplatesGetHandler(TargetTemplatesGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindTargetTemplateGetHandler(TargetTemplateGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindTemplatesPostBodyHandler(TemplatesPostBodyHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindTemplatesPostHandler(TemplatesPostHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindTemplateDeleteHandler(TemplateDeleteHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindTargetEventsGetHandler(TargetEventsGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindTargetSnapshotPostHandler(TargetSnapshotPostHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindTargetRecordingOptionsPatchBodyHandler(
            TargetRecordingOptionsPatchBodyHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindTargetRecordingOptionsPatchHandler(
            TargetRecordingOptionsPatchHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindTargetRecordingOptionsGetHandler(
            TargetRecordingOptionsGetHandler handler);
}
