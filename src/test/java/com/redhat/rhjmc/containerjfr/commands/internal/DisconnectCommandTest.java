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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import java.util.Collections;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.net.ConnectionListener;

@ExtendWith(MockitoExtension.class)
class DisconnectCommandTest {

    DisconnectCommand command;
    @Mock ConnectionListener listener;
    @Mock JFRConnection connection;
    @Mock ClientWriter cw;

    @BeforeEach
    void setup() {
        command = new DisconnectCommand(() -> Collections.singleton(listener), cw);
        command.connectionChanged(connection);
    }

    @Test
    void shouldBeNamedDisconnect() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("disconnect"));
    }

    @Test
    void shouldExpectZeroArgs() {
        assertTrue(command.validate(new String[0]));
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldNotExpectArgs() {
        assertFalse(command.validate(new String[1]));
        verify(cw).println("No arguments expected");
    }

    @Test
    void shouldNotifyListeners() throws Exception {
        verify(listener, never()).connectionChanged(Mockito.any());
        verify(connection, never()).disconnect();
        command.execute(new String[0]);
        verify(listener).connectionChanged(null);
        verify(connection).disconnect();
        verifyNoMoreInteractions(listener);
        verifyNoMoreInteractions(connection);
    }

    @Test
    void shouldHandleNoPreviousConnection() throws Exception {
        verify(listener, never()).connectionChanged(Mockito.any());
        verify(connection, never()).disconnect();
        command.connectionChanged(null);
        command.execute(new String[0]);
        verify(listener).connectionChanged(null);
        verifyNoMoreInteractions(listener);
        verifyNoMoreInteractions(connection);
    }

    @Test
    void shouldHandleDoubleDisconnect() throws Exception {
        verify(listener, never()).connectionChanged(Mockito.any());
        verify(connection, never()).disconnect();
        command.execute(new String[0]);
        command.connectionChanged(null);
        command.execute(new String[0]);
        verify(listener, Mockito.times(2)).connectionChanged(null);
        verify(connection, Mockito.times(1)).disconnect();
        verify(cw).println("No active connection");
        verifyNoMoreInteractions(listener);
        verifyNoMoreInteractions(connection);
    }

    @Test
    void shouldReturnSuccessOutput() throws Exception {
        SerializableCommand.Output out = command.serializableExecute(new String[0]);
        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.SuccessOutput.class));
    }
}
