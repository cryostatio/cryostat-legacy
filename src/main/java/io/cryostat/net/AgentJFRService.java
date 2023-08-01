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
package io.cryostat.net;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.common.unit.IDescribedMap;
import org.openjdk.jmc.common.unit.IOptionDescriptor;
import org.openjdk.jmc.common.unit.IQuantity;
import org.openjdk.jmc.common.unit.ITypedQuantity;
import org.openjdk.jmc.common.unit.QuantityConversionException;
import org.openjdk.jmc.common.unit.UnitLookup;
import org.openjdk.jmc.flightrecorder.configuration.events.EventOptionID;
import org.openjdk.jmc.flightrecorder.configuration.events.IEventTypeID;
import org.openjdk.jmc.flightrecorder.configuration.internal.DefaultValueMap;
import org.openjdk.jmc.flightrecorder.configuration.internal.KnownEventOptions;
import org.openjdk.jmc.flightrecorder.configuration.internal.KnownRecordingOptions;
import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;
import org.openjdk.jmc.rjmx.ConnectionException;
import org.openjdk.jmc.rjmx.ServiceNotAvailableException;
import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IEventTypeInfo;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.core.EventOptionsBuilder.EventOptionException;
import io.cryostat.core.EventOptionsBuilder.EventTypeException;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.CryostatFlightRecorderService;
import io.cryostat.core.templates.MergedTemplateService;
import io.cryostat.core.templates.Template;
import io.cryostat.core.templates.TemplateType;

import org.jsoup.nodes.Document;

class AgentJFRService implements CryostatFlightRecorderService {

    private final AgentClient client;
    private final MergedTemplateService templateService;
    private final Logger logger;

    AgentJFRService(AgentClient client, MergedTemplateService templateService, Logger logger) {
        this.client = client;
        this.templateService = templateService;
        this.logger = logger;
    }

    @Override
    public IDescribedMap<EventOptionID> getDefaultEventOptions() {
        return KnownEventOptions.OPTION_DEFAULTS_V2;
    }

    @Override
    public IDescribedMap<String> getDefaultRecordingOptions() {
        return KnownRecordingOptions.OPTION_DEFAULTS_V2;
    }

    @Override
    public String getVersion() {
        return "agent"; // TODO
    }

    @Override
    public void close(IRecordingDescriptor descriptor) throws FlightRecorderException {
        throw new UnimplementedException();
    }

    @Override
    public void enable() throws FlightRecorderException {
        throw new UnimplementedException();
    }

    @Override
    public Collection<? extends IEventTypeInfo> getAvailableEventTypes()
            throws FlightRecorderException {
        try {
            return client.eventTypes().toCompletionStage().toCompletableFuture().get();
        } catch (ExecutionException | InterruptedException e) {
            logger.warn(e);
            return List.of();
        }
    }

    @Override
    public Map<String, IOptionDescriptor<?>> getAvailableRecordingOptions()
            throws FlightRecorderException {
        return KnownRecordingOptions.DESCRIPTORS_BY_KEY_V2;
    }

    @Override
    public List<IRecordingDescriptor> getAvailableRecordings() throws FlightRecorderException {
        try {
            return client.activeRecordings().toCompletionStage().toCompletableFuture().get();
        } catch (ExecutionException | InterruptedException e) {
            logger.warn(e);
            return List.of();
        }
    }

    @Override
    public IConstrainedMap<EventOptionID> getCurrentEventTypeSettings()
            throws FlightRecorderException {
        try {
            return Optional.of(
                            client.eventSettings().toCompletionStage().toCompletableFuture().get())
                    .orElse(new DefaultValueMap<>(Map.of()));
        } catch (ExecutionException | InterruptedException e) {
            logger.warn(e);
            return new DefaultValueMap<>(Map.of());
        }
    }

    @Override
    public IConstrainedMap<EventOptionID> getEventSettings(IRecordingDescriptor arg0)
            throws FlightRecorderException {
        // TODO Auto-generated method stub
        return new DefaultValueMap<>(Map.of());
    }

    @Override
    public Map<? extends IEventTypeID, ? extends IEventTypeInfo> getEventTypeInfoMapByID()
            throws FlightRecorderException {
        // TODO Auto-generated method stub
        return Map.of();
    }

    @Override
    public IConstrainedMap<String> getRecordingOptions(IRecordingDescriptor arg0)
            throws FlightRecorderException {
        // TODO Auto-generated method stub
        return new DefaultValueMap<>(Map.of());
    }

    @Override
    public List<String> getServerTemplates() throws FlightRecorderException {
        try {
            return client.eventTemplates().toCompletionStage().toCompletableFuture().get();
        } catch (ExecutionException | InterruptedException e) {
            logger.warn(e);
            return List.of();
        }
    }

    @Override
    public IRecordingDescriptor getSnapshotRecording() throws FlightRecorderException {
        throw new UnimplementedException();
    }

    @Override
    public IRecordingDescriptor getUpdatedRecordingDescription(IRecordingDescriptor arg0)
            throws FlightRecorderException {
        // TODO Auto-generated method stub
        throw new UnimplementedException();
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public InputStream openStream(IRecordingDescriptor arg0, boolean arg1)
            throws FlightRecorderException {
        throw new UnimplementedException();
    }

    @Override
    public InputStream openStream(IRecordingDescriptor arg0, IQuantity arg1, boolean arg2)
            throws FlightRecorderException {
        throw new UnimplementedException();
    }

    @Override
    public InputStream openStream(
            IRecordingDescriptor arg0, IQuantity arg1, IQuantity arg2, boolean arg3)
            throws FlightRecorderException {
        throw new UnimplementedException();
    }

    @Override
    public IRecordingDescriptor start(
            IConstrainedMap<String> arg0, IConstrainedMap<EventOptionID> arg1)
            throws FlightRecorderException {
        throw new UnimplementedException();
    }

    @Override
    public void stop(IRecordingDescriptor arg0) throws FlightRecorderException {
        throw new UnimplementedException();
    }

    @Override
    public void updateEventOptions(IRecordingDescriptor arg0, IConstrainedMap<EventOptionID> arg1)
            throws FlightRecorderException {
        throw new UnimplementedException();
    }

    @Override
    public void updateRecordingOptions(IRecordingDescriptor arg0, IConstrainedMap<String> arg1)
            throws FlightRecorderException {
        throw new UnimplementedException();
    }

    @Override
    public IRecordingDescriptor start(
            IConstrainedMap<String> recordingOptions,
            String templateName,
            TemplateType preferredTemplateType)
            throws io.cryostat.core.FlightRecorderException, FlightRecorderException,
                    ConnectionException, IOException, ServiceNotAvailableException,
                    QuantityConversionException, EventOptionException, EventTypeException {
        StartRecordingRequest req;
        String recordingName = recordingOptions.get("name").toString();
        long duration =
                (Optional.ofNullable(
                                        (ITypedQuantity)
                                                recordingOptions.get(
                                                        RecordingOptionsBuilder.KEY_DURATION))
                                .orElse(UnitLookup.MILLISECOND.quantity(0)))
                        .longValueIn(UnitLookup.MILLISECOND);
        long maxSize =
                (Optional.ofNullable(
                                        (ITypedQuantity)
                                                recordingOptions.get(
                                                        RecordingOptionsBuilder.KEY_MAX_SIZE))
                                .orElse(UnitLookup.BYTE.quantity(0)))
                        .longValueIn(UnitLookup.BYTE);
        long maxAge =
                (Optional.ofNullable(
                                        (ITypedQuantity)
                                                recordingOptions.get(
                                                        RecordingOptionsBuilder.KEY_MAX_AGE))
                                .orElse(UnitLookup.MILLISECOND.quantity(0)))
                        .longValueIn(UnitLookup.MILLISECOND);
        if (preferredTemplateType.equals(TemplateType.CUSTOM)) {
            req =
                    new StartRecordingRequest(
                            recordingName,
                            null,
                            templateService
                                    .getXml(templateName, preferredTemplateType)
                                    .orElseThrow()
                                    .outerHtml(),
                            duration,
                            maxSize,
                            maxAge);
        } else {
            req =
                    new StartRecordingRequest(
                            recordingName, templateName, null, duration, maxSize, maxAge);
        }
        try {
            return client.startRecording(req).toCompletionStage().toCompletableFuture().get();
        } catch (ExecutionException | InterruptedException e) {
            throw new io.cryostat.core.FlightRecorderException(e);
        }
    }

    @Override
    public IRecordingDescriptor start(
            IConstrainedMap<String> recordingOptions, Template eventTemplate)
            throws io.cryostat.core.FlightRecorderException, FlightRecorderException,
                    ConnectionException, IOException, FlightRecorderException,
                    ServiceNotAvailableException, QuantityConversionException, EventOptionException,
                    EventTypeException {
        return CryostatFlightRecorderService.super.start(recordingOptions, eventTemplate);
    }

    @Override
    public IRecordingDescriptor start(IConstrainedMap<String> recordingOptions, Document template)
            throws FlightRecorderException, ParseException, IOException {
        throw new UnimplementedException();
    }

    public static class UnimplementedException extends IllegalStateException {}

    static record StartRecordingRequest(
            String name,
            String localTemplateName,
            String template,
            long duration,
            long maxSize,
            long maxAge) {}
}
