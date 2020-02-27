package com.redhat.rhjmc.containerjfr.tui.ws;

import java.io.IOException;
import java.util.Collections;

import org.apache.commons.lang3.StringUtils;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.commands.SerializableCommandRegistry;
import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.tui.ClientReader;
import com.redhat.rhjmc.containerjfr.tui.CommandExecutor;
import dagger.Lazy;

class WsCommandExecutor implements CommandExecutor {

    private final Logger logger;
    private final MessagingServer server;
    private final ClientReader cr;
    private final Lazy<SerializableCommandRegistry> registry;
    private final Gson gson;
    private volatile Thread readingThread;
    private volatile boolean running = true;

    WsCommandExecutor(
            Logger logger,
            MessagingServer server,
            ClientReader cr,
            Lazy<SerializableCommandRegistry> commandRegistry,
            Gson gson) {
        this.logger = logger;
        this.server = server;
        this.cr = cr;
        this.registry = commandRegistry;
        this.gson = gson;
    }

    @Override
    public synchronized void run(String unused) {
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
                    if (!registry.get().validate(commandName, args)) {
                        flush(
                                new InvalidCommandArgumentsResponseMessage(
                                        commandMessage.id, commandName, args));
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
