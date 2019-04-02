package es.andrewazor.containertest.tui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

import org.apache.commons.lang3.exception.ExceptionUtils;

import dagger.Lazy;
import es.andrewazor.containertest.JMCConnection;
import es.andrewazor.containertest.commands.CommandRegistry;
import es.andrewazor.containertest.commands.internal.ExitCommand;

public class Shell implements CommandExecutor {

    private final ClientReader cr;
    private final ClientWriter cw;
    private final Lazy<CommandRegistry> commandRegistry;
    private boolean connected = false;

    public Shell(ClientReader cr, ClientWriter cw, Lazy<CommandRegistry> commandRegistry) {
        this.cr = cr;
        this.cw = cw;
        this.commandRegistry = commandRegistry;
    }

    @Override
    public void connectionChanged(JMCConnection connection) {
        this.connected = connection != null;
    }

    @Override
    public void run(String[] args) {
        try {
            if (args.length == 0) {
                runInteractive();
            } else {
                runScripted(args);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void runScripted(String[] args) {
        List<String> commands = new ArrayList<>(Arrays.asList(args[0].split(";")));
        commands.add(ExitCommand.NAME);
        executeCommands(commands);
    }

    private void runInteractive() {
        try (cr) {
            String in;
            do {
                cw.print(this.connected ? "> " : "- ");
                try {
                    in = cr.readLine().trim();
                } catch (NoSuchElementException e) {
                    in = ExitCommand.NAME;
                }
                executeCommandLine(in);
            } while (!in.toLowerCase().equals(ExitCommand.NAME.toLowerCase()));
        } catch (Exception e) {
            cw.println(ExceptionUtils.getStackTrace(e));
        }
    }

    private void executeCommands(List<String> lines) {
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
            try {
                boolean valid = this.commandRegistry.get().validate(commandLine.command, commandLine.args);
                if (!valid) {
                    cw.println(String.format("\t\"%s\" are invalid arguments to %s", Arrays.asList(commandLine.args), commandLine.command));
                }
                allValid &= valid;
            } catch (Exception e) {
                allValid = false;
                cw.println(ExceptionUtils.getStackTrace(e));
            }
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

    private void executeCommandLine(String line) {
        executeCommands(Collections.singletonList(line));
    }

    private static class CommandLine {
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
