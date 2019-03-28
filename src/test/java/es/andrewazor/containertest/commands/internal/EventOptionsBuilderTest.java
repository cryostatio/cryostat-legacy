package es.andrewazor.containertest.commands.internal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IEventTypeInfo;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;

import es.andrewazor.containertest.JMCConnection;
import es.andrewazor.containertest.StdoutTest;
import es.andrewazor.containertest.commands.internal.EventOptionsBuilder.EventOptionException;
import es.andrewazor.containertest.commands.internal.EventOptionsBuilder.EventTypeException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventOptionsBuilderTest extends StdoutTest {

    private EventOptionsBuilder builder;
    @Mock private JMCConnection connection;
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
        when(service.getAvailableEventTypes()).thenReturn(capture(Collections.singletonList(event)));

        when(event.getEventTypeID()).thenReturn(eventId);
        when(event.getOptionDescriptors()).thenReturn(capture(Collections.singletonMap("prop", option)));
        when(eventId.getFullKey()).thenReturn("jdk.Foo");
        when(eventId.getFullKey(Mockito.anyString())).thenReturn("jdk.Foo full");

        IConstraint constraint = mock(IConstraint.class);
        when(option.getConstraint()).thenReturn(constraint);
        when(constraint.parseInteractive(Mockito.any())).thenReturn("val");
        when(constraint.validate(Mockito.any())).thenReturn(true);

        builder = new EventOptionsBuilder(connection, () -> true);
    }

    private static <T, V> V capture(T t) {
        // TODO clean up this generics hack
        return (V) t;
    }

    @Test
    void shouldWarnV1Unsupported() throws FlightRecorderException {
        new EventOptionsBuilder(connection, () -> false);
        MatcherAssert.assertThat(stdout.toString(), Matchers.equalTo("Flight Recorder V1 is not yet supported\n"));
    }

    @Test
    void shouldBuildNullMapWhenV1Detected() throws FlightRecorderException {
        MatcherAssert.assertThat(new EventOptionsBuilder(connection, () -> false).build(), Matchers.nullValue());
    }

    @Test
    void shouldAddValidEventToBuiltMap() throws Exception {
        builder.addEvent(eventId.getFullKey(), "prop", "val");

        ArgumentCaptor<EventOptionID> optionIdCaptor = ArgumentCaptor.forClass(EventOptionID.class);
        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        verify(mutableMap).put(optionIdCaptor.capture(), valueCaptor.capture());
        MatcherAssert.assertThat(optionIdCaptor.getValue().getEventTypeID().getFullKey(), Matchers.equalTo(eventId.getFullKey()));
        MatcherAssert.assertThat(valueCaptor.getValue(), Matchers.equalTo("val"));
    }

    @Test
    void shouldReturnServiceProvidedMap() throws Exception {
        MatcherAssert.assertThat(builder.build(), Matchers.sameInstance(mutableMap));
    }

    @Test
    void shouldThrowEventTypeExceptionIfEventTypeUnknown() throws Exception {
        Exception e = assertThrows(EventTypeException.class, () -> {
            builder.addEvent("jdk.Bar", "prop", "val");
        });
        MatcherAssert.assertThat(e.getMessage(), Matchers.equalTo("Unknown event type \"jdk.Bar\""));
    }

    @Test
    void shouldThrowEventOptionExceptionIfOptionUnknown() throws Exception {
        Exception e = assertThrows(EventOptionException.class, () -> {
            builder.addEvent("jdk.Foo", "opt", "val");
        });
        MatcherAssert.assertThat(e.getMessage(), Matchers.equalTo("Unknown option \"opt\" for event \"jdk.Foo\""));
    }

}