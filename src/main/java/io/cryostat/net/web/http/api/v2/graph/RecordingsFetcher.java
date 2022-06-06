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

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Provider;

import org.openjdk.jmc.common.unit.QuantityConversionException;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.api.v2.graph.RecordingsFetcher.Recordings;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.discovery.TargetNode;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingMetadataManager;
import io.cryostat.recordings.RecordingMetadataManager.Metadata;
import io.cryostat.rules.ArchivedRecordingInfo;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

class RecordingsFetcher implements DataFetcher<Recordings> {

    private final TargetConnectionManager tcm;
    private final RecordingArchiveHelper archiveHelper;
    private final CredentialsManager credentialsManager;
    private final RecordingMetadataManager metadataManager;
    private final Provider<WebServer> webServer;
    private final Logger logger;

    @Inject
    RecordingsFetcher(
            TargetConnectionManager tcm,
            RecordingArchiveHelper archiveHelper,
            CredentialsManager credentialsManager,
            RecordingMetadataManager metadataManager,
            Provider<WebServer> webServer,
            Logger logger) {
        this.tcm = tcm;
        this.archiveHelper = archiveHelper;
        this.credentialsManager = credentialsManager;
        this.metadataManager = metadataManager;
        this.webServer = webServer;
        this.logger = logger;
    }

    @Override
    @SuppressFBWarnings(
            value = "URF_UNREAD_FIELD",
            justification =
                    "The Recordings fields are serialized and returned to the client by the GraphQL engine")
    public Recordings get(DataFetchingEnvironment environment) throws Exception {
        TargetNode source = (TargetNode) environment.getSource();
        ServiceRef target = source.getTarget();
        String targetId = target.getServiceUri().toString();
        Recordings recordings = new Recordings();

        ConnectionDescriptor cd =
                new ConnectionDescriptor(targetId, credentialsManager.getCredentials(target));
        // FIXME populating these two struct members are each async tasks. we should do them in
        // parallel
        recordings.archived = archiveHelper.getRecordings(targetId).get();
        recordings.active =
                tcm.executeConnectedTask(
                        cd,
                        conn -> {
                            return conn.getService().getAvailableRecordings().stream()
                                    .map(
                                            r -> {
                                                try {
                                                    String downloadUrl =
                                                            webServer
                                                                    .get()
                                                                    .getDownloadURL(
                                                                            conn, r.getName());
                                                    String reportUrl =
                                                            webServer
                                                                    .get()
                                                                    .getReportURL(
                                                                            conn, r.getName());
                                                    Metadata metadata =
                                                            metadataManager.getMetadata(
                                                                    targetId, r.getName());
                                                    return new GraphRecordingDescriptor(
                                                            target,
                                                            r,
                                                            downloadUrl,
                                                            reportUrl,
                                                            metadata);
                                                } catch (QuantityConversionException
                                                        | URISyntaxException
                                                        | IOException e) {
                                                    logger.error(e);
                                                    return null;
                                                }
                                            })
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.toList());
                        },
                        false);

        return recordings;
    }

    static class Recordings {
        List<GraphRecordingDescriptor> active;
        List<ArchivedRecordingInfo> archived;
    }
}
