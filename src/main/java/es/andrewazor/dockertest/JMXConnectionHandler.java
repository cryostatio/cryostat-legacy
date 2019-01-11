package es.andrewazor.dockertest;

import java.util.NoSuchElementException;
import java.util.Scanner;

import org.openjdk.jmc.rjmx.internal.RJMXConnection;

class JMXConnectionHandler implements Runnable {

    private final RJMXConnection connection;

    JMXConnectionHandler(RJMXConnection connection) {
        this.connection = connection;
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
            System.out.println(String.format("\t\"%s\"", in));

            try {
                if (in.equals("list")) {
                    System.out.println("MBeans:");
                    this.connection.getMBeanNames().forEach(o -> System.out.println(String.format("\n\t%s", o.getCanonicalName())));
                }
            } catch (Exception e) {
                System.err.println(String.format("%s operation failed due to %s", in, e.getMessage()));
            }
        } while (!in.toLowerCase().equals("exit") && !in.toLowerCase().equals("quit"));
        System.out.println("exit");
    }
}
