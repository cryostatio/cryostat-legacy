package com.redhat.rhjmc.containerjfr.tui.ws;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.redhat.rhjmc.containerjfr.MainModule;
import com.redhat.rhjmc.containerjfr.commands.SerializableCommand.ExceptionOutput;
import com.redhat.rhjmc.containerjfr.commands.SerializableCommand.FailureOutput;
import com.redhat.rhjmc.containerjfr.commands.SerializableCommand.ListOutput;
import com.redhat.rhjmc.containerjfr.commands.SerializableCommand.MapOutput;
import com.redhat.rhjmc.containerjfr.commands.SerializableCommand.Output;
import com.redhat.rhjmc.containerjfr.commands.SerializableCommand.StringOutput;
import com.redhat.rhjmc.containerjfr.commands.SerializableCommand.SuccessOutput;
import com.redhat.rhjmc.containerjfr.commands.SerializableCommandRegistry;
import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.tui.ClientReader;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.MalformedJsonException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class WsCommandExecutorTest {

    WsCommandExecutor executor;
    @Mock MessagingServer server;
    @Mock Logger logger;
    @Mock ClientReader cr;
    @Mock SerializableCommandRegistry commandRegistry;
    Gson gson = MainModule.provideGson();

    @BeforeEach
    void setup() {
        executor = new WsCommandExecutor(logger, server, cr, () -> commandRegistry, gson);
    }

    @Test
    void shouldExecuteWellFormedValidCommand() throws Exception {
        when(cr.readLine())
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                executor.shutdown();
                                return "{\"command\":\"help\",\"args\":[]}";
                            }
                        });
        when(commandRegistry.getRegisteredCommandNames()).thenReturn(Collections.singleton("help"));
        when(commandRegistry.isCommandAvailable(Mockito.anyString())).thenReturn(true);
        when(commandRegistry.validate(Mockito.anyString(), Mockito.any(String[].class)))
                .thenReturn(true);
        when(commandRegistry.execute(Mockito.anyString(), Mockito.any(String[].class)))
                .thenReturn(new SuccessOutput());

        executor.run(null);

        InOrder inOrder = inOrder(commandRegistry, server);
        inOrder.verify(commandRegistry).getRegisteredCommandNames();
        inOrder.verify(commandRegistry).isCommandAvailable("help");
        inOrder.verify(commandRegistry).validate("help", new String[0]);
        inOrder.verify(commandRegistry).execute("help", new String[0]);
        inOrder.verify(server).flush(Mockito.any(SuccessResponseMessage.class));
    }

    @Test
    void shouldExecuteWellFormedValidCommandWithArgs() throws Exception {
        when(cr.readLine())
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                executor.shutdown();
                                return "{\"command\":\"help\",\"args\":[\"hello\",\"world\"]}";
                            }
                        });
        when(commandRegistry.getRegisteredCommandNames()).thenReturn(Collections.singleton("help"));
        when(commandRegistry.isCommandAvailable(Mockito.anyString())).thenReturn(true);
        when(commandRegistry.validate(Mockito.anyString(), Mockito.any(String[].class)))
                .thenReturn(true);
        when(commandRegistry.execute(Mockito.anyString(), Mockito.any(String[].class)))
                .thenReturn(new SuccessOutput());

        executor.run(null);

        InOrder inOrder = inOrder(commandRegistry, server);
        inOrder.verify(commandRegistry).getRegisteredCommandNames();
        inOrder.verify(commandRegistry).isCommandAvailable("help");
        inOrder.verify(commandRegistry).validate("help", new String[] {"hello", "world"});
        inOrder.verify(commandRegistry).execute("help", new String[] {"hello", "world"});
        inOrder.verify(server).flush(Mockito.any(SuccessResponseMessage.class));
    }

    @Test
    void shouldHandleFailureOutputs() throws Exception {
        when(cr.readLine())
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                executor.shutdown();
                                return "{\"command\":\"help\",\"args\":[\"hello\",\"world\"]}";
                            }
                        });
        when(commandRegistry.getRegisteredCommandNames()).thenReturn(Collections.singleton("help"));
        when(commandRegistry.isCommandAvailable(Mockito.anyString())).thenReturn(true);
        when(commandRegistry.validate(Mockito.anyString(), Mockito.any(String[].class)))
                .thenReturn(true);
        when(commandRegistry.execute(Mockito.anyString(), Mockito.any(String[].class)))
                .thenReturn(new FailureOutput("some reason"));

        executor.run(null);

        ArgumentCaptor<FailureResponseMessage> response =
                ArgumentCaptor.forClass(FailureResponseMessage.class);
        InOrder inOrder = inOrder(commandRegistry, server);
        inOrder.verify(commandRegistry).getRegisteredCommandNames();
        inOrder.verify(commandRegistry).isCommandAvailable("help");
        inOrder.verify(commandRegistry).validate("help", new String[] {"hello", "world"});
        inOrder.verify(commandRegistry).execute("help", new String[] {"hello", "world"});
        inOrder.verify(server).flush(response.capture());

        MatcherAssert.assertThat(response.getValue().status, Matchers.equalTo(-1));
        MatcherAssert.assertThat(response.getValue().commandName, Matchers.equalTo("help"));
        MatcherAssert.assertThat(response.getValue().payload, Matchers.equalTo("some reason"));
    }

    @Test
    void shouldHandleStringOutputs() throws Exception {
        when(cr.readLine())
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                executor.shutdown();
                                return "{\"command\":\"help\",\"args\":[\"hello\",\"world\"]}";
                            }
                        });
        when(commandRegistry.getRegisteredCommandNames()).thenReturn(Collections.singleton("help"));
        when(commandRegistry.isCommandAvailable(Mockito.anyString())).thenReturn(true);
        when(commandRegistry.validate(Mockito.anyString(), Mockito.any(String[].class)))
                .thenReturn(true);
        when(commandRegistry.execute(Mockito.anyString(), Mockito.any(String[].class)))
                .thenReturn(new StringOutput("some reason"));

        executor.run(null);

        ArgumentCaptor<SuccessResponseMessage<String>> response =
                ArgumentCaptor.forClass(SuccessResponseMessage.class);
        InOrder inOrder = inOrder(commandRegistry, server);
        inOrder.verify(commandRegistry).getRegisteredCommandNames();
        inOrder.verify(commandRegistry).isCommandAvailable("help");
        inOrder.verify(commandRegistry).validate("help", new String[] {"hello", "world"});
        inOrder.verify(commandRegistry).execute("help", new String[] {"hello", "world"});
        inOrder.verify(server).flush(response.capture());

        MatcherAssert.assertThat(response.getValue().status, Matchers.equalTo(0));
        MatcherAssert.assertThat(response.getValue().commandName, Matchers.equalTo("help"));
        MatcherAssert.assertThat(response.getValue().payload, Matchers.equalTo("some reason"));
    }

    @Test
    void shouldHandleListOutputs() throws Exception {
        when(cr.readLine())
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                executor.shutdown();
                                return "{\"command\":\"help\",\"args\":[\"hello\",\"world\"]}";
                            }
                        });
        when(commandRegistry.getRegisteredCommandNames()).thenReturn(Collections.singleton("help"));
        when(commandRegistry.isCommandAvailable(Mockito.anyString())).thenReturn(true);
        when(commandRegistry.validate(Mockito.anyString(), Mockito.any(String[].class)))
                .thenReturn(true);
        when(commandRegistry.execute(Mockito.anyString(), Mockito.any(String[].class)))
                .thenReturn(new ListOutput<Integer>(Arrays.asList(3, 1, 4, 1, 5, 9)));

        executor.run(null);

        ArgumentCaptor<SuccessResponseMessage<List<Integer>>> response =
                ArgumentCaptor.forClass(SuccessResponseMessage.class);
        InOrder inOrder = inOrder(commandRegistry, server);
        inOrder.verify(commandRegistry).getRegisteredCommandNames();
        inOrder.verify(commandRegistry).isCommandAvailable("help");
        inOrder.verify(commandRegistry).validate("help", new String[] {"hello", "world"});
        inOrder.verify(commandRegistry).execute("help", new String[] {"hello", "world"});
        inOrder.verify(server).flush(response.capture());

        MatcherAssert.assertThat(response.getValue().status, Matchers.equalTo(0));
        MatcherAssert.assertThat(response.getValue().commandName, Matchers.equalTo("help"));
        MatcherAssert.assertThat(
                response.getValue().payload, Matchers.equalTo(Arrays.asList(3, 1, 4, 1, 5, 9)));
    }

    @Test
    void shouldHandleMapOutputs() throws Exception {
        when(cr.readLine())
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                executor.shutdown();
                                return "{\"command\":\"help\",\"args\":[\"hello\",\"world\"]}";
                            }
                        });
        when(commandRegistry.getRegisteredCommandNames()).thenReturn(Collections.singleton("help"));
        when(commandRegistry.isCommandAvailable(Mockito.anyString())).thenReturn(true);
        when(commandRegistry.validate(Mockito.anyString(), Mockito.any(String[].class)))
                .thenReturn(true);
        when(commandRegistry.execute(Mockito.anyString(), Mockito.any(String[].class)))
                .thenReturn(new MapOutput<String, String>(Map.of("foo", "bar")));

        executor.run(null);

        ArgumentCaptor<SuccessResponseMessage<Map<String, String>>> response =
                ArgumentCaptor.forClass(SuccessResponseMessage.class);
        InOrder inOrder = inOrder(commandRegistry, server);
        inOrder.verify(commandRegistry).getRegisteredCommandNames();
        inOrder.verify(commandRegistry).isCommandAvailable("help");
        inOrder.verify(commandRegistry).validate("help", new String[] {"hello", "world"});
        inOrder.verify(commandRegistry).execute("help", new String[] {"hello", "world"});
        inOrder.verify(server).flush(response.capture());

        MatcherAssert.assertThat(response.getValue().status, Matchers.equalTo(0));
        MatcherAssert.assertThat(response.getValue().commandName, Matchers.equalTo("help"));
        MatcherAssert.assertThat(
                response.getValue().payload, Matchers.equalTo(Map.of("foo", "bar")));
    }

    @Test
    void shouldHandleExceptionOutputs() throws Exception {
        when(cr.readLine())
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                executor.shutdown();
                                return "{\"command\":\"help\",\"args\":[\"hello\",\"world\"]}";
                            }
                        });
        when(commandRegistry.getRegisteredCommandNames()).thenReturn(Collections.singleton("help"));
        when(commandRegistry.isCommandAvailable(Mockito.anyString())).thenReturn(true);
        when(commandRegistry.validate(Mockito.anyString(), Mockito.any(String[].class)))
                .thenReturn(true);
        when(commandRegistry.execute(Mockito.anyString(), Mockito.any(String[].class)))
                .thenReturn(new ExceptionOutput(new IOException("broken pipe")));

        executor.run(null);

        ArgumentCaptor<CommandExceptionResponseMessage> response =
                ArgumentCaptor.forClass(CommandExceptionResponseMessage.class);
        InOrder inOrder = inOrder(commandRegistry, server);
        inOrder.verify(commandRegistry).getRegisteredCommandNames();
        inOrder.verify(commandRegistry).isCommandAvailable("help");
        inOrder.verify(commandRegistry).validate("help", new String[] {"hello", "world"});
        inOrder.verify(commandRegistry).execute("help", new String[] {"hello", "world"});
        inOrder.verify(server).flush(response.capture());

        MatcherAssert.assertThat(response.getValue().status, Matchers.equalTo(-2));
        MatcherAssert.assertThat(response.getValue().commandName, Matchers.equalTo("help"));
        MatcherAssert.assertThat(
                response.getValue().payload, Matchers.equalTo("IOException: broken pipe"));
    }

    @Test
    void shouldHandleUnknownOutputs() throws Exception {
        when(cr.readLine())
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                executor.shutdown();
                                return "{\"command\":\"help\",\"args\":[\"hello\",\"world\"]}";
                            }
                        });
        when(commandRegistry.getRegisteredCommandNames()).thenReturn(Collections.singleton("help"));
        when(commandRegistry.isCommandAvailable(Mockito.anyString())).thenReturn(true);
        when(commandRegistry.validate(Mockito.anyString(), Mockito.any(String[].class)))
                .thenReturn(true);
        when(commandRegistry.execute(Mockito.anyString(), Mockito.any(String[].class)))
                .thenReturn(
                        new Output<Void>() {
                            @Override
                            public Void getPayload() {
                                return null;
                            }
                        });

        executor.run(null);

        ArgumentCaptor<CommandExceptionResponseMessage> response =
                ArgumentCaptor.forClass(CommandExceptionResponseMessage.class);
        InOrder inOrder = inOrder(commandRegistry, server);
        inOrder.verify(commandRegistry).getRegisteredCommandNames();
        inOrder.verify(commandRegistry).isCommandAvailable("help");
        inOrder.verify(commandRegistry).validate("help", new String[] {"hello", "world"});
        inOrder.verify(commandRegistry).execute("help", new String[] {"hello", "world"});
        inOrder.verify(server).flush(response.capture());

        MatcherAssert.assertThat(response.getValue().status, Matchers.equalTo(-2));
        MatcherAssert.assertThat(response.getValue().commandName, Matchers.equalTo("help"));
        MatcherAssert.assertThat(response.getValue().payload, Matchers.equalTo("internal error"));
    }

    @Test
    void shouldSkipNullLines() throws Exception {
        when(cr.readLine())
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                executor.shutdown();
                                return null;
                            }
                        });

        executor.run(null);

        verifyZeroInteractions(commandRegistry);
        verifyZeroInteractions(server);
    }

    @Test
    void shouldSkipEmptyLines() throws Exception {
        when(cr.readLine())
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                executor.shutdown();
                                return "";
                            }
                        });

        executor.run(null);

        verifyZeroInteractions(commandRegistry);
        verifyZeroInteractions(server);
    }

    @Test
    void shouldSkipBlankLines() throws Exception {
        when(cr.readLine())
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                executor.shutdown();
                                return "\t ";
                            }
                        });

        executor.run(null);

        verifyZeroInteractions(commandRegistry);
        verifyZeroInteractions(server);
    }

    @Test
    void shouldSkipTextWordNull() throws Exception {
        when(cr.readLine())
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                executor.shutdown();
                                return "null";
                            }
                        });

        executor.run(null);

        verifyZeroInteractions(commandRegistry);
        verifyZeroInteractions(server);
    }

    @Test
    void shouldInterpretMissingArgsAsEmpty() throws Exception {
        when(cr.readLine())
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                executor.shutdown();
                                return "{\"command\":\"help\"}";
                            }
                        });
        when(commandRegistry.getRegisteredCommandNames()).thenReturn(Collections.singleton("help"));
        when(commandRegistry.isCommandAvailable(Mockito.anyString())).thenReturn(true);
        when(commandRegistry.validate(Mockito.anyString(), Mockito.any(String[].class)))
                .thenReturn(true);
        when(commandRegistry.execute(Mockito.anyString(), Mockito.any(String[].class)))
                .thenReturn(new SuccessOutput());

        executor.run(null);

        InOrder inOrder = inOrder(commandRegistry, server);
        inOrder.verify(commandRegistry).getRegisteredCommandNames();
        inOrder.verify(commandRegistry).isCommandAvailable("help");
        inOrder.verify(commandRegistry).validate("help", new String[0]);
        inOrder.verify(commandRegistry).execute("help", new String[0]);
        inOrder.verify(server).flush(Mockito.any(SuccessResponseMessage.class));
    }

    @Test
    void shouldRespondToNullCommand() throws Exception {
        when(cr.readLine())
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                executor.shutdown();
                                return "{\"commandName\":\"foo\"}";
                            }
                        });

        executor.run(null);

        ArgumentCaptor<ResponseMessage<String>> messageCaptor =
                ArgumentCaptor.forClass(ResponseMessage.class);
        verify(server).flush(messageCaptor.capture());
        ResponseMessage<String> message = messageCaptor.getValue();
        MatcherAssert.assertThat(message.status, Matchers.equalTo(-1));
    }

    @Test
    void shouldRespondToUnregisteredCommand() throws Exception {
        when(cr.readLine())
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                executor.shutdown();
                                return "{\"command\":\"foo\"}";
                            }
                        });
        when(commandRegistry.getRegisteredCommandNames()).thenReturn(Collections.singleton("bar"));

        executor.run(null);

        verify(commandRegistry).getRegisteredCommandNames();
        verifyNoMoreInteractions(commandRegistry);

        ArgumentCaptor<ResponseMessage<String>> messageCaptor =
                ArgumentCaptor.forClass(ResponseMessage.class);
        verify(server).flush(messageCaptor.capture());
        ResponseMessage<String> message = messageCaptor.getValue();
        MatcherAssert.assertThat(message.status, Matchers.equalTo(-1));
    }

    @Test
    void shouldRespondToInvalidCommand() throws Exception {
        when(cr.readLine())
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                executor.shutdown();
                                return "{\"command\":\"foo\"}";
                            }
                        });
        when(commandRegistry.validate(Mockito.anyString(), Mockito.any(String[].class)))
                .thenReturn(false);
        when(commandRegistry.getRegisteredCommandNames()).thenReturn(Collections.singleton("foo"));
        when(commandRegistry.isCommandAvailable(Mockito.anyString())).thenReturn(true);

        executor.run(null);

        InOrder inOrder = inOrder(commandRegistry);
        inOrder.verify(commandRegistry).getRegisteredCommandNames();
        inOrder.verify(commandRegistry).isCommandAvailable("foo");
        inOrder.verify(commandRegistry).validate("foo", new String[0]);
        verifyNoMoreInteractions(commandRegistry);

        ArgumentCaptor<ResponseMessage<String>> messageCaptor =
                ArgumentCaptor.forClass(ResponseMessage.class);
        verify(server).flush(messageCaptor.capture());
        ResponseMessage<String> message = messageCaptor.getValue();
        MatcherAssert.assertThat(message.status, Matchers.equalTo(-1));
    }

    @Test
    void shouldRespondToUnavailableCommand() throws Exception {
        when(cr.readLine())
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                executor.shutdown();
                                return "{\"command\":\"foo\"}";
                            }
                        });
        when(commandRegistry.getRegisteredCommandNames()).thenReturn(Collections.singleton("foo"));
        when(commandRegistry.isCommandAvailable(Mockito.anyString())).thenReturn(false);

        executor.run(null);

        InOrder inOrder = inOrder(commandRegistry);
        inOrder.verify(commandRegistry).getRegisteredCommandNames();
        inOrder.verify(commandRegistry).isCommandAvailable("foo");
        verifyNoMoreInteractions(commandRegistry);

        ArgumentCaptor<ResponseMessage<String>> messageCaptor =
                ArgumentCaptor.forClass(ResponseMessage.class);
        verify(server).flush(messageCaptor.capture());
        ResponseMessage<String> message = messageCaptor.getValue();
        MatcherAssert.assertThat(message.status, Matchers.equalTo(-1));
    }

    @Test
    void shouldReportInvalidJSONExceptions() throws Exception {
        when(cr.readLine())
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                executor.shutdown();
                                return "{\"command\":\"help}";
                            }
                        });

        executor.run(null);

        ArgumentCaptor<CommandExceptionResponseMessage> messageCaptor =
                ArgumentCaptor.forClass(CommandExceptionResponseMessage.class);
        verify(server).flush(messageCaptor.capture());
        ResponseMessage<String> message = messageCaptor.getValue();
        MatcherAssert.assertThat(message.status, Matchers.equalTo(-2));

        verifyZeroInteractions(commandRegistry);

        ArgumentCaptor<Exception> logCaptor = ArgumentCaptor.forClass(Exception.class);
        verify(logger).warn(logCaptor.capture());
        MatcherAssert.assertThat(
                ExceptionUtils.getStackTrace(logCaptor.getValue()),
                Matchers.stringContainsInOrder(
                        JsonSyntaxException.class.getName(),
                        MalformedJsonException.class.getName()));
    }
}
