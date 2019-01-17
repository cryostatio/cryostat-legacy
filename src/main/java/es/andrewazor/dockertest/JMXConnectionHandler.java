package es.andrewazor.dockertest;

import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Scanner;

import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;

import es.andrewazor.dockertest.commands.CommandRegistry;
import es.andrewazor.dockertest.commands.CommandRegistryFactory;

class JMXConnectionHandler implements Runnable {

    private final String[] args;
    private final IFlightRecorderService svc;
    private final CommandRegistry commandRegistry;

    JMXConnectionHandler(String[] args, IFlightRecorderService svc) throws Exception {
        this.args = args;
        this.svc = svc;
        this.commandRegistry = CommandRegistryFactory.createNewInstance(svc);
    }

    @Override
    public void run() {
        try {
            if (!svc.isEnabled()) {
                svc.enable();
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
        String[] commands = args[0].split(";");
        for (String command : commands) {
            executeCommandLine(command.trim());
        }
    }

    private void runInteractive() {
        Scanner scanner = new Scanner(System.in);
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

    private void executeCommandLine(String line) {
        String[] words = line.split("\\s");
        String cmd = words[0];
        String[] args = Arrays.copyOfRange(words, 1, words.length);
        System.out.println(String.format("\t\"%s\" \"%s\"", cmd, Arrays.asList(args)));

        try {
            this.commandRegistry.execute(cmd, args);
        } catch (Exception e) {
            System.err.println(String.format("%s operation failed due to %s", line, e.getMessage()));
            e.printStackTrace();
        }
    }
}
