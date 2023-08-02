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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;

import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.jmc.serialization.HyperlinkedSerializableRecordingDescriptor;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.WebServer;
import io.cryostat.platform.ServiceRef;
import io.cryostat.recordings.RecordingMetadataManager;
import io.cryostat.recordings.RecordingMetadataManager.Metadata;
import io.cryostat.recordings.RecordingTargetHelper;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import graphql.schema.DataFetchingEnvironment;

class PutActiveRecordingMetadataMutator
        extends AbstractPermissionedDataFetcher<HyperlinkedSerializableRecordingDescriptor> {

    private final CredentialsManager credentialsManager;
    private final TargetConnectionManager targetConnectionManager;
    private final RecordingMetadataManager metadataManager;
    private final RecordingTargetHelper recordingTargetHelper;
    private final Provider<WebServer> webServer;
    private final Gson gson;

    @Inject
    PutActiveRecordingMetadataMutator(
            AuthManager auth,
            CredentialsManager credentialsManager,
            TargetConnectionManager targetConnectionManager,
            RecordingTargetHelper recordingTargetHelper,
            RecordingMetadataManager metadataManager,
            Provider<WebServer> webServer,
            Gson gson) {
        super(auth);
        this.credentialsManager = credentialsManager;
        this.targetConnectionManager = targetConnectionManager;
        this.recordingTargetHelper = recordingTargetHelper;
        this.metadataManager = metadataManager;
        this.webServer = webServer;
        this.gson = gson;
    }

    @Override
    Set<String> applicableContexts() {
        return Set.of("ActiveRecording");
    }

    @Override
    String name() {
        return "doPutMetadata";
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return Set.of(
                ResourceAction.READ_RECORDING,
                ResourceAction.UPDATE_RECORDING,
                ResourceAction.READ_TARGET);
    }

    @Override
    public HyperlinkedSerializableRecordingDescriptor getAuthenticated(
            DataFetchingEnvironment environment) throws Exception {
        GraphRecordingDescriptor source = environment.getSource();
        ServiceRef target = source.target;
        String uri = target.getServiceUri().toString();
        String recordingName = source.getName();
        Map<String, Object> settings = environment.getArgument("metadata");
        Map<String, String> labels = new HashMap<>();

        if (settings.containsKey("labels")) {
            List<InputRecordingLabel> inputLabels =
                    gson.fromJson(
                            settings.get("labels").toString(),
                            new TypeToken<List<InputRecordingLabel>>() {}.getType());
            for (InputRecordingLabel l : inputLabels) {
                labels.put(l.getKey(), l.getValue());
            }
        }

        ConnectionDescriptor cd =
                new ConnectionDescriptor(uri, credentialsManager.getCredentials(target));

        return targetConnectionManager.executeConnectedTask(
                cd,
                conn -> {
                    IRecordingDescriptor desc =
                            recordingTargetHelper.getDescriptorByName(conn, recordingName).get();

                    WebServer ws = webServer.get();

                    Metadata metadata =
                            metadataManager
                                    .setRecordingMetadata(
                                            cd, recordingName, new Metadata(labels), true)
                                    .get();

                    return new HyperlinkedSerializableRecordingDescriptor(
                            desc,
                            ws.getDownloadURL(conn, desc.getName()),
                            ws.getReportURL(conn, desc.getName()),
                            metadata);
                });
    }

    public static class InputRecordingLabel {
        private String key;
        private String value;

        public InputRecordingLabel(String key, String value) {
            this.key = key;
            this.value = value;
        }

        public String getKey() {
            return this.key;
        }

        public String getValue() {
            return this.value;
        }
    }
}
