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

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

import com.redhat.rhjmc.containerjfr.TestBase;
import com.redhat.rhjmc.containerjfr.commands.Command;

@ExtendWith(MockitoExtension.class)
public class CommandRegistryImplTest extends TestBase {

    CommandRegistryImpl registry;

    @Nested
    class WithEmptySetCommands {

        @BeforeEach
        public void setup() {
            registry = new CommandRegistryImpl(mockClientWriter, Collections.emptySet());
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
            registry.execute("foo", new String[] {});
            assertThat(stdout(), equalTo("Command \"foo\" not recognized\n"));
        }

        @Test
        public void shouldNotValidateCommands() throws Exception {
            assertFalse(registry.validate("foo", new String[0]));
            assertThat(stdout(), equalTo("Command \"foo\" not recognized\n"));
        }
    }

    @Nested
    class WithCommandDefinitions {

        FooCommand fooCommand = new FooCommand();
        BarCommand barCommand = new BarCommand();

        Command[] commands = new Command[] {fooCommand, barCommand};

        @BeforeEach
        public void setup() {
            registry =
                    new CommandRegistryImpl(
                            mockClientWriter, new HashSet<Command>(Arrays.asList(commands)));
        }

        @Test
        public void shouldReturnRegisteredCommandNames() {
            assertThat(
                    "registered command names should be returned",
                    registry.getRegisteredCommandNames(),
                    equalTo(
                            new HashSet<String>(
                                    Arrays.asList(fooCommand.getName(), barCommand.getName()))));
        }

        @Test
        public void shouldReturnAvailableCommandNames() {
            assertThat(
                    "available command names should be returned",
                    registry.getAvailableCommandNames(),
                    equalTo(new HashSet<String>(Arrays.asList(fooCommand.getName()))));
        }

        @Test
        public void shouldExecuteRegisteredAndAvailableCommand() throws Exception {
            assertThat("command should not have been executed", fooCommand.value, nullValue());
            registry.execute("foo", new String[] {"arg"});
            assertThat("command should have been executed", fooCommand.value, equalTo("arg"));
        }

        @Test
        public void shouldNotExecuteRegisteredAndUnavailableCommand() throws Exception {
            assertThat("command should not have been executed", barCommand.value, nullValue());
            registry.execute("bar", new String[] {"arg"});
            assertThat("command should not have been executed", barCommand.value, nullValue());
            assertThat(stdout(), equalTo("Command \"bar\" not available\n"));
        }

        @Test
        public void shouldNoOpOnUnregisteredCommand() throws Exception {
            assertThat("command should not have been executed", fooCommand.value, nullValue());
            assertThat("command should not have been executed", barCommand.value, nullValue());
            registry.execute("baz", new String[] {"arg"});
            assertThat("command should not have been executed", fooCommand.value, nullValue());
            assertThat("command should not have been executed", barCommand.value, nullValue());
            assertThat(stdout(), equalTo("Command \"baz\" not recognized\n"));
        }

        @Test
        public void shouldNotValidateUnknownCommands() throws Exception {
            assertFalse(registry.validate("baz", new String[0]));
            assertThat(stdout(), equalTo("Command \"baz\" not recognized\n"));
        }

        @ParameterizedTest
        @ValueSource(strings = {"bar", "", "  "})
        @NullSource
        public void shouldNotValidateInvalidCommands(String cmd) throws Exception {
            assertFalse(registry.validate(cmd, new String[0]));
        }

        @Test
        public void shouldValidateCommands() throws Exception {
            assertTrue(registry.validate("foo", new String[0]));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "  "})
        @NullSource
        public void shouldHandleBlankOrNullCommandAvailability(String cmd) throws Exception {
            assertFalse(registry.isCommandAvailable(cmd));
        }
    }

    @Nested
    class WithConflictingCommandDefinitions {
        @Test
        public void shouldThrowCommandDefinitionException() {
            CommandRegistryImpl.CommandDefinitionException thrown =
                    Assertions.assertThrows(
                            CommandRegistryImpl.CommandDefinitionException.class,
                            () ->
                                    new CommandRegistryImpl(
                                            mockClientWriter,
                                            new HashSet<Command>(
                                                    Arrays.asList(
                                                            new FooCommand(),
                                                            new DuplicateFooCommand()))),
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

    static class FooCommand implements Command {
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
        public boolean validate(String[] args) {
            return true;
        }

        @Override
        public void execute(String[] args) {
            this.value = args[0];
        }
    }

    static class BarCommand implements Command {
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
        public boolean validate(String[] args) {
            return false;
        }

        @Override
        public void execute(String[] args) {
            this.value = args[0];
        }
    }

    static class DuplicateFooCommand extends FooCommand {}
}
