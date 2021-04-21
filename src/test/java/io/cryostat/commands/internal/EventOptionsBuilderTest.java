/*-
 * #%L
 * Cryostat
 * %%
 * Copyright (C) 2020 - 2021 The Cryostat Authors
 * %%
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
 * #L%
 */
package io.cryostat.commands.internal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.util.Collections;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.openjdk.jmc.common.unit.IConstraint;
import org.openjdk.jmc.common.unit.IDescribedMap;
import org.openjdk.jmc.common.unit.IMutableConstrainedMap;
import org.openjdk.jmc.common.unit.IOptionDescriptor;
import org.openjdk.jmc.flightrecorder.configuration.events.EventOptionID;
import org.openjdk.jmc.flightrecorder.configuration.events.IEventTypeID;
import org.openjdk.jmc.rjmx.services.jfr.IEventTypeInfo;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;

import io.cryostat.TestBase;
import io.cryostat.core.net.JFRConnection;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventOptionsBuilderTest extends TestBase {

    private EventOptionsBuilder builder;
    @Mock private JFRConnection connection;
    @Mock private IFlightRecorderService service;
    @Mock private IDescribedMap map;
    @Mock private IMutableConstrainedMap mutableMap;
    @Mock private IOptionDescriptor option;
    @Mock private IEventTypeInfo event;
    @Mock private IEventTypeID eventId;

    @BeforeEach
    void setup() throws Exception {
        when(connection.getService()).thenReturn(service);
        when(service.getDefaultEventOptions()).thenReturn(map);
        when(map.emptyWithSameConstraints()).thenReturn(mutableMap);
        when(service.getAvailableEventTypes())
                .thenReturn(EventOptionsBuilder.capture(Collections.singletonList(event)));

        when(event.getEventTypeID()).thenReturn(eventId);
        when(event.getOptionDescriptors())
                .thenReturn(EventOptionsBuilder.capture(Collections.singletonMap("prop", option)));
        when(eventId.getFullKey()).thenReturn("jdk.Foo");
        when(eventId.getFullKey(Mockito.anyString())).thenReturn("jdk.Foo full");

        IConstraint constraint = mock(IConstraint.class);
        when(option.getConstraint()).thenReturn(constraint);
        when(constraint.parseInteractive(Mockito.any())).thenReturn("val");
        when(constraint.validate(Mockito.any())).thenReturn(true);

        builder = new EventOptionsBuilder(mockClientWriter, connection, () -> true);
    }

    @Test
    void shouldWarnV1Unsupported() throws Exception {
        new EventOptionsBuilder(mockClientWriter, connection, () -> false);
        MatcherAssert.assertThat(
                stdout(), Matchers.equalTo("Flight Recorder V1 is not yet supported\n"));
    }

    @Test
    void shouldWarnV1Unsupported2() throws Exception {
        new EventOptionsBuilder(mockClientWriter, connection, () -> false);
        MatcherAssert.assertThat(
                stdout(), Matchers.equalTo("Flight Recorder V1 is not yet supported\n"));
    }

    @Test
    void shouldBuildNullMapWhenV1Detected() throws Exception {
        MatcherAssert.assertThat(
                new EventOptionsBuilder(mockClientWriter, connection, () -> false).build(),
                Matchers.nullValue());
    }

    @Test
    void shouldAddValidEventToBuiltMap() throws Exception {
        builder.addEvent(eventId.getFullKey(), "prop", "val");

        ArgumentCaptor<EventOptionID> optionIdCaptor = ArgumentCaptor.forClass(EventOptionID.class);
        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        verify(mutableMap).put(optionIdCaptor.capture(), valueCaptor.capture());
        MatcherAssert.assertThat(
                optionIdCaptor.getValue().getEventTypeID().getFullKey(),
                Matchers.equalTo(eventId.getFullKey()));
        MatcherAssert.assertThat(valueCaptor.getValue(), Matchers.equalTo("val"));
    }

    @Test
    void shouldReturnServiceProvidedMap() throws Exception {
        MatcherAssert.assertThat(builder.build(), Matchers.sameInstance(mutableMap));
    }

    @Test
    void shouldThrowEventTypeExceptionIfEventTypeUnknown() throws Exception {
        Exception e =
                assertThrows(
                        EventOptionsBuilder.EventTypeException.class,
                        () -> {
                            builder.addEvent("jdk.Bar", "prop", "val");
                        });
        MatcherAssert.assertThat(
                e.getMessage(), Matchers.equalTo("Unknown event type \"jdk.Bar\""));
    }

    @Test
    void shouldThrowEventOptionExceptionIfOptionUnknown() throws Exception {
        Exception e =
                assertThrows(
                        EventOptionsBuilder.EventOptionException.class,
                        () -> {
                            builder.addEvent("jdk.Foo", "opt", "val");
                        });
        MatcherAssert.assertThat(
                e.getMessage(), Matchers.equalTo("Unknown option \"opt\" for event \"jdk.Foo\""));
    }

    @ExtendWith(MockitoExtension.class)
    static class FactoryTest extends TestBase {

        private EventOptionsBuilder.Factory factory;
        @Mock private JFRConnection connection;
        @Mock private IFlightRecorderService service;
        @Mock private IDescribedMap map;
        @Mock private IMutableConstrainedMap mutableMap;

        @BeforeEach
        void setup() {
            factory = new EventOptionsBuilder.Factory(mockClientWriter);
        }

        @Test
        void shouldCreateBuilder() throws Exception {
            when(connection.getService()).thenReturn(service);
            when(service.getDefaultEventOptions()).thenReturn(map);
            when(map.emptyWithSameConstraints()).thenReturn(mutableMap);
            EventOptionsBuilder result = factory.create(connection);
            MatcherAssert.assertThat(
                    stdout(), Matchers.equalTo("Flight Recorder V1 is not yet supported\n"));
        }
    }
}
