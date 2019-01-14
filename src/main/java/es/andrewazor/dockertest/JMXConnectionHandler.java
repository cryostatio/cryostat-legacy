package es.andrewazor.dockertest;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.common.unit.IOptionDescriptor;
import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

class JMXConnectionHandler implements Runnable {

    private final IFlightRecorderService svc;

    JMXConnectionHandler(IFlightRecorderService svc) {
        this.svc = svc;
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
                if (in.equals("list-options")) {
                    System.out.println("Available recording options:");
                    Map<String, IOptionDescriptor<?>> options = svc.getAvailableRecordingOptions();
                    for (Map.Entry<String, IOptionDescriptor<?>> entry : options.entrySet()) {
                        System.out.println(String.format("\t%s : %s", entry.getKey(), entry.getValue()));
                    }
                } else if (in.equals("dump")) {
                    IConstrainedMap<String> recordingOptions = new RecordingOptionsBuilder(svc)
                        .toDisk(true)
                        .duration(10000)
                        .build();
                    this.svc.start(recordingOptions, null);
                } else if (in.equals("list")) {
                    System.out.println("Available recordings:");
                    for (IRecordingDescriptor recording : svc.getAvailableRecordings()) {
                        System.out.println(String.format("\t%s", recording));
                    }
                } else {
                    System.out.println("Command not recognized");
                }
            } catch (Exception e) {
                System.err.println(String.format("%s operation failed due to %s", in, e.getMessage()));
                e.printStackTrace();
            }
        } while (!in.toLowerCase().equals("exit") && !in.toLowerCase().equals("quit"));
        System.out.println("exit");
    }
}
