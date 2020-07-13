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
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.jmc.serialization.SavedRecordingDescriptor;
import com.redhat.rhjmc.containerjfr.net.web.WebServer;

@ExtendWith(MockitoExtension.class)
class ListSavedRecordingsCommandTest {

    @Mock ClientWriter cw;
    @Mock FileSystem fs;
    @Mock Path recordingsPath;
    @Mock WebServer exporter;
    ListSavedRecordingsCommand command;

    @BeforeEach
    void setup() {
        command = new ListSavedRecordingsCommand(cw, fs, recordingsPath, exporter);
    }

    @Test
    void shouldBeNamedListSaved() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("list-saved"));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void shouldNotValidateIncorrectArgc(int argc) {
        Exception e =
                Assertions.assertThrows(
                        FailedValidationException.class, () -> command.validate(new String[argc]));
        String errorMessage = "No arguments expected";
        verify(cw).println(errorMessage);
        MatcherAssert.assertThat(e.getMessage(), Matchers.equalTo(errorMessage));
    }

    @Test
    void shouldExpectNoArgs() {
        Assertions.assertDoesNotThrow(() -> command.validate(new String[0]));
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldBeAvailableIfRecordingsPathIsDirectory() {
        when(fs.isDirectory(Mockito.any())).thenReturn(true);
        Assertions.assertTrue(command.isAvailable());
        verify(fs).isDirectory(recordingsPath);
    }

    @Test
    void shouldBeUnavailableIfRecordingsPathIsNotDirectory() {
        when(fs.isDirectory(Mockito.any())).thenReturn(false);
        Assertions.assertFalse(command.isAvailable());
        verify(fs).isDirectory(recordingsPath);
    }

    @Test
    void shouldExecuteAndPrintMessageIfNoSavedRecordingsFound() throws Exception {
        when(fs.listDirectoryChildren(recordingsPath)).thenReturn(Collections.emptyList());

        command.execute(new String[0]);

        InOrder inOrder = inOrder(cw);
        inOrder.verify(cw).println("Saved recordings:");
        inOrder.verify(cw).println("\tNone");
    }

    @Test
    void shouldExecuteAndPrintSavedRecordings() throws Exception {
        when(fs.listDirectoryChildren(recordingsPath)).thenReturn(Arrays.asList("foo", "bar"));

        command.execute(new String[0]);

        InOrder inOrder = inOrder(cw);
        inOrder.verify(cw).println("Saved recordings:");
        inOrder.verify(cw).println(Mockito.contains("getName\t\tfoo"));
        inOrder.verify(cw).println(Mockito.contains("getName\t\tbar"));
    }

    @Test
    void shouldPrintDownloadURL() throws Exception {
        when(fs.listDirectoryChildren(recordingsPath)).thenReturn(Arrays.asList("foo", "bar"));
        when(exporter.getArchivedDownloadURL(Mockito.anyString()))
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                return String.format(
                                        "http://example.com:1234/api/v1/recordings/%s",
                                        invocation.getArguments()[0]);
                            }
                        });

        command.execute(new String[0]);

        InOrder inOrder = inOrder(cw);
        inOrder.verify(cw).println("Saved recordings:");
        inOrder.verify(cw)
                .println(
                        Mockito.contains(
                                "\tgetDownloadUrl\t\thttp://example.com:1234/api/v1/recordings/foo"));
        inOrder.verify(cw)
                .println(
                        Mockito.contains(
                                "\tgetDownloadUrl\t\thttp://example.com:1234/api/v1/recordings/bar"));
    }

    @Test
    void shouldPrintReportURL() throws Exception {
        when(fs.listDirectoryChildren(recordingsPath)).thenReturn(Arrays.asList("foo", "bar"));
        when(exporter.getArchivedReportURL(Mockito.anyString()))
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                return String.format(
                                        "http://example.com:1234/api/v1/reports/%s",
                                        invocation.getArguments()[0]);
                            }
                        });

        command.execute(new String[0]);

        InOrder inOrder = inOrder(cw);
        inOrder.verify(cw).println("Saved recordings:");
        inOrder.verify(cw)
                .println(
                        Mockito.contains(
                                "\tgetReportUrl\t\thttp://example.com:1234/api/v1/reports/foo"));
        inOrder.verify(cw)
                .println(
                        Mockito.contains(
                                "\tgetReportUrl\t\thttp://example.com:1234/api/v1/reports/bar"));
    }

    @Test
    void shouldExecuteAndReturnSerializedMessageIfNoSavedRecordingsFound() throws Exception {
        when(fs.listDirectoryChildren(recordingsPath)).thenReturn(Collections.emptyList());

        SerializableCommand.Output<?> out = command.serializableExecute(new String[0]);

        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.ListOutput.class));
        MatcherAssert.assertThat(
                ((SerializableCommand.ListOutput) out).getPayload(),
                Matchers.equalTo(Collections.emptyList()));

        verifyZeroInteractions(cw);
    }

    @Test
    void shouldExecuteAndReturnSerializedExceptionMessageIfThrows() throws Exception {
        when(fs.listDirectoryChildren(recordingsPath)).thenThrow(IOException.class);

        SerializableCommand.Output<?> out = command.serializableExecute(new String[0]);

        MatcherAssert.assertThat(
                out, Matchers.instanceOf(SerializableCommand.ExceptionOutput.class));

        verifyZeroInteractions(cw);
    }

    @Test
    void shouldExecuteAndReturnSerializedRecordingInfo() throws Exception {
        when(fs.listDirectoryChildren(recordingsPath)).thenReturn(Arrays.asList("foo", "bar"));
        when(exporter.getArchivedDownloadURL(Mockito.anyString()))
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                return invocation.getArguments()[0] + ".jfr";
                            }
                        });
        when(exporter.getArchivedReportURL(Mockito.anyString()))
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                return "/reports/" + invocation.getArguments()[0] + ".jfr";
                            }
                        });

        SerializableCommand.Output<?> out = command.serializableExecute(new String[0]);

        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.ListOutput.class));
        MatcherAssert.assertThat(
                ((SerializableCommand.ListOutput) out).getPayload(),
                Matchers.equalTo(
                        Arrays.asList(
                                new SavedRecordingDescriptor("foo", "foo.jfr", "/reports/foo.jfr"),
                                new SavedRecordingDescriptor(
                                        "bar", "bar.jfr", "/reports/bar.jfr"))));

        verifyZeroInteractions(cw);
    }
}
