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
import javax.inject.Provider;

import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.WebServer;
import io.cryostat.platform.ServiceRef;
import io.cryostat.recordings.RecordingMetadataManager;
import io.cryostat.recordings.RecordingMetadataManager.Metadata;
import io.cryostat.recordings.RecordingTargetHelper;

import graphql.schema.DataFetchingEnvironment;

class StopRecordingMutator extends AbstractPermissionedDataFetcher<GraphRecordingDescriptor> {

    private final TargetConnectionManager targetConnectionManager;
    private final RecordingTargetHelper recordingTargetHelper;
    private final CredentialsManager credentialsManager;
    private final RecordingMetadataManager metadataManager;
    private final Provider<WebServer> webServer;

    @Inject
    StopRecordingMutator(
            AuthManager auth,
            TargetConnectionManager targetConnectionManager,
            RecordingTargetHelper recordingTargetHelper,
            CredentialsManager credentialsManager,
            RecordingMetadataManager metadataManager,
            Provider<WebServer> webServer) {
        super(auth);
        this.targetConnectionManager = targetConnectionManager;
        this.recordingTargetHelper = recordingTargetHelper;
        this.credentialsManager = credentialsManager;
        this.metadataManager = metadataManager;
        this.webServer = webServer;
    }

    @Override
    Set<String> applicableContexts() {
        return Set.of("ActiveRecording");
    }

    @Override
    String name() {
        return "doStop";
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(
                ResourceAction.READ_RECORDING,
                ResourceAction.UPDATE_RECORDING,
                ResourceAction.READ_TARGET,
                ResourceAction.READ_CREDENTIALS);
    }

    @Override
    public GraphRecordingDescriptor getAuthenticated(DataFetchingEnvironment environment)
            throws Exception {
        GraphRecordingDescriptor source = environment.getSource();
        ServiceRef target = source.target;
        String uri = target.getServiceUri().toString();
        ConnectionDescriptor cd =
                new ConnectionDescriptor(uri, credentialsManager.getCredentials(target));

        return targetConnectionManager.executeConnectedTask(
                cd,
                conn -> {
                    IRecordingDescriptor desc =
                            recordingTargetHelper.stopRecording(cd, source.getName(), true);
                    WebServer ws = webServer.get();
                    Metadata metadata = metadataManager.getMetadata(cd, desc.getName());
                    return new GraphRecordingDescriptor(
                            target,
                            desc,
                            ws.getDownloadURL(conn, desc.getName()),
                            ws.getReportURL(conn, desc.getName()),
                            metadata);
                });
    }
}
