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

import java.util.ArrayList;

import javax.inject.Inject;

import com.redhat.rhjmc.containerjfr.commands.CommandRegistry;
import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.commands.SerializableCommandRegistry;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import dagger.Lazy;

class HelpCommand implements SerializableCommand {

    private final ClientWriter cw;
    private final Lazy<CommandRegistry> registry;
    private final Lazy<SerializableCommandRegistry> serializableRegistry;

    @Inject
    HelpCommand(
            ClientWriter cw,
            Lazy<CommandRegistry> commandRegistry,
            Lazy<SerializableCommandRegistry> serializableCommandRegistry) {
        this.cw = cw;
        this.registry = commandRegistry;
        this.serializableRegistry = serializableCommandRegistry;
    }

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public void validate(String[] args) throws FailedValidationException {
        if (args.length != 0) {
            String errorMessage = "No arguments expected";
            cw.println(errorMessage);
            throw new FailedValidationException(errorMessage);
        }
        for (String arg : args) {
            if (arg == null) {
                String errorMessage = "One or more arguments were null";
                cw.println(errorMessage);
                throw new FailedValidationException(errorMessage);
            }
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    /** No args expected. */
    @Override
    public void execute(String[] args) throws Exception {
        cw.println("Available commands:");
        registry.get().getAvailableCommandNames().forEach(this::printCommand);
    }

    @Override
    public Output<?> serializableExecute(String[] args) {
        return new ListOutput<>(
                new ArrayList<>(serializableRegistry.get().getAvailableCommandNames()));
    }

    private void printCommand(String cmd) {
        cw.println(String.format("\t%s", cmd));
    }
}
