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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

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

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;

@ExtendWith(MockitoExtension.class)
class RecordingOptionsCustomizerCommandTest {

    RecordingOptionsCustomizerCommand command;
    @Mock ClientWriter cw;
    @Mock RecordingOptionsCustomizer customizer;

    @BeforeEach
    void setup() {
        command = new RecordingOptionsCustomizerCommand(cw, customizer);
    }

    @Test
    void shouldBeNamedRecordingOptions() {
        MatcherAssert.assertThat(command.getName(), Matchers.equalTo("recording-option"));
    }

    @Test
    void shouldBeAvailable() {
        Assertions.assertTrue(command.isAvailable());
    }

    @Test
    void shouldNotExpectNoArgs() {
        assertFalse(command.validate(new String[0]));
        verify(cw).println("Expected one argument: recording option name");
    }

    @Test
    void shouldNotExpectTooManyArgs() {
        assertFalse(command.validate(new String[2]));
        verify(cw).println("Expected one argument: recording option name");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "foo",
                "+foo",
                "foo=",
                "-foo=bar",
            })
    void shouldNotValidateMalformedArg(String arg) {
        assertFalse(command.validate(new String[] {arg}));
        verify(cw).println(arg + " is an invalid option string");
    }

    @Test
    void shouldNotValidateUnrecognizedOption() {
        assertFalse(command.validate(new String[] {"someUnknownOption=value"}));
        verify(cw).println("someUnknownOption is an unrecognized or unsupported option");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "toDisk=true",
                "maxAge=10",
                "maxSize=512",
            })
    void shouldKnownValidateKeyValueArg(String arg) {
        assertTrue(command.validate(new String[] {arg}));
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldExpectUnsetArg() {
        assertTrue(command.validate(new String[] {"-toDisk"}));
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldSetMaxAge() throws Exception {
        verifyZeroInteractions(customizer);
        command.execute(new String[] {"maxAge=123"});
        verify(customizer).set(RecordingOptionsCustomizer.OptionKey.MAX_AGE, "123");
        verifyNoMoreInteractions(customizer);
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldSetMaxSize() throws Exception {
        verifyZeroInteractions(customizer);
        command.execute(new String[] {"maxSize=123"});
        verify(customizer).set(RecordingOptionsCustomizer.OptionKey.MAX_SIZE, "123");
        verifyNoMoreInteractions(customizer);
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldSetToDisk() throws Exception {
        verifyZeroInteractions(customizer);
        command.execute(new String[] {"toDisk=true"});
        verify(customizer).set(RecordingOptionsCustomizer.OptionKey.TO_DISK, "true");
        verifyNoMoreInteractions(customizer);
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldUnsetMaxAge() throws Exception {
        verifyZeroInteractions(customizer);
        command.execute(new String[] {"-maxAge"});
        verify(customizer).unset(RecordingOptionsCustomizer.OptionKey.MAX_AGE);
        verifyNoMoreInteractions(customizer);
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldUnsetMaxSize() throws Exception {
        verifyZeroInteractions(customizer);
        command.execute(new String[] {"-maxSize"});
        verify(customizer).unset(RecordingOptionsCustomizer.OptionKey.MAX_SIZE);
        verifyNoMoreInteractions(customizer);
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldUnsetToDisk() throws Exception {
        verifyZeroInteractions(customizer);
        command.execute(new String[] {"-toDisk"});
        verify(customizer).unset(RecordingOptionsCustomizer.OptionKey.TO_DISK);
        verifyNoMoreInteractions(customizer);
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldReturnSuccessOutput() throws Exception {
        verifyZeroInteractions(customizer);
        SerializableCommand.Output<?> out =
                command.serializableExecute(new String[] {"toDisk=true"});
        MatcherAssert.assertThat(out, Matchers.instanceOf(SerializableCommand.SuccessOutput.class));
        verify(customizer).set(RecordingOptionsCustomizer.OptionKey.TO_DISK, "true");
        verifyNoMoreInteractions(customizer);
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldReturnExceptionOutput() throws Exception {
        verifyZeroInteractions(customizer);
        doThrow(NullPointerException.class).when(customizer).set(Mockito.any(), Mockito.any());
        SerializableCommand.Output<?> out =
                command.serializableExecute(new String[] {"toDisk=true"});
        MatcherAssert.assertThat(
                out, Matchers.instanceOf(SerializableCommand.ExceptionOutput.class));
        MatcherAssert.assertThat(out.getPayload(), Matchers.equalTo("NullPointerException: "));
        verify(customizer).set(RecordingOptionsCustomizer.OptionKey.TO_DISK, "true");
        verifyNoMoreInteractions(customizer);
    }
}
