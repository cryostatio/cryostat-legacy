package es.andrewazor.containertest.net;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openjdk.jmc.flightrecorder.CouldNotLoadRecordingException;
import org.openjdk.jmc.flightrecorder.rules.report.html.JfrHtmlRulesReport;
import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import es.andrewazor.containertest.sys.Environment;
import es.andrewazor.containertest.tui.ClientWriter;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;

public class RecordingExporter implements ConnectionListener {

    // TODO extract the name pattern (here and AbstractConnectedCommand) to shared
    // utility
    private static final Pattern RECORDING_NAME_PATTERN = Pattern.compile("^/([\\w-_]+)(?:\\.jfr)?$",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    private static final Pattern REPORT_PATTERN = Pattern.compile("^/reports/([\\w-_]+)$", Pattern.MULTILINE);
    static final String HOST_VAR = "CONTAINER_DOWNLOAD_HOST";
    static final String PORT_VAR = "CONTAINER_DOWNLOAD_PORT";

    private final Environment env;
    private final ClientWriter cw;
    private IFlightRecorderService service;
    private final NetworkResolver resolver;
    private final NanoHTTPD server;
    private final Map<String, IRecordingDescriptor> recordings = new ConcurrentHashMap<>();
    private final Map<String, Integer> downloadCounts = new ConcurrentHashMap<>();

    RecordingExporter(Environment env, ClientWriter cw, NetworkResolver resolver) {
        this.env = env;
        this.cw = cw;
        this.resolver = resolver;
        this.server = new ServerImpl();
    }

    // Testing-only constructor
    RecordingExporter(Environment env, ClientWriter cw, NetworkResolver resolver, NanoHTTPD server) {
        this.env = env;
        this.cw = cw;
        this.resolver = resolver;
        this.server = server;
    }

    @Override
    public void connectionChanged(JMCConnection connection) {
        if (connection == null) {
            stop();
            return;
        }

        this.service = connection.getService();
        try {
            restart();
        } catch (Exception e) {
            stop();
            throw new RuntimeException(e);
        }
    }

    public void start() throws IOException, FlightRecorderException {
        if (this.service != null && !this.server.isAlive()) {
            this.server.start();
            this.service.getAvailableRecordings().forEach(this::addRecording);

            cw.println(String.format("Recordings available at %s", this.getDownloadURL("$RECORDING_NAME")));
        }
    }

    public void stop() {
        if (this.server.isAlive()) {
            this.server.stop();
        }
        recordings.clear();
        downloadCounts.clear();
    }

    public void restart() throws IOException, FlightRecorderException {
        stop();
        start();
    }

    public void addRecording(IRecordingDescriptor descriptor) {
        recordings.put(descriptor.getName(), descriptor);
        downloadCounts.put(descriptor.getName(), 0);
    }

    public void removeRecording(IRecordingDescriptor descriptor) {
        recordings.remove(descriptor.getName());
        downloadCounts.remove(descriptor.getName());
    }

    public int getDownloadCount(String recordingName) {
        return this.downloadCounts.getOrDefault(recordingName, -1);
    }

    public URL getHostUrl() throws UnknownHostException, MalformedURLException, SocketException {
        String hostname = env.getEnv(HOST_VAR, resolver.getHostAddress());
        int port = Integer.parseInt(env.getEnv(PORT_VAR, "8080"));

        return new URL("http", hostname, port, "");
    }

    public String getDownloadURL(String recordingName)
            throws UnknownHostException, MalformedURLException, SocketException {
        return String.format("%s/%s", this.getHostUrl(), recordingName);
    }

    public String getReportURL(String recordingName)
            throws UnknownHostException, MalformedURLException, SocketException {
        return String.format("%s/reports/%s", this.getHostUrl(), recordingName);
    }

    private class ServerImpl extends NanoHTTPD {

        private final ExecutorService TRIM_WORKER = Executors.newSingleThreadExecutor();

        private ServerImpl() {
            super(Integer.parseInt(env.getEnv(PORT_VAR, "8080")));
        }

        @Override
        public Response serve(IHTTPSession session) {
            String requestUrl = session.getUri();
            Matcher recordingMatcher = RECORDING_NAME_PATTERN.matcher(requestUrl);
            Matcher reportMatcher = REPORT_PATTERN.matcher(requestUrl);
            if (recordingMatcher.find()) {
                return serveRecording(recordingMatcher);
            } else if (reportMatcher.find()) {
                return serveReport(reportMatcher);
            }
            return newNotFoundResponse(requestUrl);
        }

        private Response serveRecording(Matcher matcher) {
            String recordingName = matcher.group(1);
            if (!recordings.containsKey(recordingName)) {
                return newNotFoundResponse(recordingName);
            }
            try {
                return newFlightRecorderResponse(recordingName);
            } catch (Exception e) {
                e.printStackTrace();
                cw.println(e);
                return newCouldNotBeOpenedResponse(recordingName);
            }
        }

        private Response serveReport(Matcher matcher) {
            String recordingName = matcher.group(1);
            if (!recordings.containsKey(recordingName)) {
                return newNotFoundResponse(recordingName);
            }
            try {
                return newReportResponse(recordingName);
            } catch (Exception e) {
                e.printStackTrace();
                cw.println(e);
                return newCouldNotBeOpenedResponse(recordingName);
            }
        }

        @Override
        protected boolean useGzipWhenAccepted(Response r) {
            return true;
        }

        private Response newNotFoundResponse(String recordingName) {
            return newFixedLengthResponse(Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT,
                    String.format("%s not found", recordingName));
        }

        private Response newCouldNotBeOpenedResponse(String recordingName) {
            return newFixedLengthResponse(Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT,
                    String.format("%s could not be opened", recordingName));
        }

        private Response newFlightRecorderResponse(String recordingName) throws FlightRecorderException {
            Response r = new Response(Status.OK, "application/octet-stream",
                    service.openStream(recordings.get(recordingName), false), -1) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        downloadCounts.put(recordingName, downloadCounts.getOrDefault(recordingName, 0) + 1);
                    }
                }
            };
            r.addHeader("Access-Control-Allow-Origin", "*");
            return r;
        }

        private Response newReportResponse(String recordingName)
                throws IOException, CouldNotLoadRecordingException, FlightRecorderException {
            String report = JfrHtmlRulesReport.createReport(service.openStream(recordings.get(recordingName), false));
            Response response = newFixedLengthResponse(Status.OK, NanoHTTPD.MIME_HTML, report);
            response.addHeader("Access-Control-Allow-Origin", "*");

            // ugly hack for "trimming" created clones of specified recording. JMC service creates a clone of running
            // recordings before loading events to create the report, and these clones are erroneously left dangling.
            TRIM_WORKER.submit(() -> {
                try {
                    service.getAvailableRecordings()
                        .stream()
                        .filter(r -> r.getName().equals(String.format("Clone of %s", recordingName)))
                        .forEach(r -> {
                            try {
                                service.close(r);
                            } catch (FlightRecorderException ignored) {
                            }
                        });
                } catch (FlightRecorderException ignored) {
                }
            });

            return response;
        }

    }
}