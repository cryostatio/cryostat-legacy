package es.andrewazor.containertest.tui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dagger.Lazy;
import es.andrewazor.containertest.commands.CommandRegistry;
import es.andrewazor.containertest.commands.internal.ExitCommand;

class BatchModeExecutor extends AbstractCommandExecutor {
    BatchModeExecutor(ClientReader cr, ClientWriter cw, Lazy<CommandRegistry> commandRegistry) {
        super(cr, cw, commandRegistry);
    }

    @Override
    public void run(String clientArgString) {
        List<String> commands = new ArrayList<>(Arrays.asList(clientArgString.split(";")));
        commands.add(ExitCommand.NAME);
        executeCommands(commands);
    }
}