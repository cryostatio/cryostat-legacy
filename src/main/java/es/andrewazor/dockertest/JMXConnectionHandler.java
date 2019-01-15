package es.andrewazor.dockertest;

import java.util.Arrays;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.common.unit.IOptionDescriptor;
import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

class JMXConnectionHandler implements Runnable {

    private static final Pattern DOWNLOAD_PATTERN = Pattern.compile("download\\s(.+)\\s(.+)");
    private final IFlightRecorderService svc;;

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
            String[] words = in.split("\\s");
            String cmd = words[0];
            String[] args = Arrays.copyOfRange(words, 1, words.length);
            System.out.println(String.format("\t\"%s\" \"%s\"", cmd, Arrays.asList(args)));

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
                } else if (in.matches(DOWNLOAD_PATTERN.pattern())) {
                    Matcher m = DOWNLOAD_PATTERN.matcher(in);
                    m.find();
                    String recordingName = m.group(1);
                    String savePath = m.group(2);

                    IRecordingDescriptor descriptor = null;
                    for (IRecordingDescriptor recording : svc.getAvailableRecordings()) {
                        if (recording.getName().equals(recordingName)) {
                            descriptor = recording;
                            break;
                        }
                    }

                    if (descriptor == null) {
                        System.out.println(String.format("\tCould not locate recording named \"%s\"", recordingName));
                        continue;
                    }

                    System.out.println(String.format("\tDownloading recording \"%s\" to \"%s\" ...", recordingName, savePath));

                    // GZIPInputStream gzip = new GZIPInputStream(svc.openStream(descriptor, false));
                } else {
                    System.out.println(String.format("Command \"%s\" not recognized", cmd));
                }
            } catch (Exception e) {
                System.err.println(String.format("%s operation failed due to %s", in, e.getMessage()));
                e.printStackTrace();
            }
        } while (!in.toLowerCase().equals("exit") && !in.toLowerCase().equals("quit"));
        System.out.println("exit");
    }
}
