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
package io.cryostat.net.web.http.api.v2.graph;

import java.util.EnumSet;
import java.util.Set;

import javax.inject.Inject;

import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.rules.ArchivedRecordingInfo;

import graphql.schema.DataFetchingEnvironment;

class DeleteArchivedRecordingMutator
        extends AbstractPermissionedDataFetcher<ArchivedRecordingInfo> {

    private final RecordingArchiveHelper recordingArchiveHelper;

    @Inject
    DeleteArchivedRecordingMutator(
            AuthManager auth, RecordingArchiveHelper recordingArchiveHelper) {
        super(auth);
        this.recordingArchiveHelper = recordingArchiveHelper;
    }

    @Override
    Set<String> applicableContexts() {
        return Set.of("ArchivedRecording");
    }

    @Override
    String name() {
        return "doDelete";
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(ResourceAction.DELETE_RECORDING);
    }

    @Override
    public ArchivedRecordingInfo getAuthenticated(DataFetchingEnvironment environment)
            throws Exception {
        ArchivedRecordingInfo source = environment.getSource();
        return recordingArchiveHelper
                .deleteRecording(source.getServiceUri(), source.getName())
                .get();
    }
}
