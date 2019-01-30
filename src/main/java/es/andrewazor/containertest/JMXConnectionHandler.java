package es.andrewazor.containertest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;

import es.andrewazor.containertest.commands.CommandRegistry;
import es.andrewazor.containertest.commands.CommandRegistryFactory;

class JMXConnectionHandler implements Runnable {

    private final String[] args;
    private final JMCConnection connection;
    private final CommandRegistry commandRegistry;

    JMXConnectionHandler(String[] args, JMCConnection connection) throws Exception {
        this.args = args;
        this.connection = connection;
        this.commandRegistry = CommandRegistryFactory.createNewInstance(connection);
    }

    @Override
    public void run() {
        try {
            if (!connection.getService().isEnabled()) {
                connection.getService().enable();
            }
        } catch (FlightRecorderException fre) {
            throw new RuntimeException(fre);
        }
        if (args.length == 0) {
            runInteractive();
        } else {
            runScripted();
        }
    }

    private void runScripted() {
        executeCommands(args[0].split(";"));
    }

    private void runInteractive() {
        try (Scanner scanner = new Scanner(System.in)) {
            String in;
            do {
                System.out.print("> ");
                try {
                    in = scanner.nextLine().trim();
                } catch (NoSuchElementException e) {
                    break;
                }
                executeCommandLine(in);
            } while (!in.toLowerCase().equals("exit") && !in.toLowerCase().equals("quit"));
            System.out.println("exit");
        }
    }

    private void executeCommands(String[] lines) {
        List<CommandLine> commandLines = new ArrayList<>(lines.length);
        for (String line : lines) {
            String[] words = line.split("\\s");
            String cmd = words[0];
            String[] args = Arrays.copyOfRange(words, 1, words.length);
            commandLines.add(new CommandLine(cmd, args));
        }

        boolean allValid = true;
        for (CommandLine commandLine : commandLines) {
            try {
                boolean valid = this.commandRegistry.validate(commandLine.command, commandLine.args);
                if (!valid) {
                    System.out.println(String.format("\t\"%s\" are invalid arguments to %s", Arrays.asList(commandLine.args), commandLine.command));
                }
                allValid &= valid;
            } catch (Exception e) {
                allValid = false;
                e.printStackTrace();
            }
        }

        if (!allValid) {
            return;
        }

        for (CommandLine commandLine : commandLines) {
            try {
                System.out.println(String.format("\n\"%s\" \"%s\"", commandLine.command, Arrays.asList(commandLine.args)));
                this.commandRegistry.execute(commandLine.command, commandLine.args);
            } catch (Exception e) {
                System.err.println(String.format("%s operation failed due to %s", commandLine, e.getMessage()));
                e.printStackTrace();
            }
        }
    }

    private void executeCommandLine(String line) {
        executeCommands(new String[] { line  });
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
            StringBuilder sb = new StringBuilder();
            sb.append(command);
            sb.append(" ");
            for (String arg : args) {
                sb.append(arg);
                sb.append(" ");
            }
            return sb.toString().trim();
        }
    }
}
