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
package com.redhat.rhjmc.containerjfr.commands;

import java.util.Collections;

import org.apache.commons.lang3.StringUtils;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import com.redhat.rhjmc.containerjfr.commands.internal.FailedValidationException;
import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.messaging.CommandExceptionResponseMessage;
import com.redhat.rhjmc.containerjfr.messaging.CommandMessage;
import com.redhat.rhjmc.containerjfr.messaging.CommandUnavailableMessage;
import com.redhat.rhjmc.containerjfr.messaging.FailedValidationResponseMessage;
import com.redhat.rhjmc.containerjfr.messaging.FailureResponseMessage;
import com.redhat.rhjmc.containerjfr.messaging.InvalidCommandResponseMessage;
import com.redhat.rhjmc.containerjfr.messaging.MalformedMessageResponseMessage;
import com.redhat.rhjmc.containerjfr.messaging.MessagingServer;
import com.redhat.rhjmc.containerjfr.messaging.ResponseMessage;
import com.redhat.rhjmc.containerjfr.messaging.SuccessResponseMessage;
import dagger.Lazy;

public class CommandExecutor {

    private final Logger logger;
    private final MessagingServer server;
    private final Lazy<CommandRegistry> registry;
    private final Gson gson;
    private volatile Thread readingThread;
    private volatile boolean running = true;

    CommandExecutor(
            Logger logger,
            MessagingServer server,
            Lazy<CommandRegistry> commandRegistry,
            Gson gson) {
        this.logger = logger;
        this.server = server;
        this.registry = commandRegistry;
        this.gson = gson;
    }

    public synchronized void run() {
        readingThread = Thread.currentThread();
        try (server) {
            while (running) {
                String rawMsg = server.readMessage();
                try {
                    if (StringUtils.isBlank(rawMsg)) {
                        writeMessage(new MalformedMessageResponseMessage(rawMsg));
                        continue;
                    }
                    CommandMessage commandMessage = gson.fromJson(rawMsg, CommandMessage.class);
                    if (commandMessage == null) {
                        writeMessage(new MalformedMessageResponseMessage(rawMsg));
                        continue;
                    }
                    if (commandMessage.args == null) {
                        commandMessage.args = Collections.emptyList();
                    }
                    String commandName = commandMessage.command;
                    String[] args = commandMessage.args.toArray(new String[0]);
                    if (StringUtils.isBlank(commandName)
                            || !registry.get().getRegisteredCommandNames().contains(commandName)) {
                        writeMessage(
                                new InvalidCommandResponseMessage(commandMessage.id, commandName));
                        continue;
                    }
                    if (!registry.get().isCommandAvailable(commandName)) {
                        writeMessage(new CommandUnavailableMessage(commandMessage.id, commandName));
                        continue;
                    }
                    try {
                        registry.get().validate(commandName, args);
                    } catch (FailedValidationException e) {
                        writeMessage(
                                new FailedValidationResponseMessage(
                                        commandMessage.id, commandName, e.getMessage()));
                        continue;
                    }
                    Command.Output<?> out = registry.get().execute(commandName, args);
                    if (out instanceof Command.SuccessOutput) {
                        writeMessage(
                                new SuccessResponseMessage<Void>(
                                        commandMessage.id, commandName, null));
                    } else if (out instanceof Command.FailureOutput) {
                        writeMessage(
                                new FailureResponseMessage(
                                        commandMessage.id,
                                        commandName,
                                        ((Command.FailureOutput) out).getPayload()));
                    } else if (out instanceof Command.StringOutput) {
                        writeMessage(
                                new SuccessResponseMessage<>(
                                        commandMessage.id, commandName, out.getPayload()));
                    } else if (out instanceof Command.ListOutput) {
                        writeMessage(
                                new SuccessResponseMessage<>(
                                        commandMessage.id, commandName, out.getPayload()));
                    } else if (out instanceof Command.MapOutput) {
                        writeMessage(
                                new SuccessResponseMessage<>(
                                        commandMessage.id, commandName, out.getPayload()));
                    } else if (out instanceof Command.ExceptionOutput) {
                        writeMessage(
                                new CommandExceptionResponseMessage(
                                        commandMessage.id,
                                        commandName,
                                        ((Command.ExceptionOutput) out).getPayload()));
                    } else {
                        writeMessage(
                                new CommandExceptionResponseMessage(
                                        commandMessage.id, commandName, "internal error"));
                    }
                } catch (JsonSyntaxException jse) {
                    reportException(rawMsg, jse);
                }
            }
        }
    }

    void shutdown() {
        this.running = false;
        if (readingThread != Thread.currentThread()) {
            readingThread.interrupt();
            readingThread = null;
        }
    }

    private void reportException(String rawMsg, Exception e) {
        logger.warn(e);
        writeMessage(new CommandExceptionResponseMessage(null, rawMsg, e));
    }

    private void writeMessage(ResponseMessage<?> message) {
        server.writeMessage(message);
    }
}
