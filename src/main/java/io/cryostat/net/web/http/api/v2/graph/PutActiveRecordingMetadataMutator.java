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
package io.cryostat.net.web.http.api.v2.graph;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;

import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.net.Credentials;
import io.cryostat.jmc.serialization.HyperlinkedSerializableRecordingDescriptor;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.WebServer;
import io.cryostat.platform.ServiceRef;
import io.cryostat.recordings.RecordingMetadataManager;
import io.cryostat.recordings.RecordingMetadataManager.Metadata;
import io.cryostat.recordings.RecordingMetadataManager.SecurityContext;
import io.cryostat.recordings.RecordingTargetHelper;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import graphql.schema.DataFetchingEnvironment;

class PutActiveRecordingMetadataMutator
        extends AbstractPermissionedDataFetcher<HyperlinkedSerializableRecordingDescriptor> {

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
        super(auth, credentialsManager);
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
    SecurityContext securityContext(DataFetchingEnvironment environment) {
        GraphRecordingDescriptor source = environment.getSource();
        return source.getMetadata().getSecurityContext();
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

        Credentials credentials =
                getSessionCredentials(environment, uri.toString())
                        .orElse(credentialsManager.getCredentials(target));
        ConnectionDescriptor cd = new ConnectionDescriptor(uri, credentials);

        return targetConnectionManager.executeConnectedTask(
                cd,
                conn -> {
                    IRecordingDescriptor desc =
                            recordingTargetHelper.getDescriptorByName(conn, recordingName).get();

                    WebServer ws = webServer.get();

                    Metadata metadata =
                            metadataManager
                                    .setRecordingMetadata(cd, recordingName, labels, true)
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
