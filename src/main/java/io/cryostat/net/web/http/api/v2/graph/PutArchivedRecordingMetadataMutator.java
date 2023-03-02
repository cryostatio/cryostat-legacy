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

import io.cryostat.configuration.CredentialsManager;
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
    private final CredentialsManager credentialsManager;
    private final Provider<WebServer> webServer;
    private final Gson gson;
    private final Base32 base32;

    @Inject
    PutArchivedRecordingMetadataMutator(
            AuthManager auth,
            CredentialsManager credentialsManager,
            RecordingMetadataManager metadataManager,
            Provider<WebServer> webServer,
            Gson gson,
            Base32 base32) {
        super(auth);
        this.credentialsManager = credentialsManager;
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
                                new ConnectionDescriptor(
                                        uri, credentialsManager.getCredentialsByTargetId(uri)),
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
