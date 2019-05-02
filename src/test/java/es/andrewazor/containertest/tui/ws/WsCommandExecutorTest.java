package es.andrewazor.containertest.tui.ws;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import es.andrewazor.containertest.commands.CommandRegistry;
import es.andrewazor.containertest.tui.ClientReader;
import es.andrewazor.containertest.tui.ClientWriter;

@ExtendWith(MockitoExtension.class)
class WsCommandExecutorTest {

    WsCommandExecutor executor;
    @Mock MessagingServer server;
    @Mock WsClientReaderWriter connection;
    @Mock ClientReader cr;
    @Mock ClientWriter cw;
    @Mock CommandRegistry commandRegistry;
    Gson gson;
    static PrintStream origErr;
    static OutputStream errStream;

    @BeforeAll
    static void setErr() {
        origErr = System.err;
        errStream = new ByteArrayOutputStream();
        System.setErr(new PrintStream(errStream));
    }

    @AfterAll
    static void resetErr() {
        System.setErr(origErr);
    }

    @BeforeEach
    void setup() {
        origErr = System.err;
        gson = new GsonBuilder().serializeNulls().create();
        executor = new WsCommandExecutor(server, cr, cw, () -> commandRegistry, gson);
    }

    @Test
    void shouldExecuteWellFormedValidCommand() throws Exception {
        when(cr.readLine()).thenReturn("{\"command\":\"help\",\"args\":[]}")
                .thenThrow(new RuntimeException("To terminate executor loop"));
        when(server.getConnection()).thenReturn(connection);
        when(commandRegistry.validate(Mockito.anyString(), Mockito.any(String[].class))).thenReturn(true);
        when(commandRegistry.isCommandAvailable(Mockito.anyString())).thenReturn(true);

        executor.run(null);

        InOrder inOrder = inOrder(commandRegistry, connection);
        inOrder.verify(commandRegistry).validate("help", new String[0]);
        inOrder.verify(connection).clearBuffer();
        inOrder.verify(commandRegistry).execute("help", new String[0]);
        inOrder.verify(connection).flush(Mockito.any(SuccessResponseMessage.class));

        MatcherAssert.assertThat(errStream.toString(), Matchers.containsString("To terminate executor loop"));
    }

    @Test
    void shouldExecuteWellFormedValidCommandWithArgs() throws Exception {
        when(cr.readLine()).thenReturn("{\"command\":\"help\",\"args\":[\"hello\",\"world\"]}")
                .thenThrow(new RuntimeException("To terminate executor loop"));
        when(server.getConnection()).thenReturn(connection);
        when(commandRegistry.validate(Mockito.anyString(), Mockito.any(String[].class))).thenReturn(true);
        when(commandRegistry.isCommandAvailable(Mockito.anyString())).thenReturn(true);

        executor.run(null);

        InOrder inOrder = inOrder(commandRegistry, connection);
        inOrder.verify(commandRegistry).validate("help", new String[]{ "hello", "world" });
        inOrder.verify(connection).clearBuffer();
        inOrder.verify(commandRegistry).execute("help", new String[]{ "hello", "world" });
        inOrder.verify(connection).flush(Mockito.any(SuccessResponseMessage.class));

        MatcherAssert.assertThat(errStream.toString(), Matchers.containsString("To terminate executor loop"));
    }

    @Test
    void shouldSkipNullLines() throws Exception {
        when(cr.readLine()).thenReturn(null)
                .thenThrow(new RuntimeException("To terminate executor loop"));

        executor.run(null);

        verifyZeroInteractions(commandRegistry);
        verifyZeroInteractions(server);
        verifyZeroInteractions(cw);

        MatcherAssert.assertThat(errStream.toString(), Matchers.containsString("To terminate executor loop"));
    }

    @Test
    void shouldInterpretMissingArgsAsEmpty() throws Exception {
        when(cr.readLine()).thenReturn("{\"command\":\"help\"}")
                .thenThrow(new RuntimeException("To terminate executor loop"));
        when(server.getConnection()).thenReturn(connection);
        when(commandRegistry.validate(Mockito.anyString(), Mockito.any(String[].class))).thenReturn(true);
        when(commandRegistry.isCommandAvailable(Mockito.anyString())).thenReturn(true);

        executor.run(null);

        verify(commandRegistry).validate("help", new String[0]);
        verify(commandRegistry).execute("help", new String[0]);

        MatcherAssert.assertThat(errStream.toString(), Matchers.containsString("To terminate executor loop"));
    }

    @Test
    void shouldRespondToInvalidCommand() throws Exception {
        when(cr.readLine()).thenReturn("{\"command\":\"foo\"}")
                .thenThrow(new RuntimeException("To terminate executor loop"));
        when(server.getConnection()).thenReturn(connection);
        when(commandRegistry.validate(Mockito.anyString(), Mockito.any(String[].class))).thenReturn(false);

        executor.run(null);

        verify(commandRegistry).validate("foo", new String[0]);
        verifyNoMoreInteractions(commandRegistry);

        ArgumentCaptor<ResponseMessage> messageCaptor = ArgumentCaptor.forClass(ResponseMessage.class);
        verify(connection).flush(messageCaptor.capture());
        ResponseMessage message = messageCaptor.getValue();
        MatcherAssert.assertThat(message.status, Matchers.equalTo(-1));

        MatcherAssert.assertThat(errStream.toString(), Matchers.containsString("To terminate executor loop"));
    }

    @Test
    void shouldRespondToUnavailableCommand() throws Exception {
        when(cr.readLine()).thenReturn("{\"command\":\"foo\"}")
                .thenThrow(new RuntimeException("To terminate executor loop"));
        when(server.getConnection()).thenReturn(connection);
        when(commandRegistry.validate(Mockito.anyString(), Mockito.any(String[].class))).thenReturn(true);
        when(commandRegistry.isCommandAvailable(Mockito.anyString())).thenReturn(false);

        executor.run(null);

        verify(commandRegistry).validate("foo", new String[0]);
        verifyNoMoreInteractions(commandRegistry);

        ArgumentCaptor<ResponseMessage> messageCaptor = ArgumentCaptor.forClass(ResponseMessage.class);
        verify(connection).flush(messageCaptor.capture());
        ResponseMessage message = messageCaptor.getValue();
        MatcherAssert.assertThat(message.status, Matchers.equalTo(-1));

        MatcherAssert.assertThat(errStream.toString(), Matchers.containsString("To terminate executor loop"));
    }

    @Test
    void shouldReportCommandExecutionExceptions() throws Exception {
        when(cr.readLine()).thenReturn("{\"command\":\"help\",\"args\":[]}")
                .thenThrow(new RuntimeException("To terminate executor loop"));
        when(server.getConnection()).thenReturn(connection);
        when(commandRegistry.validate(Mockito.anyString(), Mockito.any(String[].class))).thenReturn(true);
        when(commandRegistry.isCommandAvailable(Mockito.anyString())).thenReturn(true);
        doThrow(IOException.class).when(commandRegistry).execute(Mockito.anyString(), Mockito.any(String[].class));

        executor.run(null);

        ArgumentCaptor<CommandExceptionResponseMessage> messageCaptor = ArgumentCaptor.forClass(CommandExceptionResponseMessage.class);
        InOrder inOrder = inOrder(commandRegistry, connection);
        inOrder.verify(commandRegistry).validate("help", new String[0]);
        inOrder.verify(connection).clearBuffer();
        inOrder.verify(commandRegistry).execute("help", new String[0]);
        inOrder.verify(connection).flush(messageCaptor.capture());
        ResponseMessage message = messageCaptor.getValue();
        MatcherAssert.assertThat(message.status, Matchers.equalTo(-2));

        MatcherAssert.assertThat(errStream.toString(), Matchers.containsString("To terminate executor loop"));
    }

    @Test
    void shouldReportInvalidJSONExceptions() throws Exception {
        when(cr.readLine()).thenReturn("{\"command\":\"help}")
                .thenThrow(new RuntimeException("To terminate executor loop"));
        when(server.getConnection()).thenReturn(connection);

        executor.run(null);

        ArgumentCaptor<CommandExceptionResponseMessage> messageCaptor = ArgumentCaptor.forClass(CommandExceptionResponseMessage.class);
        verify(connection).flush(messageCaptor.capture());
        ResponseMessage message = messageCaptor.getValue();
        MatcherAssert.assertThat(message.status, Matchers.equalTo(-2));

        verifyZeroInteractions(commandRegistry);

        MatcherAssert.assertThat(errStream.toString(), Matchers.containsString("To terminate executor loop"));
    }

}