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

import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.api.v2.graph.PutActiveRecordingMetadataMutator.InputRecordingLabel;
import io.cryostat.recordings.RecordingMetadataManager;
import io.cryostat.recordings.RecordingMetadataManager.Metadata;
import io.cryostat.rules.ArchivedRecordingInfo;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import graphql.schema.DataFetchingEnvironment;
import org.apache.commons.codec.binary.Base32;

class PutArchivedRecordingMetadataMutator
        extends AbstractPermissionedDataFetcher<ArchivedRecordingInfo> {

    private final RecordingMetadataManager metadataManager;
    private final Provider<WebServer> webServer;
    private final Gson gson;
    private final Base32 base32;

    @Inject
    PutArchivedRecordingMetadataMutator(
            AuthManager auth,
            RecordingMetadataManager metadataManager,
            Provider<WebServer> webServer,
            Gson gson,
            Base32 base32) {
        super(auth);
        this.metadataManager = metadataManager;
        this.webServer = webServer;
        this.gson = gson;
        this.base32 = base32;
    }

    @Override
    Set<String> applicableContexts() {
        return Set.of("ArchivedRecording");
    }

    @Override
    String name() {
        return "doPutMetadata";
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return Set.of(ResourceAction.READ_RECORDING, ResourceAction.UPDATE_RECORDING);
    }

    @Override
    public ArchivedRecordingInfo getAuthenticated(DataFetchingEnvironment environment)
            throws Exception {
        ArchivedRecordingInfo source = environment.getSource();
        String uri = source.getServiceUri();
        String recordingName = source.getName();
        long size = source.getSize();
        long archivedTime = source.getArchivedTime();
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
        Metadata metadata =
                metadataManager
                        .setRecordingMetadata(
                                new ConnectionDescriptor(uri),
                                recordingName,
                                new Metadata(labels),
                                true)
                        .get();

        WebServer ws = webServer.get();

        return new ArchivedRecordingInfo(
                uri,
                recordingName,
                ws.getArchivedDownloadURL(uri, recordingName),
                ws.getArchivedReportURL(uri, recordingName),
                metadata,
                size,
                archivedTime);
    }
}
