package es.andrewazor.containertest.tui;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.exception.ExceptionUtils;

import dagger.Lazy;
import es.andrewazor.containertest.commands.CommandRegistry;
import es.andrewazor.containertest.commands.internal.ExitCommand;
import es.andrewazor.containertest.net.JMCConnection;

abstract class AbstractCommandExecutor implements CommandExecutor {

    protected final ClientReader cr;
    protected final ClientWriter cw;
    protected final Lazy<CommandRegistry> commandRegistry;
    protected JMCConnection connection;

    AbstractCommandExecutor(ClientReader cr, ClientWriter cw, Lazy<CommandRegistry> commandRegistry) {
        this.cr = cr;
        this.cw = cw;
        this.commandRegistry = commandRegistry;
    }

    @Override
    public void connectionChanged(JMCConnection connection) {
        this.connection = connection;
    }

    protected void executeCommands(List<String> lines) {
        List<CommandLine> commandLines = lines
            .stream()
            .map(String::trim)
            .filter(s -> !s.startsWith("#"))
            .map(line -> line.split("\\s"))
            .filter(words -> words.length > 0 && !words[0].isEmpty())
            .map(words -> new CommandLine(words[0], Arrays.copyOfRange(words, 1, words.length)))
            .collect(Collectors.toList());

        boolean allValid = true;
        for (CommandLine commandLine : commandLines) {
            boolean valid = this.commandRegistry.get().validate(commandLine.command, commandLine.args);
            if (!valid) {
                cw.println(String.format("\t\"%s\" are invalid arguments to %s", Arrays.asList(commandLine.args), commandLine.command));
            }
            allValid &= valid;
        }

        if (!allValid) {
            return;
        }

        for (CommandLine commandLine : commandLines) {
            try {
                cw.println(String.format("\n\"%s\" \"%s\"", commandLine.command, Arrays.asList(commandLine.args)));
                this.commandRegistry.get().execute(commandLine.command, commandLine.args);
                if (commandLine.command.toLowerCase().equals(ExitCommand.NAME.toLowerCase())) {
                    break;
                }
            } catch (Exception e) {
                cw.println(String.format("%s operation failed due to %s", commandLine, e.getMessage()));
                cw.println(ExceptionUtils.getStackTrace(e));
            }
        }
    }

    protected void executeCommandLine(String line) {
        executeCommands(Collections.singletonList(line));
    }

    protected static class CommandLine {
        final String command;
        final String[] args;

        CommandLine(String command, String[] args) {
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