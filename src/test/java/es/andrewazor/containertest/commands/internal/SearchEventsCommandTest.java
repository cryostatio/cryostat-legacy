package es.andrewazor.containertest.commands.internal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openjdk.jmc.flightrecorder.configuration.events.IEventTypeID;
import org.openjdk.jmc.rjmx.services.jfr.IEventTypeInfo;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;

import es.andrewazor.containertest.JMCConnection;
import es.andrewazor.containertest.StdoutTest;

@ExtendWith(MockitoExtension.class)
class SearchEventsCommandTest extends StdoutTest {

    private SearchEventsCommand command;
    @Mock private JMCConnection connection;
    @Mock IFlightRecorderService service;

    @BeforeEach
    void setup() {
        command = new SearchEventsCommand();
        command.connectionChanged(connection);
    }

    @Test
    void shouldBeNamedSearchEvents() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("search-events"));
    }

    @Test
    void shouldExpectOneArg() {
        assertTrue(command.validate(new String[1]));
        assertFalse(command.validate(new String[0]));
        assertFalse(command.validate(new String[2]));
    }

    @Test
    void shouldHandleNoMatches() throws Exception {
        when(connection.getService()).thenReturn(service);
        when(service.getAvailableEventTypes()).thenReturn(Collections.emptyList());

        command.execute(new String[] { "foo" });

        MatcherAssert.assertThat(stdout.toString(), Matchers.equalTo("\tNo matches\n"));
    }

    @Test
    void shouldHandleMatches() throws Exception {
        IEventTypeInfo infoA = mock(IEventTypeInfo.class);
        IEventTypeID eventIdA = mock(IEventTypeID.class);
        when(eventIdA.getFullKey()).thenReturn("com.example.A");
        when(infoA.getEventTypeID()).thenReturn(eventIdA);
        when(infoA.getHierarchicalCategory()).thenReturn(new String[0]);
        when(infoA.getDescription()).thenReturn("Does some fooing");

        IEventTypeInfo infoB = mock(IEventTypeInfo.class);
        IEventTypeID eventIdB = mock(IEventTypeID.class);
        when(eventIdB.getFullKey()).thenReturn("com.example.B");
        when(infoB.getEventTypeID()).thenReturn(eventIdB);
        when(infoB.getHierarchicalCategory()).thenReturn(new String[0]);
        when(infoB.getName()).thenReturn("FooProperty");

        IEventTypeInfo infoC = mock(IEventTypeInfo.class);
        IEventTypeID eventIdC = mock(IEventTypeID.class);
        when(eventIdC.getFullKey()).thenReturn("com.example.C");
        when(infoC.getEventTypeID()).thenReturn(eventIdC);
        when(infoC.getHierarchicalCategory()).thenReturn(new String[]{ "com", "example", "Foo"});

        IEventTypeInfo infoD = mock(IEventTypeInfo.class);
        IEventTypeID eventIdD = mock(IEventTypeID.class);
        when(eventIdD.getFullKey()).thenReturn("com.example.Foo");
        when(infoD.getEventTypeID()).thenReturn(eventIdD);
        when(infoD.getHierarchicalCategory()).thenReturn(new String[0]);

        IEventTypeInfo infoE = mock(IEventTypeInfo.class);
        IEventTypeID eventIdE = mock(IEventTypeID.class);
        when(eventIdE.getFullKey()).thenReturn("com.example.E");
        when(infoE.getEventTypeID()).thenReturn(eventIdE);
        when(infoE.getHierarchicalCategory()).thenReturn(new String[0]);
        when(infoE.getName()).thenReturn("bar");
        when(infoE.getDescription()).thenReturn("Does some baring");

        List events = Arrays.asList(infoA, infoB, infoC, infoD, infoE);

        when(connection.getService()).thenReturn(service);
        when(service.getAvailableEventTypes()).thenReturn(events);

        command.execute(new String[] { "foo" });

        String out = stdout.toString();
        MatcherAssert.assertThat(out, Matchers.allOf(
            Matchers.containsString("\tcom.example.A\toptions: []"),
            Matchers.containsString("\tcom.example.B\toptions: []"),
            Matchers.containsString("\tcom.example.C\toptions: []"),
            Matchers.containsString("\tcom.example.Foo\toptions: []")
        ));
        MatcherAssert.assertThat(out, Matchers.not(
            Matchers.anyOf(
                Matchers.containsStringIgnoringCase("bar"),
                Matchers.containsString("com.example.E")
            )
        ));
    }

}