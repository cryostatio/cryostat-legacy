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
package com.redhat.rhjmc.containerjfr.tui.tty;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.NoSuchElementException;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.redhat.rhjmc.containerjfr.TestBase;
import com.redhat.rhjmc.containerjfr.commands.CommandRegistry;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.tui.ClientReader;

@ExtendWith(MockitoExtension.class)
class InteractiveShellExecutorTest extends TestBase {

    InteractiveShellExecutor executor;
    @Mock ClientReader mockClientReader;
    @Mock CommandRegistry mockRegistry;
    @Mock JFRConnection mockConnection;

    @BeforeEach
    void setup() {
        executor =
                new InteractiveShellExecutor(
                        mockClientReader, mockClientWriter, () -> mockRegistry);
    }

    @Test
    void shouldPrintCommandExceptions() throws Exception {
        verifyZeroInteractions(mockClientReader);
        verifyZeroInteractions(mockRegistry);

        when(mockClientReader.readLine()).thenReturn("help").thenReturn("exit");
        doThrow(UnsupportedOperationException.class)
                .when(mockRegistry)
                .execute(eq("help"), any(String[].class));

        executor.run(null);

        MatcherAssert.assertThat(
                stdout(),
                Matchers.equalTo(
                        "> \n\"help\" \"[]\"\nhelp operation failed due to null\njava.lang.UnsupportedOperationException\n\n> \n\"exit\" \"[]\"\n"));

        verify(mockRegistry).validate("help", new String[0]);
        verify(mockRegistry).validate("exit", new String[0]);
        verify(mockClientReader).close();

        InOrder inOrder = inOrder(mockRegistry);
        inOrder.verify(mockRegistry).execute("help", new String[0]);
        inOrder.verify(mockRegistry).execute("exit", new String[0]);

        verifyNoMoreInteractions(mockClientReader);
        verifyNoMoreInteractions(mockRegistry);
    }

    @Test
    void shouldHandleClientReaderExceptions() throws Exception {
        verifyZeroInteractions(mockClientReader);
        verifyZeroInteractions(mockRegistry);

        when(mockClientReader.readLine()).thenThrow(NullPointerException.class);

        executor.run(null);

        MatcherAssert.assertThat(
                stdout(), Matchers.equalTo("> java.lang.NullPointerException\n\n"));
        verify(mockClientReader).readLine();
        verify(mockClientReader).close();

        verifyNoMoreInteractions(mockClientReader);
        verifyNoMoreInteractions(mockRegistry);
    }

    @Test
    void shouldHandleClientReaderNoSuchElementExceptions() throws Exception {
        verifyZeroInteractions(mockClientReader);
        verifyZeroInteractions(mockRegistry);

        when(mockClientReader.readLine()).thenThrow(NoSuchElementException.class);

        executor.run(null);

        MatcherAssert.assertThat(stdout(), Matchers.equalTo("> \n\"exit\" \"[]\"\n"));

        verify(mockClientReader).readLine();
        verify(mockRegistry).validate("exit", new String[0]);
        verify(mockRegistry).execute("exit", new String[0]);
        verify(mockClientReader).close();

        verifyNoMoreInteractions(mockClientReader);
        verifyNoMoreInteractions(mockRegistry);
    }

    @Test
    void shouldHandleClientReaderReturnsNull() throws Exception {
        verifyZeroInteractions(mockClientReader);
        verifyZeroInteractions(mockRegistry);

        when(mockClientReader.readLine()).thenReturn(null);

        executor.run(null);

        MatcherAssert.assertThat(stdout(), Matchers.equalTo("> \n\"exit\" \"[]\"\n"));

        verify(mockClientReader).readLine();
        verify(mockRegistry).validate("exit", new String[0]);
        verify(mockRegistry).execute("exit", new String[0]);
        verify(mockClientReader).close();

        verifyNoMoreInteractions(mockClientReader);
        verifyNoMoreInteractions(mockRegistry);
    }
}
