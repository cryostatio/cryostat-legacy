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
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.jmc.serialization.HyperlinkedSerializableRecordingDescriptor;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.SecurityContext;
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
    private final DiscoveryStorage discoveryStorage;
    private final Provider<WebServer> webServerProvider;
    private final RecordingMetadataManager recordingMetadataManager;
    private final Gson gson;

    @Inject
    TargetRecordingsGetHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            TargetConnectionManager connectionManager,
            DiscoveryStorage discoveryStorage,
            Provider<WebServer> webServerProvider,
            RecordingMetadataManager recordingMetadataManager,
            Gson gson,
            Logger logger) {
        super(auth, credentialsManager, logger);
        this.discoveryStorage = discoveryStorage;
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
    public SecurityContext securityContext(RoutingContext ctx) {
        ConnectionDescriptor cd = getConnectionDescriptorFromContext(ctx);
        return discoveryStorage
                .lookupServiceByTargetId(cd.getTargetId())
                .map(auth::contextFor)
                .orElse(null);
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
