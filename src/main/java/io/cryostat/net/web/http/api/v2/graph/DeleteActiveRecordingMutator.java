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

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.platform.ServiceRef;
import io.cryostat.recordings.RecordingTargetHelper;

import graphql.schema.DataFetchingEnvironment;

class DeleteActiveRecordingMutator
        extends AbstractPermissionedDataFetcher<GraphRecordingDescriptor> {

    private final RecordingTargetHelper recordingTargetHelper;
    private final CredentialsManager credentialsManager;

    @Inject
    DeleteActiveRecordingMutator(
            AuthManager auth,
            RecordingTargetHelper recordingTargetHelper,
            CredentialsManager credentialsManager) {
        super(auth);
        this.recordingTargetHelper = recordingTargetHelper;
        this.credentialsManager = credentialsManager;
    }

    @Override
    Set<String> applicableContexts() {
        return Set.of("ActiveRecording");
    }

    @Override
    String name() {
        return "doDelete";
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(
                ResourceAction.DELETE_RECORDING,
                ResourceAction.READ_TARGET,
                ResourceAction.UPDATE_TARGET,
                ResourceAction.DELETE_CREDENTIALS);
    }

    @Override
    public GraphRecordingDescriptor getAuthenticated(DataFetchingEnvironment environment)
            throws Exception {
        GraphRecordingDescriptor source = environment.getSource();
        ServiceRef target = source.target;
        String uri = target.getServiceUri().toString();
        ConnectionDescriptor cd =
                new ConnectionDescriptor(uri, credentialsManager.getCredentials(target));

        recordingTargetHelper.deleteRecording(cd, source.getName()).get();
        return source;
    }
}
