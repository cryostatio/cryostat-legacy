/*-
 * #%L
 * Cryostat
 * %%
 * Copyright (C) 2020 - 2021 The Cryostat Authors
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
package io.cryostat.commands.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.cryostat.commands.CommandRegistry;
import io.cryostat.commands.SerializableCommand;
import io.cryostat.commands.SerializableCommandRegistry;
import io.cryostat.core.tui.ClientWriter;

@ExtendWith(MockitoExtension.class)
class HelpCommandTest {

    HelpCommand command;
    @Mock ClientWriter cw;
    @Mock CommandRegistry registry;
    @Mock SerializableCommandRegistry serializableRegistry;

    @BeforeEach
    void setup() {
        command = new HelpCommand(cw, () -> registry, () -> serializableRegistry);
    }

    @Test
    void shouldBeNamedHelp() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("help"));
    }

    @Test
    void shouldExpectNoArgs() {
        assertDoesNotThrow(() -> command.validate(new String[0]));
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldNotExpectArgs() {
        Exception e =
                assertThrows(
                        FailedValidationException.class, () -> command.validate(new String[1]));
        verify(cw).println("No arguments expected");
    }

    @Test
    void shouldBeAvailable() {
        assertTrue(command.isAvailable());
    }

    @Test
    void shouldPrintAvailableCommandNames() throws Exception {
        Set<String> names = new HashSet<>(Arrays.asList("foo", "bar"));

        when(registry.getAvailableCommandNames()).thenReturn(names);
        command.execute(new String[0]);

        InOrder inOrder = inOrder(cw);
        inOrder.verify(cw).println("Available commands:");
        inOrder.verify(cw).println("\tbar");
        inOrder.verify(cw).println("\tfoo");
    }

    @Test
    void shouldReturnListOutput() throws Exception {
        List<String> names = Arrays.asList("bar", "foo");

        when(serializableRegistry.getAvailableCommandNames()).thenReturn(new HashSet<>(names));
        SerializableCommand.Output<?> out = command.serializableExecute(new String[0]);
        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.ListOutput.class));
        MatcherAssert.assertThat(out.getPayload(), Matchers.equalTo(names));
    }
}
