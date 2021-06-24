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
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Provider;

import org.openjdk.jmc.common.unit.QuantityConversionException;
import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.commands.internal.RecordingOptionsBuilderFactory;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.core.templates.TemplateType;
import io.cryostat.jmc.serialization.HyperlinkedSerializableRecordingDescriptor;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.recordings.RecordingCreationHelper;

import com.google.gson.Gson;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

public class TargetRecordingsPostHandler extends AbstractAuthenticatedRequestHandler {

    static final String PATH = "targets/:targetId/recordings";
    private final TargetConnectionManager targetConnectionManager;
    private final RecordingCreationHelper recordingCreationHelper;
    private final RecordingOptionsBuilderFactory recordingOptionsBuilderFactory;
    private final Provider<WebServer> webServerProvider;
    private final Gson gson;

    @Inject
    TargetRecordingsPostHandler(
            AuthManager auth,
            TargetConnectionManager targetConnectionManager,
            RecordingCreationHelper recordingCreationHelper,
            RecordingOptionsBuilderFactory recordingOptionsBuilderFactory,
            Provider<WebServer> webServerProvider,
            Gson gson) {
        super(auth);
        this.targetConnectionManager = targetConnectionManager;
        this.recordingCreationHelper = recordingCreationHelper;
        this.recordingOptionsBuilderFactory = recordingOptionsBuilderFactory;
        this.webServerProvider = webServerProvider;
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
    public boolean isAsync() {
        return false;
    }

    @Override
    public void handleAuthenticated(RoutingContext ctx) throws Exception {
        MultiMap attrs = ctx.request().formAttributes();
        String recordingName = attrs.get("recordingName");
        if (StringUtils.isBlank(recordingName)) {
            throw new HttpStatusException(400, "\"recordingName\" form parameter must be provided");
        }
        String eventSpecifier = attrs.get("events");
        if (StringUtils.isBlank(eventSpecifier)) {
            throw new HttpStatusException(400, "\"events\" form parameter must be provided");
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
                                        throw new HttpStatusException(400, "Invalid options");
                                    builder = builder.toDisk(Boolean.valueOf(attrs.get("toDisk")));
                                }
                                if (attrs.contains("maxAge")) {
                                    builder = builder.maxAge(Long.parseLong(attrs.get("maxAge")));
                                }
                                if (attrs.contains("maxSize")) {
                                    builder = builder.maxSize(Long.parseLong(attrs.get("maxSize")));
                                }
                                Pair<String, TemplateType> template =
                                        RecordingCreationHelper.parseEventSpecifierToTemplate(
                                                eventSpecifier);
                                IRecordingDescriptor descriptor =
                                        recordingCreationHelper.startRecording(
                                                connectionDescriptor,
                                                builder.build(),
                                                template.getLeft(),
                                                template.getRight());
                                try {
                                    WebServer webServer = webServerProvider.get();
                                    return new HyperlinkedSerializableRecordingDescriptor(
                                            descriptor,
                                            webServer.getDownloadURL(
                                                    connection, descriptor.getName()),
                                            webServer.getReportURL(
                                                    connection, descriptor.getName()));
                                } catch (QuantityConversionException
                                        | URISyntaxException
                                        | IOException e) {
                                    throw new HttpStatusException(500, e);
                                }
                            });

            ctx.response().setStatusCode(201);
            ctx.response().putHeader(HttpHeaders.LOCATION, "/" + recordingName);
            ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime());
            ctx.response().end(gson.toJson(linkedDescriptor));
        } catch (NumberFormatException nfe) {
            throw new HttpStatusException(
                    400, String.format("Invalid argument: %s", nfe.getMessage()), nfe);
        } catch (IllegalArgumentException iae) {
            throw new HttpStatusException(400, iae.getMessage(), iae);
        }
    }

    protected Optional<IRecordingDescriptor> getDescriptorByName(
            JFRConnection connection, String recordingName) throws Exception {
        return connection.getService().getAvailableRecordings().stream()
                .filter(recording -> recording.getName().equals(recordingName))
                .findFirst();
    }
}
