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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.redhat.rhjmc.containerjfr.commands.Command;
import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.net.internal.reports.ReportService;

@ExtendWith(MockitoExtension.class)
class DeleteSavedRecordingCommandTest implements ValidatesRecordingName {

    DeleteSavedRecordingCommand command;
    @Mock ClientWriter cw;
    @Mock FileSystem fs;
    @Mock Path recordingsPath;
    @Mock ReportService reportService;

    @Override
    public Command commandForValidationTesting() {
        return command;
    }

    @Override
    public List<String> argumentSignature() {
        return List.of(RECORDING_NAME);
    }

    @BeforeEach
    void setup() {
        command = new DeleteSavedRecordingCommand(cw, fs, recordingsPath, reportService);
    }

    @Test
    void shouldBeNamedDeleteSaved() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("delete-saved"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void shouldNotValidateWrongArgCounts(int count) {
        Assertions.assertFalse(command.validate(new String[count]));
        verify(cw).println("Expected one argument: recording name");
    }

    @Test
    void shouldBeAvailableIfRecordingsPathIsDirectory() {
        when(fs.isDirectory(Mockito.any())).thenReturn(true);

        Assertions.assertTrue(command.isAvailable());

        verify(fs).isDirectory(recordingsPath);
    }

    @Test
    void shouldNotBeAvailableIfRecordingsPathIsNotDirectory() {
        when(fs.isDirectory(Mockito.any())).thenReturn(false);

        Assertions.assertFalse(command.isAvailable());

        verify(fs).isDirectory(recordingsPath);
    }

    @Test
    void shouldExecuteAndPrintMessageOnSuccess() throws Exception {
        when(fs.deleteIfExists(Mockito.any())).thenReturn(true);
        Path filePath = mock(Path.class);
        when(recordingsPath.resolve(Mockito.anyString())).thenReturn(filePath);

        command.execute(new String[] {"foo"});

        verify(recordingsPath).resolve("foo");
        verify(fs).deleteIfExists(filePath);
        verify(reportService).delete("foo");
        verify(cw).println("\"foo\" deleted");
    }

    @Test
    void shouldExecuteAndPrintMessageOnFailure() throws Exception {
        when(fs.deleteIfExists(Mockito.any())).thenReturn(false);
        Path filePath = mock(Path.class);
        when(recordingsPath.resolve(Mockito.anyString())).thenReturn(filePath);

        command.execute(new String[] {"foo"});

        verify(recordingsPath).resolve("foo");
        verify(fs).deleteIfExists(filePath);
        verify(cw).println("Could not delete saved recording \"foo\"");
    }

    @Test
    void shouldExecuteAndReturnSerializedSuccess() throws Exception {
        when(fs.deleteIfExists(Mockito.any())).thenReturn(true);
        Path filePath = mock(Path.class);
        when(recordingsPath.resolve(Mockito.anyString())).thenReturn(filePath);

        SerializableCommand.Output<?> out = command.serializableExecute(new String[] {"foo"});

        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.SuccessOutput.class));

        verify(recordingsPath).resolve("foo");
        verify(fs).deleteIfExists(filePath);
        verify(reportService).delete("foo");
    }

    @Test
    void shouldExecuteAndReturnSerializedFailure() throws Exception {
        when(fs.deleteIfExists(Mockito.any())).thenReturn(false);
        Path filePath = mock(Path.class);
        when(recordingsPath.resolve(Mockito.anyString())).thenReturn(filePath);

        SerializableCommand.Output<?> out = command.serializableExecute(new String[] {"foo"});

        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.FailureOutput.class));
        MatcherAssert.assertThat(
                ((SerializableCommand.FailureOutput) out).getPayload(),
                Matchers.equalTo("Could not delete saved recording \"foo\""));

        verify(recordingsPath).resolve("foo");
        verify(fs).deleteIfExists(filePath);
    }

    @Test
    void shouldExecuteAndReturnSerializedException() throws Exception {
        when(fs.deleteIfExists(Mockito.any())).thenThrow(IOException.class);
        Path filePath = mock(Path.class);
        when(recordingsPath.resolve(Mockito.anyString())).thenReturn(filePath);

        SerializableCommand.Output<?> out = command.serializableExecute(new String[] {"foo"});

        MatcherAssert.assertThat(
                out, Matchers.instanceOf(SerializableCommand.ExceptionOutput.class));

        verify(recordingsPath).resolve("foo");
        verify(fs).deleteIfExists(filePath);
    }
}
