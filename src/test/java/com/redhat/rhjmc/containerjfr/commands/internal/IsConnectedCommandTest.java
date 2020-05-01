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

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;

@ExtendWith(MockitoExtension.class)
class IsConnectedCommandTest {

    IsConnectedCommand command;
    @Mock ClientWriter cw;
    @Mock JFRConnection conn;

    @BeforeEach
    void setup() {
        command = new IsConnectedCommand(cw);
    }

    @Test
    void shouldBeNamedPing() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("is-connected"));
    }

    @Test
    void shouldExpectNoArgs() {
        MatcherAssert.assertThat(command.validate(new String[0]), Matchers.is(true));
    }

    @ParameterizedTest
    @ValueSource(
            ints = {
                1, 2,
            })
    void shouldNotExpectArgs(int argc) {
        MatcherAssert.assertThat(command.validate(new String[argc]), Matchers.is(false));
        verify(cw).println("No arguments expected");
    }

    @Test
    void shouldBeAvailable() {
        MatcherAssert.assertThat(command.isAvailable(), Matchers.is(true));
    }

    @Test
    void shouldReturnStringOutput() {
        SerializableCommand.Output<?> outA = command.serializableExecute(new String[0]);
        MatcherAssert.assertThat(outA, Matchers.instanceOf(SerializableCommand.StringOutput.class));
        MatcherAssert.assertThat(outA.getPayload(), Matchers.equalTo("false"));

        when(conn.getHost()).thenReturn("someHost");
        when(conn.getPort()).thenReturn(1234);
        command.connectionChanged(conn);
        SerializableCommand.Output<?> outB = command.serializableExecute(new String[0]);
        MatcherAssert.assertThat(outB, Matchers.instanceOf(SerializableCommand.StringOutput.class));
        MatcherAssert.assertThat(outB.getPayload(), Matchers.equalTo("someHost:1234"));

        command.connectionChanged(null);
        SerializableCommand.Output<?> outC = command.serializableExecute(new String[0]);
        MatcherAssert.assertThat(outC, Matchers.instanceOf(SerializableCommand.StringOutput.class));
        MatcherAssert.assertThat(outC.getPayload(), Matchers.equalTo("false"));
    }

    @Test
    void shouldEchoStatus() throws Exception {
        command.execute(new String[0]);

        when(conn.getHost()).thenReturn("someHost");
        when(conn.getPort()).thenReturn(1234);
        command.connectionChanged(conn);
        command.execute(new String[0]);

        command.connectionChanged(null);
        command.execute(new String[0]);

        InOrder inOrder = inOrder(cw);
        inOrder.verify(cw).println("\tDisconnected");
        inOrder.verify(cw).println("\tsomeHost:1234");
        inOrder.verify(cw).println("\tDisconnected");
    }
}
