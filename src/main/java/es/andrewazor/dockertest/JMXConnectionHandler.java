package es.andrewazor.dockertest;

import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Scanner;

import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;

import es.andrewazor.dockertest.commands.CommandRegistry;
import es.andrewazor.dockertest.commands.CommandRegistryFactory;

class JMXConnectionHandler implements Runnable {

    private final IFlightRecorderService svc;
    private final CommandRegistry commandRegistry;

    JMXConnectionHandler(IFlightRecorderService svc) throws Exception {
        this.svc = svc;
        this.commandRegistry = CommandRegistryFactory.createNewInstance(svc);
    }

    @Override
    public void run() {
        Scanner scanner = new Scanner(System.in);
        String in;
        do {
            System.out.print("> ");
            try {
                in = scanner.nextLine().trim();
            } catch (NoSuchElementException e) {
                break;
            }
            String[] words = in.split("\\s");
            String cmd = words[0];
            String[] args = Arrays.copyOfRange(words, 1, words.length);
            System.out.println(String.format("\t\"%s\" \"%s\"", cmd, Arrays.asList(args)));

            try {
                this.commandRegistry.execute(cmd, args);
            } catch (Exception e) {
                System.err.println(String.format("%s operation failed due to %s", in, e.getMessage()));
                e.printStackTrace();
            }
        } while (!in.toLowerCase().equals("exit") && !in.toLowerCase().equals("quit"));
        System.out.println("exit");
    }
}
