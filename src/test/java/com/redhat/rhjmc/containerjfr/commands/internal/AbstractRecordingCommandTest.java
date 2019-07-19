package com.redhat.rhjmc.containerjfr.commands.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.Collections;

import com.redhat.rhjmc.containerjfr.TestBase;
import com.redhat.rhjmc.containerjfr.core.net.JMCConnection;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openjdk.jmc.flightrecorder.configuration.events.IEventTypeID;
import org.openjdk.jmc.rjmx.services.jfr.IEventTypeInfo;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;

@ExtendWith(MockitoExtension.class)
class AbstractRecordingCommandTest extends TestBase {

    AbstractRecordingCommand command;
    @Mock JMCConnection connection;
    @Mock EventOptionsBuilder.Factory eventOptionsBuilderFactory;
    @Mock RecordingOptionsBuilderFactory recordingOptionsBuilderFactory;

    @BeforeEach
    void setup() {
        command = new BaseRecordingCommand(mockClientWriter, eventOptionsBuilderFactory, recordingOptionsBuilderFactory);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "jdk:bar:baz",
        "jdk.Event",
        "Event",
    })
    void shouldNotValidateInvalidEventString(String events) {
        assertFalse(command.validateEvents(events));
        assertThat(stdout(), equalTo(events + " is an invalid events pattern\n"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "foo.Event:prop=val",
        "foo.Event:prop=val,bar.Event:thing=1",
        "foo.class$Inner:prop=val",
        "ALL"
    })
    void shouldValidateValidEventString(String events) {
        assertTrue(command.validateEvents(events));
        assertThat(stdout(), emptyString());
    }

    @Test
    void shouldBuildSelectedEventMap() throws Exception {
        verifyZeroInteractions(eventOptionsBuilderFactory);

        EventOptionsBuilder builder = mock(EventOptionsBuilder.class);
        when(eventOptionsBuilderFactory.create(Mockito.any())).thenReturn(builder);

        command.enableEvents("foo.Bar$Inner:prop=some,bar.Baz$Inner2:key=val,jdk.CPULoad:enabled=true");

        verify(builder).addEvent("foo.Bar$Inner", "prop", "some");
        verify(builder).addEvent("bar.Baz$Inner2", "key", "val");
        verify(builder).addEvent("jdk.CPULoad", "enabled", "true");
        verify(builder).build();

        verifyNoMoreInteractions(builder);
        verifyNoMoreInteractions(eventOptionsBuilderFactory);
    }

    @Test
    void shouldBuildAllEventMap() throws Exception {
        verifyZeroInteractions(eventOptionsBuilderFactory);

        EventOptionsBuilder builder = mock(EventOptionsBuilder.class);
        when(eventOptionsBuilderFactory.create(Mockito.any())).thenReturn(builder);

        IEventTypeInfo mockEvent = mock(IEventTypeInfo.class);
        IEventTypeID mockEventTypeId = mock(IEventTypeID.class);
        when(mockEventTypeId.getFullKey()).thenReturn("com.example.Event");
        when(mockEvent.getEventTypeID()).thenReturn(mockEventTypeId);
        IFlightRecorderService mockService = mock(IFlightRecorderService.class);
        when(connection.getService()).thenReturn(mockService);
        when(mockService.getAvailableEventTypes()).thenReturn((Collection) Collections.singletonList(mockEvent));

        command.connectionChanged(connection);
        command.enableEvents("ALL");

        verify(builder).addEvent("com.example.Event", "enabled", "true");
        verify(builder).build();

        verifyNoMoreInteractions(builder);
        verifyNoMoreInteractions(eventOptionsBuilderFactory);
    }

    static class BaseRecordingCommand extends AbstractRecordingCommand {
        BaseRecordingCommand(ClientWriter cw, EventOptionsBuilder.Factory eventOptionsBuilderFactory,
                             RecordingOptionsBuilderFactory recordingOptionsBuilderFactory) {
            super(cw, eventOptionsBuilderFactory, recordingOptionsBuilderFactory);
        }

        @Override
        public String getName() {
            return "base";
        }

        @Override
        public boolean validate(String[] args) {
            return true;
        }

        @Override
        public void execute(String[] args) { }
    }
}
