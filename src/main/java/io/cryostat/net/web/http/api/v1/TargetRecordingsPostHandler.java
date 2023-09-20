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

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.inject.Provider;

import org.openjdk.jmc.common.unit.QuantityConversionException;
import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.core.templates.TemplateType;
import io.cryostat.jmc.serialization.HyperlinkedSerializableRecordingDescriptor;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.recordings.RecordingMetadataManager;
import io.cryostat.recordings.RecordingMetadataManager.Metadata;
import io.cryostat.recordings.RecordingOptionsBuilderFactory;
import io.cryostat.recordings.RecordingTargetHelper;
import io.cryostat.recordings.RecordingTargetHelper.ReplacementPolicy;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

public class TargetRecordingsPostHandler extends AbstractAuthenticatedRequestHandler {

    static final String PATH = "targets/:targetId/recordings";
    private final TargetConnectionManager targetConnectionManager;
    private final RecordingTargetHelper recordingTargetHelper;
    private final RecordingOptionsBuilderFactory recordingOptionsBuilderFactory;
    private final Provider<WebServer> webServerProvider;
    private final RecordingMetadataManager recordingMetadataManager;
    private final Gson gson;

    @Inject
    TargetRecordingsPostHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            TargetConnectionManager targetConnectionManager,
            RecordingTargetHelper recordingTargetHelper,
            RecordingOptionsBuilderFactory recordingOptionsBuilderFactory,
            Provider<WebServer> webServerProvider,
            RecordingMetadataManager recordingMetadataManager,
            Gson gson,
            Logger logger) {
        super(auth, credentialsManager, logger);
        this.targetConnectionManager = targetConnectionManager;
        this.recordingTargetHelper = recordingTargetHelper;
        this.recordingOptionsBuilderFactory = recordingOptionsBuilderFactory;
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
        return HttpMethod.POST;
    }

    @Override
    public String path() {
        return basePath() + PATH;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(
                ResourceAction.READ_TARGET,
                ResourceAction.UPDATE_TARGET,
                ResourceAction.CREATE_RECORDING,
                ResourceAction.READ_RECORDING,
                ResourceAction.READ_TEMPLATE);
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
    public List<HttpMimeType> consumes() {
        return List.of(HttpMimeType.URLENCODED_FORM, HttpMimeType.MULTIPART_FORM);
    }

    @Override
    public void handleAuthenticated(RoutingContext ctx) throws Exception {
        MultiMap attrs = ctx.request().formAttributes();
        String recordingName = attrs.get("recordingName");
        if (StringUtils.isBlank(recordingName)) {
            throw new HttpException(400, "\"recordingName\" form parameter must be provided");
        }
        String eventSpecifier = attrs.get("events");
        if (StringUtils.isBlank(eventSpecifier)) {
            throw new HttpException(400, "\"events\" form parameter must be provided");
        }

        try {
            ConnectionDescriptor connectionDescriptor = getConnectionDescriptorFromContext(ctx);
            HyperlinkedSerializableRecordingDescriptor linkedDescriptor =
                    targetConnectionManager.executeConnectedTask(
                            connectionDescriptor,
                            connection -> {
                                RecordingOptionsBuilder builder =
                                        recordingOptionsBuilderFactory
                                                .create(connection.getService())
                                                .name(recordingName);

                                ReplacementPolicy replace = ReplacementPolicy.NEVER;

                                if (attrs.contains("restart")) {
                                    replace =
                                            Boolean.parseBoolean(attrs.get("restart"))
                                                    ? ReplacementPolicy.ALWAYS
                                                    : ReplacementPolicy.NEVER;
                                }

                                if (attrs.contains("replace")) {
                                    replace = ReplacementPolicy.fromString(attrs.get("replace"));
                                }

                                if (attrs.contains("duration")) {
                                    builder =
                                            builder.duration(
                                                    TimeUnit.SECONDS.toMillis(
                                                            Long.parseLong(attrs.get("duration"))));
                                }
                                if (attrs.contains("toDisk")) {
                                    if (attrs.get("toDisk").equals("true")
                                            || attrs.get("toDisk").equals("false")) {
                                        builder =
                                                builder.toDisk(
                                                        Boolean.valueOf(attrs.get("toDisk")));
                                    } else {
                                        throw new HttpException(400, "Invalid options");
                                    }
                                }
                                if (attrs.contains("maxAge")) {
                                    builder = builder.maxAge(Long.parseLong(attrs.get("maxAge")));
                                }
                                if (attrs.contains("maxSize")) {
                                    builder = builder.maxSize(Long.parseLong(attrs.get("maxSize")));
                                }
                                Metadata metadata = new Metadata();
                                if (attrs.contains("metadata")) {
                                    metadata =
                                            gson.fromJson(
                                                    attrs.get("metadata"),
                                                    new TypeToken<Metadata>() {}.getType());
                                }
                                boolean archiveOnStop = false;
                                if (attrs.contains("archiveOnStop")) {
                                    if (attrs.get("archiveOnStop").equals("true")
                                            || attrs.get("archiveOnStop").equals("false")) {
                                        archiveOnStop = Boolean.valueOf(attrs.get("archiveOnStop"));
                                    } else {
                                        throw new HttpException(400, "Invalid options");
                                    }
                                }

                                Pair<String, TemplateType> template =
                                        RecordingTargetHelper.parseEventSpecifierToTemplate(
                                                eventSpecifier);
                                IRecordingDescriptor descriptor =
                                        recordingTargetHelper.startRecording(
                                                replace,
                                                connectionDescriptor,
                                                builder.build(),
                                                template.getLeft(),
                                                template.getRight(),
                                                metadata,
                                                archiveOnStop);

                                try {
                                    WebServer webServer = webServerProvider.get();
                                    return new HyperlinkedSerializableRecordingDescriptor(
                                            descriptor,
                                            webServer.getDownloadURL(
                                                    connection, descriptor.getName()),
                                            webServer.getReportURL(
                                                    connection, descriptor.getName()),
                                            recordingMetadataManager.getMetadata(
                                                    connectionDescriptor, recordingName),
                                            archiveOnStop);
                                } catch (QuantityConversionException
                                        | URISyntaxException
                                        | IOException e) {
                                    throw new HttpException(500, e);
                                }
                            });

            ctx.response().setStatusCode(201);
            ctx.response().putHeader(HttpHeaders.LOCATION, "/" + recordingName);
            ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime());
            ctx.response().end(gson.toJson(linkedDescriptor));
        } catch (NumberFormatException | JsonSyntaxException ex) {
            throw new HttpException(
                    400, String.format("Invalid argument: %s", ex.getMessage()), ex);
        } catch (IllegalArgumentException iae) {
            throw new HttpException(400, iae.getMessage(), iae);
        }
    }

    protected Optional<IRecordingDescriptor> getDescriptorByName(
            JFRConnection connection, String recordingName) throws Exception {
        return connection.getService().getAvailableRecordings().stream()
                .filter(recording -> recording.getName().equals(recordingName))
                .findFirst();
    }
}
