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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collections;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnectionToolkit;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.net.ConnectionListener;

@ExtendWith(MockitoExtension.class)
class ConnectCommandTest {

    ConnectCommand command;
    @Mock ClientWriter cw;
    @Mock ConnectionListener listener;
    @Mock DisconnectCommand disconnect;
    @Mock JFRConnectionToolkit connectionToolkit;

    @BeforeEach
    void setup() {
        command =
                new ConnectCommand(
                        cw, Collections.singleton(listener), disconnect, connectionToolkit);
    }

    @Test
    void shouldBeAvailable() {
        assertTrue(command.isAvailable());
    }

    @Test
    void shouldBeNamedConnect() {
        assertThat(command.getName(), Matchers.equalTo("connect"));
    }

    @Test
    void shouldExpectOneArg() {
        assertFalse(command.validate(new String[0]));
        assertTrue(command.validate(new String[] {"foo"}));
        assertFalse(command.validate(new String[2]));
        verify(cw, Mockito.times(2))
                .println("Expected one argument: hostname:port, ip:port, or JMX service URL");
    }

    @ParameterizedTest
    @ValueSource(strings = {"some.host:", ":", "some.host:abc"})
    void shouldNotValidateInvalidIdentifiers(String id) {
        assertFalse(command.validate(new String[] {id}), id);
        verify(cw).println(id + " is an invalid connection specifier");
    }

    @Test
    void shouldNotValidateNull() {
        assertFalse(command.validate(new String[] {null}));
        verify(cw).println("Expected one argument: hostname:port, ip:port, or JMX service URL");
    }

    @Test
    void shouldNotValidateEmptyString() {
        assertFalse(command.validate(new String[] {" "}));
        verify(cw).println("Expected one argument: hostname:port, ip:port, or JMX service URL");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "some.host",
                "some.host:8181",
            })
    void shouldValidateValidIdentifiers(String id) {
        assertTrue(command.validate(new String[] {id}));
    }

    @Test
    void shouldConnectViaConnectionToolkit() throws Exception {
        verifyZeroInteractions(listener);
        verifyZeroInteractions(connectionToolkit);
        verifyZeroInteractions(disconnect);

        JFRConnection mockConnection = mock(JFRConnection.class);
        when(connectionToolkit.connect(Mockito.anyString(), Mockito.anyInt()))
                .thenReturn(mockConnection);

        command.execute(new String[] {"foo:5"});

        ArgumentCaptor<String> hostCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> portCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<JFRConnection> connectionCaptor =
                ArgumentCaptor.forClass(JFRConnection.class);

        verify(listener).connectionChanged(connectionCaptor.capture());
        verify(connectionToolkit).connect(hostCaptor.capture(), portCaptor.capture());
        verify(disconnect).execute(Mockito.any());

        assertThat(hostCaptor.getValue(), equalTo("foo"));
        assertThat(portCaptor.getValue(), equalTo(5));
        assertThat(connectionCaptor.getValue(), sameInstance(mockConnection));
    }

    @Test
    void shouldUseDefaultPortIfUnspecified() throws Exception {
        verifyZeroInteractions(listener);
        verifyZeroInteractions(connectionToolkit);
        verifyZeroInteractions(disconnect);

        JFRConnection mockConnection = mock(JFRConnection.class);
        when(connectionToolkit.connect(Mockito.anyString(), Mockito.anyInt()))
                .thenReturn(mockConnection);

        command.execute(new String[] {"foo"});

        ArgumentCaptor<String> hostCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> portCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<JFRConnection> connectionCaptor =
                ArgumentCaptor.forClass(JFRConnection.class);

        verify(listener).connectionChanged(connectionCaptor.capture());
        verify(connectionToolkit).connect(hostCaptor.capture(), portCaptor.capture());
        verify(disconnect).execute(Mockito.any());

        assertThat(hostCaptor.getValue(), equalTo("foo"));
        assertThat(portCaptor.getValue(), equalTo(JFRConnection.DEFAULT_PORT));
        assertThat(connectionCaptor.getValue(), sameInstance(mockConnection));
    }

    @Test
    void shouldReturnStringOutput() throws Exception {
        verifyZeroInteractions(listener);
        verifyZeroInteractions(connectionToolkit);
        verifyZeroInteractions(disconnect);

        JFRConnection mockConnection = mock(JFRConnection.class);
        when(connectionToolkit.connect(Mockito.anyString(), Mockito.anyInt()))
                .thenReturn(mockConnection);

        SerializableCommand.Output<?> out = command.serializableExecute(new String[] {"foo"});
        assertThat(out, instanceOf(SerializableCommand.StringOutput.class));
        MatcherAssert.assertThat(
                ((SerializableCommand.StringOutput) out).getPayload(), equalTo("foo"));

        ArgumentCaptor<String> hostCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> portCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<JFRConnection> connectionCaptor =
                ArgumentCaptor.forClass(JFRConnection.class);

        verify(listener).connectionChanged(connectionCaptor.capture());
        verify(connectionToolkit).connect(hostCaptor.capture(), portCaptor.capture());
        verify(disconnect).execute(Mockito.any());

        assertThat(hostCaptor.getValue(), equalTo("foo"));
        assertThat(portCaptor.getValue(), equalTo(JFRConnection.DEFAULT_PORT));
        assertThat(connectionCaptor.getValue(), sameInstance(mockConnection));
    }

    @Test
    void shouldReturnExceptionOutput() throws Exception {
        verifyZeroInteractions(listener);
        verifyZeroInteractions(connectionToolkit);
        verifyZeroInteractions(disconnect);

        when(connectionToolkit.connect(Mockito.anyString(), Mockito.anyInt()))
                .thenThrow(IOException.class);

        SerializableCommand.Output<?> out = command.serializableExecute(new String[] {"foo"});
        assertThat(out, instanceOf(SerializableCommand.ExceptionOutput.class));
        MatcherAssert.assertThat(
                ((SerializableCommand.ExceptionOutput) out).getPayload(), equalTo("IOException: "));

        ArgumentCaptor<String> hostCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Integer> portCaptor = ArgumentCaptor.forClass(Integer.class);

        verifyNoMoreInteractions(listener);
        verify(connectionToolkit).connect(hostCaptor.capture(), portCaptor.capture());
        verify(disconnect).execute(Mockito.any());

        assertThat(hostCaptor.getValue(), equalTo("foo"));
        assertThat(portCaptor.getValue(), equalTo(JFRConnection.DEFAULT_PORT));
    }
}
