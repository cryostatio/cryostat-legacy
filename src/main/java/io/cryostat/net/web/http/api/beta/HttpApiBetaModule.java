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
package io.cryostat.net.web.http.api.beta;

import io.cryostat.net.web.http.RequestHandler;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoSet;

@Module
public abstract class HttpApiBetaModule {
    @Binds
    @IntoSet
    abstract RequestHandler bindRecordingMetadataLabelsPostHandler(
            RecordingMetadataLabelsPostHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindTargetRecordingMetadataLabelsPostHandler(
            TargetRecordingMetadataLabelsPostHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindProbeTemplateGetHandler(ProbeTemplateGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindRecordingMetadataLabelsPostBodyHandler(
            RecordingMetadataLabelsPostBodyHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindTargetRecordingMetadataLabelsPostBodyHandler(
            TargetRecordingMetadataLabelsPostBodyHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindRecordingGethandler(RecordingGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindRecordingGetWithJwtHandler(RecordingGetWithJwtHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindRecordingDeleteHandler(RecordingDeleteHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindReportGetHandler(ReportGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindReportGetWithJwtHandler(ReportGetWithJwtHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindRecordingUploadPostHandler(RecordingUploadPostHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindArchivedDirectoriesGetHandler(
            ArchivedDirectoriesGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindRecordingDeleteFromPathHandler(
            RecordingDeleteFromPathHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindRecordingGetFromPathWithJwtHandler(
            RecordingGetFromPathWithJwtHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindReportGetFromPathHandler(ReportGetFromPathHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindReportGetFromPathWithJwtHandler(
            ReportGetFromPathWithJwtHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindRecordingUploadPostFromPathHandler(
            RecordingUploadPostFromPathHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindRecordingMetadataLabelsPostFromPathHandler(
            RecordingMetadataLabelsPostFromPathHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindRecordingMetadataLabelsPostFromPathBodyHandler(
            RecordingMetadataLabelsPostFromPathBodyHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindRecordingsFromIdPostHandler(RecordingsFromIdPostHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindRecordingsFromIdPostBodyHandler(
            RecordingsFromIdPostBodyHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindMatchExpressionGetHandler(MatchExpressionGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindMatchExpressionsGetHandler(MatchExpressionsGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindMatchExpressionsPostHandler(MatchExpressionsPostHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindMatchExpressionsPostBodyHandler(
            MatchExpressionsPostBodyHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindMatchExpressionDeleteHandler(MatchExpressionDeleteHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindCredentialTestPostHandler(CredentialTestPostHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindCredentialTestGetBodyHandler(CredentialTestPostBodyHandler handler);
}
