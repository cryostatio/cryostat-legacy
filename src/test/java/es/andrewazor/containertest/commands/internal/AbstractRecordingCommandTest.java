package es.andrewazor.containertest.commands.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import es.andrewazor.containertest.JMCConnection;
import es.andrewazor.containertest.TestBase;

@ExtendWith(MockitoExtension.class)
class AbstractRecordingCommandTest extends TestBase {

    private AbstractRecordingCommand command;
    @Mock private JMCConnection connection;
    @Mock private EventOptionsBuilder.Factory eventOptionsBuilderFactory;
    @Mock private RecordingOptionsBuilderFactory recordingOptionsBuilderFactory;

    @BeforeEach
    void setup() {
        command = new BaseRecordingCommand(eventOptionsBuilderFactory, recordingOptionsBuilderFactory);
    }

    @Test
    void emptyStringIsInvalidEventString() {
        assertFalse(command.validateEvents(""));
        assertThat(stdout.toString(), equalTo(" is an invalid events pattern\n"));
    }

    @Test
    void corruptStringIsInvalidEventString() {
        assertFalse(command.validateEvents("jdk:bar:baz"));
        assertThat(stdout.toString(), equalTo("jdk:bar:baz is an invalid events pattern\n"));
    }

    @Test
    void eventWithoutPropertyIsInvalid() {
        assertFalse(command.validateEvents("jdk.Event"));
        assertThat(stdout.toString(), equalTo("jdk.Event is an invalid events pattern\n"));
    }

    @Test
    void singleEventStringIsValid() {
        assertTrue(command.validateEvents("foo.Event:prop=val"));
    }

    @Test
    void multipleEventStringIsValid() {
        assertTrue(command.validateEvents("foo.Event:prop=val,bar.Event:thing=1"));
    }

    @Test
    void shouldBuildEventMap() throws Exception {
        verifyZeroInteractions(eventOptionsBuilderFactory);

        EventOptionsBuilder builder = mock(EventOptionsBuilder.class);
        when(eventOptionsBuilderFactory.create(Mockito.any())).thenReturn(builder);

        command.enableEvents("foo.Bar:prop=val");

        ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> optionCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(builder).addEvent(eventCaptor.capture(), optionCaptor.capture(), valueCaptor.capture());
        verify(builder).build();

        assertThat(eventCaptor.getValue(), equalTo("foo.Bar"));
        assertThat(optionCaptor.getValue(), equalTo("prop"));
        assertThat(valueCaptor.getValue(), equalTo("val"));

        verifyNoMoreInteractions(eventOptionsBuilderFactory);
    }

    private static class BaseRecordingCommand extends AbstractRecordingCommand {
        BaseRecordingCommand(EventOptionsBuilder.Factory eventOptionsBuilderFactory, RecordingOptionsBuilderFactory recordingOptionsBuilderFactory) {
            super(eventOptionsBuilderFactory, recordingOptionsBuilderFactory);
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
