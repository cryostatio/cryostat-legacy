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
package io.cryostat.tui.tcp;

import static org.mockito.Mockito.*;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.Semaphore;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.cryostat.core.log.Logger;

@ExtendWith(MockitoExtension.class)
class SocketClientReaderWriterTest {
    @Mock Logger logger;

    @Mock Semaphore semaphore;

    @Mock Socket socket;

    @Mock Scanner scanner;

    @Mock OutputStreamWriter writer;

    SocketClientReaderWriter scrw;

    @BeforeEach
    void setup() {
        scrw = new SocketClientReaderWriter(logger, semaphore, socket, scanner, writer);
    }

    @Test
    void shouldClose() throws Exception {
        scrw.close();

        verify(scanner).close();
        verify(writer).close();
        verify(socket).close();
    }

    @Test
    void shouldReadLine() throws Exception {
        String result = "read line result";
        when(scanner.nextLine()).thenReturn(result);

        MatcherAssert.assertThat(scrw.readLine(), Matchers.equalTo(result));

        verify(semaphore).acquire();
        verify(semaphore).release();
    }

    @Test
    void shouldReadLineWarnOnException() throws Exception {
        InterruptedException e = new InterruptedException();
        doThrow(e).when(semaphore).acquire();

        scrw.readLine();

        verify(logger).warn(e);
    }

    @Test
    void shouldReadLineReleaseOnException() throws Exception {
        RuntimeException e = new RuntimeException();
        when(scanner.nextLine()).thenThrow(e);

        Assertions.assertThrows(RuntimeException.class, scrw::readLine);

        verify(semaphore).acquire();
        verify(semaphore).release();
    }

    @Test
    void shouldPrint() throws Exception {
        String content = "printed content";

        scrw.print(content);

        verify(writer).write(content);
        verify(writer).flush();
    }

    @Test
    void shouldPrintWarnOnException() throws Exception {
        IOException e = new IOException();
        doThrow(e).when(writer).flush();

        scrw.print("some content");

        verify(logger).warn(e);
    }

    @Test
    void shouldPrintReleaseOnException() throws Exception {
        IOException e = new IOException();
        doThrow(e).when(writer).flush();

        scrw.print("some content");

        verify(semaphore).acquire();
        verify(semaphore).release();
    }
}
