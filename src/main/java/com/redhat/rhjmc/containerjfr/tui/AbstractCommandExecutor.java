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
package com.redhat.rhjmc.containerjfr.tui;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.redhat.rhjmc.containerjfr.commands.CommandRegistry;
import com.redhat.rhjmc.containerjfr.commands.internal.ExitCommand;
import com.redhat.rhjmc.containerjfr.commands.internal.FailedValidationException;
import com.redhat.rhjmc.containerjfr.core.tui.ClientReader;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import dagger.Lazy;

public abstract class AbstractCommandExecutor implements CommandExecutor {

    protected final ClientReader cr;
    protected final ClientWriter cw;
    protected final Lazy<CommandRegistry> commandRegistry;

    protected AbstractCommandExecutor(
            ClientReader cr, ClientWriter cw, Lazy<CommandRegistry> commandRegistry) {
        this.cr = cr;
        this.cw = cw;
        this.commandRegistry = commandRegistry;
    }

    protected void executeCommands(List<String> lines) {
        List<CommandLine> commandLines =
                lines.stream()
                        .map(String::trim)
                        .filter(s -> !s.startsWith("#"))
                        .map(line -> line.split("\\s"))
                        .filter(words -> words.length > 0 && !words[0].isEmpty())
                        .map(
                                words ->
                                        new CommandLine(
                                                words[0],
                                                Arrays.copyOfRange(words, 1, words.length)))
                        .collect(Collectors.toList());

        if (!validateCommands(commandLines)) {
            return;
        }

        for (CommandLine commandLine : commandLines) {
            try {
                cw.println(
                        String.format(
                                "%n\"%s\" \"%s\"",
                                commandLine.command, Arrays.asList(commandLine.args)));
                this.commandRegistry.get().execute(commandLine.command, commandLine.args);
                if (commandLine.command.toLowerCase().equals(ExitCommand.NAME.toLowerCase())) {
                    break;
                }
            } catch (Exception e) {
                cw.println(
                        String.format(
                                "%s operation failed due to %s", commandLine, e.getMessage()));
                cw.println(e);
            }
        }
    }

    protected boolean validateCommands(Collection<CommandLine> commandLines) {
        boolean allValid = true;
        for (CommandLine commandLine : commandLines) {
            try {
                this.commandRegistry.get().validate(commandLine.command, commandLine.args);
            } catch (FailedValidationException e) {
                cw.println(
                        String.format(
                                "Could not validate \"%s\" command; %s",
                                commandLine.command, e.getMessage()));
                allValid = false;
            }
        }
        return allValid;
    }

    protected void executeCommandLine(String line) {
        executeCommands(Collections.singletonList(line));
    }

    protected static class CommandLine {
        public final String command;
        public final String[] args;

        public CommandLine(String command, String[] args) {
            this.command = command;
            this.args = args;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder(command);
            for (String arg : args) {
                sb.append(" ");
                sb.append(arg);
            }
            return sb.toString();
        }
    }
}
