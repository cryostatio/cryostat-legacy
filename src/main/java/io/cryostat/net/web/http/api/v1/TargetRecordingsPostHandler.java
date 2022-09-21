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

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
                                if (attrs.contains("duration")) {
                                    builder =
                                            builder.duration(
                                                    TimeUnit.SECONDS.toMillis(
                                                            Long.parseLong(attrs.get("duration"))));
                                }
                                if (attrs.contains("toDisk")) {
                                    Pattern bool = Pattern.compile("true|false");
                                    Matcher m = bool.matcher(attrs.get("toDisk"));
                                    if (!m.matches())
                                        throw new HttpException(400, "Invalid options");
                                    builder = builder.toDisk(Boolean.valueOf(attrs.get("toDisk")));
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

                                Pair<String, TemplateType> template =
                                        RecordingTargetHelper.parseEventSpecifierToTemplate(
                                                eventSpecifier);
                                IRecordingDescriptor descriptor =
                                        recordingTargetHelper.startRecording(
                                                connectionDescriptor,
                                                builder.build(),
                                                template.getLeft(),
                                                template.getRight(),
                                                metadata);

                                try {
                                    WebServer webServer = webServerProvider.get();
                                    return new HyperlinkedSerializableRecordingDescriptor(
                                            descriptor,
                                            webServer.getDownloadURL(
                                                    connection, descriptor.getName()),
                                            webServer.getReportURL(
                                                    connection, descriptor.getName()),
                                            recordingMetadataManager.getMetadata(
                                                    connectionDescriptor, recordingName));
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
