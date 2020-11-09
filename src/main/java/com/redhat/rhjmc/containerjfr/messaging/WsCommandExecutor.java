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
package com.redhat.rhjmc.containerjfr.messaging;

import java.io.IOException;
import java.util.Collections;

import org.apache.commons.lang3.StringUtils;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import com.redhat.rhjmc.containerjfr.commands.CommandRegistry;
import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.commands.internal.FailedValidationException;
import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.tui.ClientReader;
import dagger.Lazy;

public class WsCommandExecutor {

    private final Logger logger;
    private final MessagingServer server;
    private final ClientReader cr;
    private final Lazy<CommandRegistry> registry;
    private final Gson gson;
    private volatile Thread readingThread;
    private volatile boolean running = true;

    WsCommandExecutor(
            Logger logger,
            MessagingServer server,
            ClientReader cr,
            Lazy<CommandRegistry> commandRegistry,
            Gson gson) {
        this.logger = logger;
        this.server = server;
        this.cr = cr;
        this.registry = commandRegistry;
        this.gson = gson;
    }

    public synchronized void run() {
        readingThread = Thread.currentThread();
        try (cr) {
            while (running) {
                String rawMsg = cr.readLine();
                try {
                    if (StringUtils.isBlank(rawMsg)) {
                        flush(new MalformedMessageResponseMessage(rawMsg));
                        continue;
                    }
                    CommandMessage commandMessage = gson.fromJson(rawMsg, CommandMessage.class);
                    if (commandMessage == null) {
                        flush(new MalformedMessageResponseMessage(rawMsg));
                        continue;
                    }
                    if (commandMessage.args == null) {
                        commandMessage.args = Collections.emptyList();
                    }
                    String commandName = commandMessage.command;
                    String[] args = commandMessage.args.toArray(new String[0]);
                    if (StringUtils.isBlank(commandName)
                            || !registry.get().getRegisteredCommandNames().contains(commandName)) {
                        flush(new InvalidCommandResponseMessage(commandMessage.id, commandName));
                        continue;
                    }
                    if (!registry.get().isCommandAvailable(commandName)) {
                        flush(new CommandUnavailableMessage(commandMessage.id, commandName));
                        continue;
                    }
                    try {
                        registry.get().validate(commandName, args);
                    } catch (FailedValidationException e) {
                        flush(
                                new FailedValidationResponseMessage(
                                        commandMessage.id, commandName, e.getMessage()));
                        continue;
                    }
                    SerializableCommand.Output<?> out = registry.get().execute(commandName, args);
                    if (out instanceof SerializableCommand.SuccessOutput) {
                        flush(
                                new SuccessResponseMessage<Void>(
                                        commandMessage.id, commandName, null));
                    } else if (out instanceof SerializableCommand.FailureOutput) {
                        flush(
                                new FailureResponseMessage(
                                        commandMessage.id,
                                        commandName,
                                        ((SerializableCommand.FailureOutput) out).getPayload()));
                    } else if (out instanceof SerializableCommand.StringOutput) {
                        flush(
                                new SuccessResponseMessage<>(
                                        commandMessage.id, commandName, out.getPayload()));
                    } else if (out instanceof SerializableCommand.ListOutput) {
                        flush(
                                new SuccessResponseMessage<>(
                                        commandMessage.id, commandName, out.getPayload()));
                    } else if (out instanceof SerializableCommand.MapOutput) {
                        flush(
                                new SuccessResponseMessage<>(
                                        commandMessage.id, commandName, out.getPayload()));
                    } else if (out instanceof SerializableCommand.ExceptionOutput) {
                        flush(
                                new CommandExceptionResponseMessage(
                                        commandMessage.id,
                                        commandName,
                                        ((SerializableCommand.ExceptionOutput) out).getPayload()));
                    } else {
                        flush(
                                new CommandExceptionResponseMessage(
                                        commandMessage.id, commandName, "internal error"));
                    }
                } catch (JsonSyntaxException jse) {
                    reportException(rawMsg, jse);
                }
            }
        } catch (IOException e) {
            logger.warn(e);
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
        flush(new CommandExceptionResponseMessage(null, rawMsg, e));
    }

    private void flush(ResponseMessage<?> message) {
        server.flush(message);
    }
}
