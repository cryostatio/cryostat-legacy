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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Provider;

import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.templates.TemplateType;
import io.cryostat.jmc.serialization.HyperlinkedSerializableRecordingDescriptor;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.api.v2.graph.PutActiveRecordingMetadataMutator.InputRecordingLabel;
import io.cryostat.platform.discovery.TargetNode;
import io.cryostat.recordings.RecordingMetadataManager;
import io.cryostat.recordings.RecordingMetadataManager.Metadata;
import io.cryostat.recordings.RecordingOptionsBuilderFactory;
import io.cryostat.recordings.RecordingTargetHelper;
import io.cryostat.recordings.RecordingTargetHelper.ReplacementPolicy;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import graphql.schema.DataFetchingEnvironment;

class StartRecordingOnTargetMutator
        extends AbstractPermissionedDataFetcher<HyperlinkedSerializableRecordingDescriptor> {

    private final TargetConnectionManager targetConnectionManager;
    private final RecordingTargetHelper recordingTargetHelper;
    private final RecordingOptionsBuilderFactory recordingOptionsBuilderFactory;
    private final CredentialsManager credentialsManager;
    private final RecordingMetadataManager metadataManager;
    private final Provider<WebServer> webServer;
    private final Gson gson;

    @Inject
    StartRecordingOnTargetMutator(
            AuthManager auth,
            TargetConnectionManager targetConnectionManager,
            RecordingTargetHelper recordingTargetHelper,
            RecordingOptionsBuilderFactory recordingOptionsBuilderFactory,
            CredentialsManager credentialsManager,
            RecordingMetadataManager metadataManager,
            Provider<WebServer> webServer,
            Gson gson) {
        super(auth);
        this.targetConnectionManager = targetConnectionManager;
        this.recordingTargetHelper = recordingTargetHelper;
        this.recordingOptionsBuilderFactory = recordingOptionsBuilderFactory;
        this.credentialsManager = credentialsManager;
        this.metadataManager = metadataManager;
        this.webServer = webServer;
        this.gson = gson;
    }

    @Override
    Set<String> applicableContexts() {
        return Set.of("TargetNode");
    }

    @Override
    String name() {
        return "doStartRecording";
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(
                ResourceAction.READ_RECORDING,
                ResourceAction.CREATE_RECORDING,
                ResourceAction.READ_TARGET,
                ResourceAction.UPDATE_TARGET,
                ResourceAction.READ_CREDENTIALS);
    }

    @Override
    public HyperlinkedSerializableRecordingDescriptor getAuthenticated(
            DataFetchingEnvironment environment) throws Exception {
        TargetNode node = environment.getSource();
        Map<String, Object> settings = environment.getArgument("recording");

        String uri = node.getTarget().getServiceUri().toString();
        ConnectionDescriptor cd =
                new ConnectionDescriptor(uri, credentialsManager.getCredentials(node.getTarget()));
        return targetConnectionManager.executeConnectedTask(
                cd,
                conn -> {
                    RecordingOptionsBuilder builder =
                            recordingOptionsBuilderFactory
                                    .create(conn.getService())
                                    .name((String) settings.get("name"));

                    ReplacementPolicy replace = ReplacementPolicy.NEVER;
                    if (settings.containsKey("restart")) {
                        replace =
                                Boolean.TRUE.equals(settings.get("restart"))
                                        ? ReplacementPolicy.ALWAYS
                                        : ReplacementPolicy.NEVER;
                    }
                    if (settings.containsKey("replace")) {
                        replace = ReplacementPolicy.fromString((String) settings.get("replace"));
                    }
                    if (settings.containsKey("duration")) {
                        builder =
                                builder.duration(
                                        TimeUnit.SECONDS.toMillis((Long) settings.get("duration")));
                    }
                    if (settings.containsKey("toDisk")) {
                        builder = builder.toDisk((Boolean) settings.get("toDisk"));
                    }
                    if (settings.containsKey("maxAge")) {
                        builder = builder.maxAge((Long) settings.get("maxAge"));
                    }
                    if (settings.containsKey("maxSize")) {
                        builder = builder.maxSize((Long) settings.get("maxSize"));
                    }
                    boolean archiveOnStop = false;
                    if (settings.containsKey("archiveOnStop")) {
                        Boolean v = (Boolean) settings.get("archiveOnStop");
                        archiveOnStop = v != null && v;
                    }
                    Metadata m = new Metadata();
                    if (settings.containsKey("metadata")) {
                        Map<String, Object> _metadata =
                                gson.fromJson(
                                        settings.get("metadata").toString(),
                                        new TypeToken<Map<String, Object>>() {}.getType());

                        Map<String, String> labels = new HashMap<>();
                        List<InputRecordingLabel> inputLabels =
                                gson.fromJson(
                                        _metadata.get("labels").toString(),
                                        new TypeToken<List<InputRecordingLabel>>() {}.getType());

                        for (InputRecordingLabel l : inputLabels) {
                            labels.put(l.getKey(), l.getValue());
                        }
                        m = new Metadata(labels);
                    }
                    IRecordingDescriptor desc =
                            recordingTargetHelper.startRecording(
                                    replace,
                                    cd,
                                    builder.build(),
                                    (String) settings.get("template"),
                                    TemplateType.valueOf(
                                            ((String) settings.get("templateType")).toUpperCase()),
                                    m,
                                    archiveOnStop);
                    WebServer ws = webServer.get();
                    Metadata metadata = metadataManager.getMetadata(cd, desc.getName());
                    return new HyperlinkedSerializableRecordingDescriptor(
                            desc,
                            ws.getDownloadURL(conn, desc.getName()),
                            ws.getReportURL(conn, desc.getName()),
                            metadata,
                            archiveOnStop);
                });
    }
}
