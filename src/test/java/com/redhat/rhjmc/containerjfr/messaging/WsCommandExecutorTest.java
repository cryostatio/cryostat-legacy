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
package com.redhat.rhjmc.containerjfr.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.MalformedJsonException;

import com.redhat.rhjmc.containerjfr.MainModule;
import com.redhat.rhjmc.containerjfr.commands.Command.ExceptionOutput;
import com.redhat.rhjmc.containerjfr.commands.Command.FailureOutput;
import com.redhat.rhjmc.containerjfr.commands.Command.ListOutput;
import com.redhat.rhjmc.containerjfr.commands.Command.MapOutput;
import com.redhat.rhjmc.containerjfr.commands.Command.Output;
import com.redhat.rhjmc.containerjfr.commands.Command.StringOutput;
import com.redhat.rhjmc.containerjfr.commands.Command.SuccessOutput;
import com.redhat.rhjmc.containerjfr.commands.CommandRegistry;
import com.redhat.rhjmc.containerjfr.commands.internal.FailedValidationException;
import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.tui.ClientReader;

@ExtendWith(MockitoExtension.class)
class WsCommandExecutorTest {

    WsCommandExecutor executor;
    @Mock MessagingServer server;
    @Mock Logger logger;
    @Mock ClientReader cr;
    @Mock CommandRegistry commandRegistry;
    Gson gson = MainModule.provideGson(logger);

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
        when(commandRegistry.execute(Mockito.anyString(), Mockito.any(String[].class)))
                .thenReturn(new SuccessOutput());

        executor.run();

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
        when(commandRegistry.execute(Mockito.anyString(), Mockito.any(String[].class)))
                .thenReturn(new SuccessOutput());

        executor.run();

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
        when(commandRegistry.execute(Mockito.anyString(), Mockito.any(String[].class)))
                .thenReturn(new FailureOutput("some reason"));

        executor.run();

        ArgumentCaptor<FailureResponseMessage> response =
                ArgumentCaptor.forClass(FailureResponseMessage.class);
        InOrder inOrder = inOrder(commandRegistry, server);
        inOrder.verify(commandRegistry).getRegisteredCommandNames();
        inOrder.verify(commandRegistry).isCommandAvailable("help");
        inOrder.verify(commandRegistry).validate("help", new String[] {"hello", "world"});
        inOrder.verify(commandRegistry).execute("help", new String[] {"hello", "world"});
        inOrder.verify(server).flush(response.capture());

        MatcherAssert.assertThat(response.getValue().status, Matchers.equalTo(-2));
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
        when(commandRegistry.execute(Mockito.anyString(), Mockito.any(String[].class)))
                .thenReturn(new StringOutput("some reason"));

        executor.run();

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
        when(commandRegistry.execute(Mockito.anyString(), Mockito.any(String[].class)))
                .thenReturn(new ListOutput<Integer>(Arrays.asList(3, 1, 4, 1, 5, 9)));

        executor.run();

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
        when(commandRegistry.execute(Mockito.anyString(), Mockito.any(String[].class)))
                .thenReturn(new MapOutput<String, String>(Map.of("foo", "bar")));

        executor.run();

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
        when(commandRegistry.execute(Mockito.anyString(), Mockito.any(String[].class)))
                .thenReturn(new ExceptionOutput(new IOException("broken pipe")));

        executor.run();

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
        when(commandRegistry.execute(Mockito.anyString(), Mockito.any(String[].class)))
                .thenReturn(
                        new Output<Void>() {
                            @Override
                            public Void getPayload() {
                                return null;
                            }
                        });

        executor.run();

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

    @ParameterizedTest
    @ValueSource(strings = {"", "\t", "  ", "\n", "null", " null ", "null\n", "\r\n"})
    @NullSource
    void shouldRespondToBlankLines(String s) throws Exception {
        when(cr.readLine())
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                executor.shutdown();
                                return s;
                            }
                        });

        executor.run();

        verifyZeroInteractions(commandRegistry);
        verify(server).flush(Mockito.any(MalformedMessageResponseMessage.class));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "foo",
                "hi",
                "\0",
                "nil",
                "0",
                "-1",
                "{command:foo",
                "{command;foo}",
                "command:foo"
            })
    void shouldRespondToMalformedJson(String s) throws Exception {
        when(cr.readLine())
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                executor.shutdown();
                                return s;
                            }
                        });

        executor.run();

        verifyZeroInteractions(commandRegistry);

        ArgumentCaptor<CommandExceptionResponseMessage> messageCaptor =
                ArgumentCaptor.forClass(CommandExceptionResponseMessage.class);
        verify(server).flush(messageCaptor.capture());
        CommandExceptionResponseMessage response = messageCaptor.getValue();
        MatcherAssert.assertThat(response.commandName, Matchers.equalTo(s));
        MatcherAssert.assertThat(response.status, Matchers.equalTo(-2));
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
        when(commandRegistry.execute(Mockito.anyString(), Mockito.any(String[].class)))
                .thenReturn(new SuccessOutput());

        executor.run();

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

        executor.run();

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

        executor.run();

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
        doThrow(new FailedValidationException("bar could not be found"))
                .when(commandRegistry)
                .validate(eq("foo"), any(String[].class));

        when(commandRegistry.getRegisteredCommandNames()).thenReturn(Collections.singleton("foo"));
        when(commandRegistry.isCommandAvailable(Mockito.anyString())).thenReturn(true);

        executor.run();

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
        MatcherAssert.assertThat(
                message.payload,
                Matchers.equalTo("Could not validate \"foo\" command: bar could not be found"));
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

        executor.run();

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

        executor.run();

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

    @Test
    void shouldMirrorIdWhenProvided() throws Exception {
        when(cr.readLine())
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                executor.shutdown();
                                return "{\"id\":\"msgId\",\"command\":\"help\",\"args\":[]}";
                            }
                        });
        when(commandRegistry.getRegisteredCommandNames()).thenReturn(Collections.singleton("help"));
        when(commandRegistry.isCommandAvailable(Mockito.anyString())).thenReturn(true);
        when(commandRegistry.execute(Mockito.anyString(), Mockito.any(String[].class)))
                .thenReturn(new SuccessOutput());

        executor.run();

        InOrder inOrder = inOrder(commandRegistry, server);
        inOrder.verify(commandRegistry).getRegisteredCommandNames();
        inOrder.verify(commandRegistry).isCommandAvailable("help");
        inOrder.verify(commandRegistry).validate("help", new String[0]);
        inOrder.verify(commandRegistry).execute("help", new String[0]);

        ArgumentCaptor<SuccessResponseMessage<Void>> msgCaptor =
                ArgumentCaptor.forClass(SuccessResponseMessage.class);
        inOrder.verify(server).flush(msgCaptor.capture());
        MatcherAssert.assertThat(msgCaptor.getValue().id, Matchers.equalTo("msgId"));
    }

    @Test
    void shouldUseNullIdWhenNotProvided() throws Exception {
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
        when(commandRegistry.execute(Mockito.anyString(), Mockito.any(String[].class)))
                .thenReturn(new SuccessOutput());

        executor.run();

        InOrder inOrder = inOrder(commandRegistry, server);
        inOrder.verify(commandRegistry).getRegisteredCommandNames();
        inOrder.verify(commandRegistry).isCommandAvailable("help");
        inOrder.verify(commandRegistry).validate("help", new String[0]);
        inOrder.verify(commandRegistry).execute("help", new String[0]);

        ArgumentCaptor<SuccessResponseMessage<Void>> msgCaptor =
                ArgumentCaptor.forClass(SuccessResponseMessage.class);
        inOrder.verify(server).flush(msgCaptor.capture());
        MatcherAssert.assertThat(msgCaptor.getValue().id, Matchers.nullValue());
    }
}
