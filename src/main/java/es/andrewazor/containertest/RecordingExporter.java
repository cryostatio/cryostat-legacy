package es.andrewazor.containertest;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;

@Singleton
public class RecordingExporter implements ConnectionListener {

    private static final String HOST_PROPERTY = "es.andrewazor.containertest.download.host";
    private static final String PORT_PROPERTY = "es.andrewazor.containertest.download.port";

    private static final Pattern SNAPSHOT_PATTERN = Pattern.compile("/snapshot/(\\d+)");

    private IFlightRecorderService service;
    private final NetworkResolver resolver;
    private final ServerImpl server;
    private final Map<String, IRecordingDescriptor> recordings = new ConcurrentHashMap<>();
    private final Map<String, Integer> downloadCounts = new ConcurrentHashMap<>();

    @Inject public RecordingExporter() {
        this.resolver = new NetworkResolver();
        try {
            this.server = new ServerImpl();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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

            System.out.println(String.format("Recordings available at %s", this.getDownloadURL("$RECORDING_NAME")));
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
    }

    public void addSnapshot(IRecordingDescriptor descriptor) {
        recordings.put(getSnapshotIdentifier(descriptor), descriptor);
    }

    public int getDownloadCount(String recordingName) {
        return this.downloadCounts.getOrDefault(recordingName, -1);
    }

    public URL getHostUrl() throws UnknownHostException, MalformedURLException, SocketException {
        String hostname = System.getProperty(HOST_PROPERTY);
        if (hostname == null || hostname.isEmpty()) {
            hostname = resolver.getHostAddress();
        }

        int port = 8080;
        String portProperty = System.getProperty(PORT_PROPERTY);
        if (portProperty != null && !portProperty.isEmpty()) {
            port = Integer.valueOf(portProperty);
        }

        return new URL("http", hostname, port, "");
    }

    public String getDownloadURL(String recordingName) throws UnknownHostException, MalformedURLException, SocketException {
        return String.format("%s/%s", this.getHostUrl(), recordingName);
    }

    private static String getSnapshotIdentifier(IRecordingDescriptor descriptor) {
        return getSnapshotIdentifier(descriptor.getId());
    }

    private static String getSnapshotIdentifier(long id) {
        return "snapshot-" + id;
    }

    private class ServerImpl extends NanoHTTPD {

        private ServerImpl() throws IOException {
            super(8080);
        }

        @Override
        public Response serve(IHTTPSession session) {
            Matcher matcher = SNAPSHOT_PATTERN.matcher(session.getUri());
            boolean isSnapshotRequest = matcher.matches();
            if (isSnapshotRequest) {
                int id = Integer.parseInt(matcher.group(1));
                return serveSnapshot(session, id);
            } else {
                return serveRecording(session);
            }
        }

        private Response serveRecording(IHTTPSession session) {
            String recordingName = session.getUri().substring(1);
            if (!recordings.containsKey(recordingName)) {
                return newNotFoundResponse(recordingName);
            }
            try {
                return newFlightRecorderResponse(recordingName);
            } catch (FlightRecorderException fre) {
                fre.printStackTrace();
                return newCouldNotBeOpenedResponse(recordingName);
            }
        }

        private Response serveSnapshot(IHTTPSession session, int id) {
            String identifier = getSnapshotIdentifier(id);
            try {
                if (!recordings.containsKey(identifier)) {
                    return newNotFoundResponse(identifier);
                }
                return newSnapshotResponse(identifier);
            } catch (FlightRecorderException fre) {
                fre.printStackTrace();
                return newCouldNotBeOpenedResponse(identifier);
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
            return new Response(Status.OK, "application/octet-stream",
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
        }

        private Response newSnapshotResponse(String identifier) throws FlightRecorderException {
            return new Response(Status.OK, "application/octet-stream",
                    service.openStream(recordings.get(identifier), false), -1) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        downloadCounts.put(identifier, downloadCounts.getOrDefault(identifier, 0) + 1);
                    }
                }
            };
        }
    }
}