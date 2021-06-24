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
package io.cryostat.recordings;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.flightrecorder.configuration.events.EventOptionID;
import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;
import org.openjdk.jmc.rjmx.services.jfr.IEventTypeInfo;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.commands.internal.EventOptionsBuilder;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.core.templates.TemplateType;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.web.http.HttpMimeType;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

public class RecordingCreationHelper {

    private static final String NOTIFICATION_CATEGORY = "RecordingCreated";

    private static final Pattern TEMPLATE_PATTERN =
            Pattern.compile("^template=([\\w]+)(?:,type=([\\w]+))?$");

    private final TargetConnectionManager targetConnectionManager;
    private final EventOptionsBuilder.Factory eventOptionsBuilderFactory;
    private final NotificationFactory notificationFactory;

    RecordingCreationHelper(
            TargetConnectionManager targetConnectionManager,
            EventOptionsBuilder.Factory eventOptionsBuilderFactory,
            NotificationFactory notificationFactory) {
        this.targetConnectionManager = targetConnectionManager;
        this.eventOptionsBuilderFactory = eventOptionsBuilderFactory;
        this.notificationFactory = notificationFactory;
    }

    public IRecordingDescriptor startRecording(
            ConnectionDescriptor connectionDescriptor,
            IConstrainedMap<String> recordingOptions,
            String templateName,
            TemplateType templateType)
            throws Exception {
        String recordingName = (String) recordingOptions.get(RecordingOptionsBuilder.KEY_NAME);
        return targetConnectionManager.executeConnectedTask(
                connectionDescriptor,
                connection -> {
                    if (getDescriptorByName(connection, recordingName).isPresent()) {
                        throw new IllegalArgumentException(
                                String.format(
                                        "Recording with name \"%s\" already exists",
                                        recordingName));
                    }
                    IRecordingDescriptor desc =
                            connection
                                    .getService()
                                    .start(
                                            recordingOptions,
                                            enableEvents(connection, templateName, templateType));
                    notificationFactory
                            .createBuilder()
                            .metaCategory(NOTIFICATION_CATEGORY)
                            .metaType(HttpMimeType.JSON)
                            .message(
                                    Map.of(
                                            "recording",
                                            recordingName,
                                            "target",
                                            connectionDescriptor.getTargetId()))
                            .build()
                            .send();
                    return desc;
                });
    }

    public static Pair<String, TemplateType> parseEventSpecifierToTemplate(String eventSpecifier)
            throws IllegalArgumentException {
        if (TEMPLATE_PATTERN.matcher(eventSpecifier).matches()) {
            Matcher m = TEMPLATE_PATTERN.matcher(eventSpecifier);
            m.find();
            String templateName = m.group(1);
            String typeName = m.group(2);
            TemplateType templateType = null;
            if (StringUtils.isNotBlank(typeName)) {
                templateType = TemplateType.valueOf(typeName.toUpperCase());
            }
            return Pair.of(templateName, templateType);
        }
        throw new IllegalArgumentException(eventSpecifier);
    }

    private Optional<IRecordingDescriptor> getDescriptorByName(
            JFRConnection connection, String recordingName) throws Exception {
        return connection.getService().getAvailableRecordings().stream()
                .filter(recording -> recording.getName().equals(recordingName))
                .findFirst();
    }

    private IConstrainedMap<EventOptionID> enableEvents(
            JFRConnection connection, String templateName, TemplateType templateType)
            throws Exception {
        if (templateName.equals("ALL")) {
            return enableAllEvents(connection);
        }
        if (templateType != null) {
            return connection
                    .getTemplateService()
                    .getEvents(templateName, templateType)
                    .orElseThrow(
                            () ->
                                    new IllegalArgumentException(
                                            String.format(
                                                    "No template \"%s\" found with type %s",
                                                    templateName, templateType)));
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

    private IConstrainedMap<EventOptionID> enableAllEvents(JFRConnection connection)
            throws Exception {
        EventOptionsBuilder builder = eventOptionsBuilderFactory.create(connection);

        for (IEventTypeInfo eventTypeInfo : connection.getService().getAvailableEventTypes()) {
            builder.addEvent(eventTypeInfo.getEventTypeID().getFullKey(), "enabled", "true");
        }

        return builder.build();
    }
}
