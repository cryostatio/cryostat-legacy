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

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Provider;

import org.openjdk.jmc.common.unit.QuantityConversionException;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.api.v2.graph.RecordingsFetcher.Recordings;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.discovery.TargetNode;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingMetadataManager;
import io.cryostat.recordings.RecordingMetadataManager.Metadata;
import io.cryostat.rules.ArchivedRecordingInfo;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import graphql.schema.DataFetchingEnvironment;

class RecordingsFetcher extends AbstractPermissionedDataFetcher<Recordings> {

    private final TargetConnectionManager targetConnectionManager;
    private final RecordingArchiveHelper archiveHelper;
    private final CredentialsManager credentialsManager;
    private final RecordingMetadataManager metadataManager;
    private final Provider<WebServer> webServer;
    private final Logger logger;

    @Inject
    RecordingsFetcher(
            AuthManager auth,
            TargetConnectionManager targetConnectionManager,
            RecordingArchiveHelper archiveHelper,
            CredentialsManager credentialsManager,
            RecordingMetadataManager metadataManager,
            Provider<WebServer> webServer,
            Logger logger) {
        super(auth);
        this.targetConnectionManager = targetConnectionManager;
        this.archiveHelper = archiveHelper;
        this.credentialsManager = credentialsManager;
        this.metadataManager = metadataManager;
        this.webServer = webServer;
        this.logger = logger;
    }

    @Override
    Set<String> applicableContexts() {
        return Set.of("TargetNode");
    }

    @Override
    String name() {
        return "recordings";
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(
                ResourceAction.READ_TARGET,
                ResourceAction.READ_RECORDING,
                ResourceAction.READ_CREDENTIALS);
    }

    @Override
    @SuppressFBWarnings(
            value = "URF_UNREAD_FIELD",
            justification =
                    "The Recordings fields are serialized and returned to the client by the GraphQL"
                            + " engine")
    public Recordings getAuthenticated(DataFetchingEnvironment environment) throws Exception {
        TargetNode source = (TargetNode) environment.getSource();
        ServiceRef target = source.getTarget();
        String targetId = target.getServiceUri().toString();
        Recordings recordings = new Recordings();

        List<String> requestedFields =
                environment.getSelectionSet().getFields().stream()
                        .map(field -> field.getName())
                        .collect(Collectors.toList());

        if (requestedFields.contains("active")) {
            ConnectionDescriptor cd =
                    new ConnectionDescriptor(targetId, credentialsManager.getCredentials(target));
            // FIXME populating these two struct members are each async tasks. we should do them in
            // parallel
            recordings.active =
                    targetConnectionManager.executeConnectedTask(
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
                                                                        cd, r.getName());
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
                            });
        }

        if (requestedFields.contains("archived")) {
            try {
                recordings.archived = archiveHelper.getRecordings(targetId).get();
            } catch (ExecutionException e) {
                recordings.archived = List.of();
                logger.warn("Couldn't get archived recordings for {}", targetId);
                logger.warn(e);
            }
        }

        return recordings;
    }

    static class Recordings {
        List<GraphRecordingDescriptor> active;
        List<ArchivedRecordingInfo> archived;
    }
}
