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

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import io.cryostat.commands.Command;
import io.cryostat.commands.SerializableCommand;
import io.cryostat.core.log.Logger;

@ExtendWith(MockitoExtension.class)
public class SerializableCommandRegistryImplTest {

    SerializableCommandRegistryImpl registry;

    @Nested
    class WithEmptySetCommands {

        @BeforeEach
        public void setup() {
            registry =
                    new SerializableCommandRegistryImpl(
                            Collections.emptySet(), Mockito.mock(Logger.class));
        }

        @Test
        public void shouldReturnEmptyRegisteredCommandNames() {
            assertThat(
                    "registered commands should be empty",
                    registry.getRegisteredCommandNames(),
                    equalTo(Collections.emptySet()));
        }

        @Test
        public void shouldReturnEmptyAvailableCommandNames() {
            assertThat(
                    "available commands should be empty",
                    registry.getAvailableCommandNames(),
                    equalTo(Collections.emptySet()));
        }

        @Test
        public void shouldNoOpOnExecute() throws Exception {
            Assertions.assertDoesNotThrow(() -> registry.execute("foo", new String[] {}));
        }

        @Test
        public void shouldNotValidateCommands() throws Exception {
            Exception e =
                    assertThrows(
                            FailedValidationException.class,
                            () -> registry.validate("foo", new String[0]));
            assertThat(e.getMessage(), equalTo("Command \"foo\" not recognized"));
        }
    }

    @Nested
    class WithCommandDefinitions {

        FooCommand fooCommand = new FooCommand();
        BarCommand barCommand = new BarCommand();
        BazCommand bazCommand = new BazCommand();
        FizzCommand fizzCommand = new FizzCommand();

        Command[] commands = new Command[] {fooCommand, barCommand, bazCommand, fizzCommand};

        @BeforeEach
        public void setup() {
            registry =
                    new SerializableCommandRegistryImpl(
                            new HashSet<Command>(Arrays.asList(commands)),
                            Mockito.mock(Logger.class));
        }

        @Test
        public void shouldReturnRegisteredCommandNames() {
            assertThat(
                    "registered command names should be returned",
                    registry.getRegisteredCommandNames(),
                    equalTo(
                            new HashSet<String>(
                                    Arrays.asList(
                                            fooCommand.getName(),
                                            fizzCommand.getName(),
                                            barCommand.getName()))));
        }

        @Test
        public void shouldReturnAvailableCommandNames() {
            assertThat(
                    "available command names should be returned",
                    registry.getAvailableCommandNames(),
                    equalTo(
                            new HashSet<String>(
                                    Arrays.asList(fooCommand.getName(), fizzCommand.getName()))));
        }

        @Test
        public void shouldExecuteRegisteredAndAvailableCommand() throws Exception {
            assertThat("command should not have been executed", fooCommand.value, nullValue());
            SerializableCommand.Output<?> out = registry.execute("foo", new String[] {"arg"});
            assertThat(out, instanceOf(SerializableCommand.SuccessOutput.class));
            assertThat("command should have been executed", fooCommand.value, equalTo("arg"));
        }

        @Test
        public void shouldNotExecuteRegisteredAndUnavailableCommand() throws Exception {
            assertThat("command should not have been executed", barCommand.value, nullValue());
            SerializableCommand.Output<?> out = registry.execute("bar", new String[] {"arg"});
            assertThat(out, instanceOf(SerializableCommand.FailureOutput.class));
            assertThat("command should not have been executed", barCommand.value, nullValue());
        }

        @Test
        public void shouldNoOpOnUnregisteredCommand() throws Exception {
            assertThat("command should not have been executed", fooCommand.value, nullValue());
            assertThat("command should not have been executed", barCommand.value, nullValue());
            SerializableCommand.Output<?> out = registry.execute("baz", new String[] {"arg"});
            assertThat(out, instanceOf(SerializableCommand.FailureOutput.class));
            assertThat("command should not have been executed", fooCommand.value, nullValue());
            assertThat("command should not have been executed", barCommand.value, nullValue());
        }

        @Test
        public void shouldWrapUncaughtCommandExceptions() throws Exception {
            SerializableCommand.Output<?> out = registry.execute("fizz", new String[0]);
            assertThat(out, instanceOf(SerializableCommand.ExceptionOutput.class));
            assertThat(out.getPayload(), equalTo("NullPointerException: Fizzed Out!"));
        }

        @Test
        public void shouldNotValidateUnknownCommands() throws Exception {
            Exception e =
                    assertThrows(
                            FailedValidationException.class,
                            () -> registry.validate("baz", new String[0]));
            assertThat(e.getMessage(), equalTo("Command \"baz\" not recognized"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"bar", "  "})
        @NullAndEmptySource
        public void shouldNotValidateInvalidCommands(String cmd) throws Exception {
            Exception e =
                    assertThrows(
                            FailedValidationException.class,
                            () -> registry.validate(cmd, new String[0]));
        }

        @Test
        public void shouldValidateCommands() throws Exception {
            assertDoesNotThrow(() -> registry.validate("foo", new String[0]));
        }

        @ParameterizedTest
        @ValueSource(strings = {"  "})
        @NullAndEmptySource
        public void shouldHandleBlankNullEmptyCommandAvailability(String cmd) throws Exception {
            assertFalse(registry.isCommandAvailable(cmd));
        }
    }

    @Nested
    class WithConflictingCommandDefinitions {
        @Test
        public void shouldThrowCommandDefinitionException() {
            CommandRegistryImpl.CommandDefinitionException thrown =
                    assertThrows(
                            CommandRegistryImpl.CommandDefinitionException.class,
                            () ->
                                    new SerializableCommandRegistryImpl(
                                            new HashSet<Command>(
                                                    Arrays.asList(
                                                            new FooCommand(),
                                                            new DuplicateFooCommand())),
                                            Mockito.mock(Logger.class)),
                            "should throw CommandDefinitionException for duplicate definitions");
            assertThat(
                    thrown.getMessage(),
                    allOf(
                            containsString("\"foo\" command definitions provided by class"),
                            containsString(DuplicateFooCommand.class.getCanonicalName()),
                            containsString("AND class"),
                            containsString(FooCommand.class.getCanonicalName())));
        }
    }

    static class FooCommand implements SerializableCommand {
        String value = null;

        @Override
        public String getName() {
            return "foo";
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public void validate(String[] args) throws FailedValidationException {}

        @Override
        public void execute(String[] args) {
            this.value = args[0];
        }

        @Override
        public Output<?> serializableExecute(String[] args) {
            this.value = args[0];
            return new SuccessOutput();
        }
    }

    static class BarCommand implements SerializableCommand {
        String value = null;

        @Override
        public String getName() {
            return "bar";
        }

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public void validate(String[] args) throws FailedValidationException {
            throw new FailedValidationException("foo could not be found");
        }

        @Override
        public void execute(String[] args) {
            this.value = args[0];
        }

        @Override
        public Output<?> serializableExecute(String[] args) {
            this.value = args[0];
            return new SuccessOutput();
        }
    }

    static class BazCommand implements Command {
        String value = null;

        @Override
        public String getName() {
            return "baz";
        }

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public void validate(String[] args) throws FailedValidationException {
            throw new FailedValidationException("fizz could not be found");
        }

        @Override
        public void execute(String[] args) {
            this.value = args[0];
        }
    }

    static class FizzCommand implements SerializableCommand {
        @Override
        public String getName() {
            return "fizz";
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public void validate(String[] args) throws FailedValidationException {}

        @Override
        public void execute(String[] args) {}

        @Override
        public Output<?> serializableExecute(String[] args) {
            throw new NullPointerException("Fizzed Out!");
        }
    }

    static class DuplicateFooCommand extends FooCommand {}
}
