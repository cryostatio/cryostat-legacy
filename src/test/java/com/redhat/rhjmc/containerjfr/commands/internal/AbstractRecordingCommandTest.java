/*-
 * #%L
 * Container JFR
 * %%
 * Copyright (C) 2020 Red Hat, Inc.
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
package com.redhat.rhjmc.containerjfr.commands.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.flightrecorder.configuration.events.EventOptionID;
import org.openjdk.jmc.flightrecorder.configuration.events.IEventTypeID;
import org.openjdk.jmc.rjmx.services.jfr.IEventTypeInfo;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;

import com.redhat.rhjmc.containerjfr.TestBase;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.templates.TemplateService;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.net.TargetConnectionManager;

@ExtendWith(MockitoExtension.class)
class AbstractRecordingCommandTest extends TestBase {

    AbstractRecordingCommand command;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock JFRConnection connection;
    @Mock EventOptionsBuilder.Factory eventOptionsBuilderFactory;
    @Mock RecordingOptionsBuilderFactory recordingOptionsBuilderFactory;

    @BeforeEach
    void setup() {
        command =
                new BaseRecordingCommand(
                        mockClientWriter,
                        targetConnectionManager,
                        eventOptionsBuilderFactory,
                        recordingOptionsBuilderFactory);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "jdk:bar:baz",
                "jdk.Event",
                "Event",
            })
    void shouldNotValidateInvalidEventString(String events) {
        assertFalse(command.validateEvents(events));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "foo.Event:prop=val",
                "foo.Event:prop=val,bar.Event:thing=1",
                "foo.class$Inner:prop=val",
                "template=ALL",
                "template=Foo",
                "template=Continuous,type=TARGET",
                "template=Foo,type=CUSTOM",
            })
    void shouldValidateValidEventString(String events) {
        assertTrue(command.validateEvents(events));
    }

    @Test
    void shouldBuildSelectedEventMap() throws Exception {
        verifyNoInteractions(eventOptionsBuilderFactory);

        EventOptionsBuilder builder = mock(EventOptionsBuilder.class);
        when(eventOptionsBuilderFactory.create(Mockito.any())).thenReturn(builder);

        command.enableEvents(
                connection,
                "foo.Bar$Inner:prop=some,bar.Baz$Inner2:key=val,jdk.CPULoad:enabled=true");

        verify(builder).addEvent("foo.Bar$Inner", "prop", "some");
        verify(builder).addEvent("bar.Baz$Inner2", "key", "val");
        verify(builder).addEvent("jdk.CPULoad", "enabled", "true");
        verify(builder).build();

        verifyNoMoreInteractions(builder);
        verifyNoMoreInteractions(eventOptionsBuilderFactory);
    }

    @Test
    void shouldBuildTemplateEventMap() throws Exception {
        TemplateService templateSvc = mock(TemplateService.class);
        when(connection.getTemplateService()).thenReturn(templateSvc);

        IConstrainedMap<EventOptionID> templateMap = mock(IConstrainedMap.class);
        when(templateSvc.getEvents(Mockito.anyString(), Mockito.any()))
                .thenReturn(Optional.of(templateMap));

        IConstrainedMap<EventOptionID> result = command.enableEvents(connection, "template=Foo");
        assertThat(result, Matchers.sameInstance(templateMap));
    }

    @Test
    void shouldThrowExceptionForUnknownTemplate() throws Exception {
        TemplateService templateSvc = mock(TemplateService.class);
        when(connection.getTemplateService()).thenReturn(templateSvc);

        when(templateSvc.getEvents(Mockito.anyString(), Mockito.any()))
                .thenReturn(Optional.empty());

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> command.enableEvents(connection, "template=Foo"));
    }

    @Test
    void shouldBuildAllEventMap() throws Exception {
        verifyNoInteractions(eventOptionsBuilderFactory);

        EventOptionsBuilder builder = mock(EventOptionsBuilder.class);
        when(eventOptionsBuilderFactory.create(Mockito.any())).thenReturn(builder);

        IEventTypeInfo mockEvent = mock(IEventTypeInfo.class);
        IEventTypeID mockEventTypeId = mock(IEventTypeID.class);
        when(mockEventTypeId.getFullKey()).thenReturn("com.example.Event");
        when(mockEvent.getEventTypeID()).thenReturn(mockEventTypeId);
        IFlightRecorderService mockService = mock(IFlightRecorderService.class);
        when(connection.getService()).thenReturn(mockService);
        when(mockService.getAvailableEventTypes())
                .thenReturn((Collection) Collections.singletonList(mockEvent));

        command.enableEvents(connection, "template=ALL");

        verify(builder).addEvent("com.example.Event", "enabled", "true");
        verify(builder).build();

        verifyNoMoreInteractions(builder);
        verifyNoMoreInteractions(eventOptionsBuilderFactory);
    }

    static class BaseRecordingCommand extends AbstractRecordingCommand {
        BaseRecordingCommand(
                ClientWriter cw,
                TargetConnectionManager targetConnectionManager,
                EventOptionsBuilder.Factory eventOptionsBuilderFactory,
                RecordingOptionsBuilderFactory recordingOptionsBuilderFactory) {
            super(
                    cw,
                    targetConnectionManager,
                    eventOptionsBuilderFactory,
                    recordingOptionsBuilderFactory);
        }

        @Override
        public String getName() {
            return "base";
        }

        @Override
        public void validate(String[] args) throws FailedValidationException {}

        @Override
        public Output<?> execute(String[] args) {
            return new SuccessOutput();
        }
    }
}
