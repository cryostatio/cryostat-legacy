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
package io.cryostat.net.web.http.api.v1;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Provider;

import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.jmc.serialization.HyperlinkedSerializableRecordingDescriptor;
import io.cryostat.net.AuthManager;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.recordings.RecordingMetadataManager;

import com.google.gson.Gson;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;

class TargetRecordingsGetHandler extends AbstractAuthenticatedRequestHandler {

    private final TargetConnectionManager connectionManager;
    private final Provider<WebServer> webServerProvider;
    private final RecordingMetadataManager recordingMetadataManager;
    private final Gson gson;

    @Inject
    TargetRecordingsGetHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            TargetConnectionManager connectionManager,
            Provider<WebServer> webServerProvider,
            RecordingMetadataManager recordingMetadataManager,
            Gson gson,
            Logger logger) {
        super(auth, credentialsManager, logger);
        this.connectionManager = connectionManager;
        this.webServerProvider = webServerProvider;
        this.recordingMetadataManager = recordingMetadataManager;
        this.gson = gson;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.V1;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.GET;
    }

    @Override
    public String path() {
        return basePath() + "targets/:targetId/recordings";
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(ResourceAction.READ_TARGET, ResourceAction.READ_RECORDING);
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public List<HttpMimeType> produces() {
        return List.of(HttpMimeType.JSON);
    }

    @Override
    public void handleAuthenticated(RoutingContext ctx) throws Exception {
        WebServer webServer = webServerProvider.get();
        List<HyperlinkedSerializableRecordingDescriptor> descriptors =
                connectionManager.executeConnectedTask(
                        getConnectionDescriptorFromContext(ctx),
                        connection -> {
                            List<IRecordingDescriptor> origDescriptors =
                                    connection.getService().getAvailableRecordings();
                            List<HyperlinkedSerializableRecordingDescriptor> list =
                                    new ArrayList<>(origDescriptors.size());
                            for (IRecordingDescriptor desc : origDescriptors) {
                                list.add(
                                        new HyperlinkedSerializableRecordingDescriptor(
                                                desc,
                                                webServer.getDownloadURL(
                                                        connection, desc.getName()),
                                                webServer.getReportURL(connection, desc.getName()),
                                                recordingMetadataManager.getMetadata(
                                                        getConnectionDescriptorFromContext(ctx),
                                                        desc.getName())));
                            }
                            return list;
                        });
        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime());
        ctx.response().end(gson.toJson(descriptors));
    }
}
