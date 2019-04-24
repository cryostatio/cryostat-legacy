package es.andrewazor.containertest.commands.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.emptyString;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import es.andrewazor.containertest.TestBase;
import es.andrewazor.containertest.net.JMCConnection;
import es.andrewazor.containertest.tui.ClientWriter;

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
        "foo.class$Inner:prop=val"
    })
    void shouldValidateValidEventString(String events) {
        assertTrue(command.validateEvents(events));
        assertThat(stdout(), emptyString());
    }

    @Test
    void shouldBuildEventMap() throws Exception {
        verifyZeroInteractions(eventOptionsBuilderFactory);

        EventOptionsBuilder builder = mock(EventOptionsBuilder.class);
        when(eventOptionsBuilderFactory.create(Mockito.any())).thenReturn(builder);

        command.enableEvents("foo.Bar$Inner:prop=val");

        ArgumentCaptor<String> eventCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> optionCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(builder).addEvent(eventCaptor.capture(), optionCaptor.capture(), valueCaptor.capture());
        verify(builder).build();

        assertThat(eventCaptor.getValue(), equalTo("foo.Bar$Inner"));
        assertThat(optionCaptor.getValue(), equalTo("prop"));
        assertThat(valueCaptor.getValue(), equalTo("val"));

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
