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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Provider;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.common.unit.QuantityConversionException;
import org.openjdk.jmc.flightrecorder.configuration.events.EventOptionID;
import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;
import org.openjdk.jmc.rjmx.services.jfr.IEventTypeInfo;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.commands.internal.EventOptionsBuilder;
import io.cryostat.commands.internal.RecordingOptionsBuilderFactory;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.core.templates.Template;
import io.cryostat.core.templates.TemplateType;
import io.cryostat.jmc.serialization.HyperlinkedSerializableRecordingDescriptor;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;

import com.google.gson.Gson;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import org.apache.commons.lang3.StringUtils;

public class TargetRecordingsPostHandler extends AbstractAuthenticatedRequestHandler {

    // TODO refactor this to use the RecordingCreationHelper after PR #486 is merged
    public static final Template ALL_EVENTS_TEMPLATE =
            new Template(
                    "ALL",
                    "Enable all available events in the target JVM, with default option values. This will be very expensive and is intended primarily for testing Cryostat's own capabilities.",
                    "Cryostat",
                    TemplateType.TARGET);

    private static final Pattern TEMPLATE_PATTERN =
            Pattern.compile("^template=([\\w]+)(?:,type=([\\w]+))?$");

    static final String PATH = "targets/:targetId/recordings";
    private final TargetConnectionManager targetConnectionManager;
    private final RecordingOptionsBuilderFactory recordingOptionsBuilderFactory;
    private final EventOptionsBuilder.Factory eventOptionsBuilderFactory;
    private final Provider<WebServer> webServerProvider;
    private final Gson gson;
    private final NotificationFactory notificationFactory;
    private static final String NOTIFICATION_CATEGORY = "RecordingCreated";

    @Inject
    TargetRecordingsPostHandler(
            AuthManager auth,
            TargetConnectionManager targetConnectionManager,
            RecordingOptionsBuilderFactory recordingOptionsBuilderFactory,
            EventOptionsBuilder.Factory eventOptionsBuilderFactory,
            Provider<WebServer> webServerProvider,
            Gson gson,
            NotificationFactory notificationFactory) {
        super(auth);
        this.targetConnectionManager = targetConnectionManager;
        this.recordingOptionsBuilderFactory = recordingOptionsBuilderFactory;
        this.eventOptionsBuilderFactory = eventOptionsBuilderFactory;
        this.webServerProvider = webServerProvider;
        this.gson = gson;
        this.notificationFactory = notificationFactory;
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
            Optional<HyperlinkedSerializableRecordingDescriptor> descriptor =
                    targetConnectionManager.executeConnectedTask(
                            getConnectionDescriptorFromContext(ctx),
                            connection -> {
                                if (getDescriptorByName(connection, recordingName).isPresent()) {
                                    throw new HttpStatusException(
                                            400,
                                            String.format(
                                                    "Recording with name \"%s\" already exists",
                                                    recordingName));
                                }

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
                                IConstrainedMap<String> recordingOptions = builder.build();
                                connection
                                        .getService()
                                        .start(
                                                recordingOptions,
                                                enableEvents(connection, eventSpecifier));
                                notificationFactory
                                        .createBuilder()
                                        .metaCategory(NOTIFICATION_CATEGORY)
                                        .metaType(HttpMimeType.JSON)
                                        .message(
                                                Map.of(
                                                        "recording",
                                                        recordingName,
                                                        "target",
                                                        getConnectionDescriptorFromContext(ctx)
                                                                .getTargetId()))
                                        .build()
                                        .send();
                                return getDescriptorByName(connection, recordingName)
                                        .map(
                                                d -> {
                                                    try {
                                                        WebServer webServer =
                                                                webServerProvider.get();
                                                        return new HyperlinkedSerializableRecordingDescriptor(
                                                                d,
                                                                webServer.getDownloadURL(
                                                                        connection, d.getName()),
                                                                webServer.getReportURL(
                                                                        connection, d.getName()));
                                                    } catch (QuantityConversionException
                                                            | URISyntaxException
                                                            | IOException e) {
                                                        throw new HttpStatusException(500, e);
                                                    }
                                                });
                            });

            descriptor.ifPresentOrElse(
                    linkedDescriptor -> {
                        ctx.response().setStatusCode(201);
                        ctx.response().putHeader(HttpHeaders.LOCATION, "/" + recordingName);
                        ctx.response()
                                .putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime());
                        ctx.response().end(gson.toJson(linkedDescriptor));
                    },
                    () -> {
                        throw new HttpStatusException(
                                500, "Unexpected failure to create recording");
                    });
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

    protected IConstrainedMap<EventOptionID> enableEvents(JFRConnection connection, String events)
            throws Exception {
        Matcher m = TEMPLATE_PATTERN.matcher(events);
        m.find();
        String templateName = m.group(1);
        String typeName = m.group(2);
        if (ALL_EVENTS_TEMPLATE.getName().equals(templateName)) {
            return enableAllEvents(connection);
        }
        if (typeName != null) {
            return connection
                    .getTemplateService()
                    .getEvents(templateName, TemplateType.valueOf(typeName))
                    .orElseThrow(
                            () ->
                                    new IllegalArgumentException(
                                            String.format(
                                                    "No template \"%s\" found with type %s",
                                                    templateName, typeName)));
        }
        // if template type not specified, try to find a Custom template by that name. If none,
        // fall back on finding a Target built-in template by the name. If not, throw an
        // exception and bail out.
        return connection
                .getTemplateService()
                .getEvents(templateName, TemplateType.CUSTOM)
                .or(
                        () -> {
                            try {
                                return connection
                                        .getTemplateService()
                                        .getEvents(templateName, TemplateType.TARGET);
                            } catch (Exception e) {
                                return Optional.empty();
                            }
                        })
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        String.format(
                                                "Invalid/unknown event template %s",
                                                templateName)));
    }

    protected IConstrainedMap<EventOptionID> enableAllEvents(JFRConnection connection)
            throws Exception {
        EventOptionsBuilder builder = eventOptionsBuilderFactory.create(connection);

        for (IEventTypeInfo eventTypeInfo : connection.getService().getAvailableEventTypes()) {
            builder.addEvent(eventTypeInfo.getEventTypeID().getFullKey(), "enabled", "true");
        }

        return builder.build();
    }
}
