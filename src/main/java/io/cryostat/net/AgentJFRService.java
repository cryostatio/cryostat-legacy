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

import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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

class AgentJFRService implements IFlightRecorderService {

    private final AgentClient client;

    AgentJFRService(AgentClient client) {
        this.client = client;
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
        // TODO Auto-generated method stub
        throw new UnimplementedException();
    }

    @Override
    public void enable() throws FlightRecorderException {
        // TODO Auto-generated method stub
        throw new UnimplementedException();
    }

    @Override
    public Collection<? extends IEventTypeInfo> getAvailableEventTypes()
            throws FlightRecorderException {
        return List.of();
    }

    @Override
    public Map<String, IOptionDescriptor<?>> getAvailableRecordingOptions()
            throws FlightRecorderException {
        return Map.of();
    }

    @Override
    public List<IRecordingDescriptor> getAvailableRecordings() throws FlightRecorderException {
        // TODO Auto-generated method stub
        return List.of();
    }

    @Override
    public IConstrainedMap<EventOptionID> getCurrentEventTypeSettings()
            throws FlightRecorderException {
        // TODO Auto-generated method stub
        return new DefaultValueMap<>(Map.of());
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
        // TODO Auto-generated method stub
        return List.of();
    }

    @Override
    public IRecordingDescriptor getSnapshotRecording() throws FlightRecorderException {
        // TODO Auto-generated method stub
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
        // TODO Auto-generated method stub
        return true;
    }

    @Override
    public InputStream openStream(IRecordingDescriptor arg0, boolean arg1)
            throws FlightRecorderException {
        // TODO Auto-generated method stub
        throw new UnimplementedException();
    }

    @Override
    public InputStream openStream(IRecordingDescriptor arg0, IQuantity arg1, boolean arg2)
            throws FlightRecorderException {
        // TODO Auto-generated method stub
        throw new UnimplementedException();
    }

    @Override
    public InputStream openStream(
            IRecordingDescriptor arg0, IQuantity arg1, IQuantity arg2, boolean arg3)
            throws FlightRecorderException {
        // TODO Auto-generated method stub
        throw new UnimplementedException();
    }

    @Override
    public IRecordingDescriptor start(
            IConstrainedMap<String> arg0, IConstrainedMap<EventOptionID> arg1)
            throws FlightRecorderException {
        // TODO Auto-generated method stub
        throw new UnimplementedException();
    }

    @Override
    public void stop(IRecordingDescriptor arg0) throws FlightRecorderException {
        // TODO Auto-generated method stub
        throw new UnimplementedException();
    }

    @Override
    public void updateEventOptions(IRecordingDescriptor arg0, IConstrainedMap<EventOptionID> arg1)
            throws FlightRecorderException {
        // TODO Auto-generated method stub
        throw new UnimplementedException();
    }

    @Override
    public void updateRecordingOptions(IRecordingDescriptor arg0, IConstrainedMap<String> arg1)
            throws FlightRecorderException {
        // TODO Auto-generated method stub
        throw new UnimplementedException();
    }

    public static class UnimplementedException extends IllegalStateException {}
}
