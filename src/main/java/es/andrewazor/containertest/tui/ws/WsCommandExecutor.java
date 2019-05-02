package es.andrewazor.containertest.tui.ws;

import java.util.Collections;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import dagger.Lazy;
import es.andrewazor.containertest.commands.CommandRegistry;
import es.andrewazor.containertest.commands.internal.ExitCommand;
import es.andrewazor.containertest.net.JMCConnection;
import es.andrewazor.containertest.tui.ClientReader;
import es.andrewazor.containertest.tui.ClientWriter;
import es.andrewazor.containertest.tui.CommandExecutor;

class WsCommandExecutor implements CommandExecutor {

    private final Gson gson;
    private final MessagingServer server;
    private final ClientReader cr;
    private final ClientWriter cw;
    private final Lazy<CommandRegistry> registry;

    WsCommandExecutor(MessagingServer server, ClientReader cr, ClientWriter cw, Lazy<CommandRegistry> commandRegistry, Gson gson) {
        this.gson = gson;
        this.server = server;
        this.cr = cr;
        this.cw = cw;
        this.registry = commandRegistry;
    }

    @Override
    public void run(String unused) {
        try (cr) {
            while (true) {
                try {
                    String rawMsg = cr.readLine();
                    if (rawMsg == null) {
                        continue;
                    }
                    CommandMessage commandMessage = gson.fromJson(rawMsg, CommandMessage.class);
                    if (commandMessage.args == null) {
                        commandMessage.args = Collections.emptyList();
                    }
                    if (!registry.get().validate(commandMessage.command, commandMessage.args.toArray(new String[0]))) {
                        flush(new InvalidCommandResponseMessage());
                        continue;
                    }
                    if (!registry.get().isCommandAvailable(commandMessage.command)) {
                        flush(new CommandUnavailableMessage());
                        continue;
                    }
                    server.getConnection().clearBuffer();
                    try {
                        registry.get().execute(commandMessage.command, commandMessage.args.toArray(new String[0]));
                        flush(new SuccessResponseMessage());
                    } catch (Exception e) {
                        reportException(e);
                    }
                } catch (JsonSyntaxException jse) {
                    reportException(jse);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void reportException(Exception e) {
        e.printStackTrace();
        cw.println(e.getMessage());
        flush(new CommandExceptionResponseMessage());
    }

    private void flush(ResponseMessage message) {
        server.getConnection().flush(message);
    }

}