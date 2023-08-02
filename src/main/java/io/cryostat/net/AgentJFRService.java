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
package io.cryostat.net;

import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.common.unit.IDescribedMap;
import org.openjdk.jmc.common.unit.IOptionDescriptor;
import org.openjdk.jmc.common.unit.IQuantity;
import org.openjdk.jmc.flightrecorder.configuration.events.EventOptionID;
import org.openjdk.jmc.flightrecorder.configuration.events.IEventTypeID;
import org.openjdk.jmc.flightrecorder.configuration.internal.DefaultValueMap;
import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IEventTypeInfo;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.core.log.Logger;

class AgentJFRService implements IFlightRecorderService {

    private final AgentClient client;
    private final Logger logger;

    AgentJFRService(AgentClient client, Logger logger) {
        this.client = client;
        this.logger = logger;
    }

    @Override
    public IDescribedMap<EventOptionID> getDefaultEventOptions() {
        return new DefaultValueMap<>(Map.of());
    }

    @Override
    public IDescribedMap<String> getDefaultRecordingOptions() {
        return new DefaultValueMap<>(Map.of());
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
        // TODO Auto-generated method stub
        return Map.of();
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

    public static class UnimplementedException extends IllegalStateException {}
}
