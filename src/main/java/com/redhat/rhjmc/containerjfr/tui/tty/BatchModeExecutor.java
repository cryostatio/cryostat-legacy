package com.redhat.rhjmc.containerjfr.tui.tty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.redhat.rhjmc.containerjfr.commands.CommandRegistry;
import com.redhat.rhjmc.containerjfr.commands.internal.ExitCommand;
import dagger.Lazy;
import com.redhat.rhjmc.containerjfr.tui.AbstractCommandExecutor;
import com.redhat.rhjmc.containerjfr.tui.ClientReader;
import com.redhat.rhjmc.containerjfr.tui.ClientWriter;

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