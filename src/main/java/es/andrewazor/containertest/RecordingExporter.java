package es.andrewazor.containertest;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;

@Singleton
public class RecordingExporter implements ConnectionListener {

    private static final String HOST_VAR = "CONTAINER_DOWNLOAD_HOST";
    private static final String PORT_VAR = "CONTAINER_DOWNLOAD_PORT";

    private IFlightRecorderService service;
    private final NetworkResolver resolver;
    private final NanoHTTPD server;
    private final Map<String, IRecordingDescriptor> recordings = new ConcurrentHashMap<>();
    private final Map<String, Integer> downloadCounts = new ConcurrentHashMap<>();

    @Inject
    public RecordingExporter(NetworkResolver resolver) {
        this.resolver = resolver;
        this.server = new ServerImpl();
    }

    // Testing-only constructor
    RecordingExporter(NetworkResolver resolver, NanoHTTPD server) {
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

    public int getDownloadCount(String recordingName) {
        return this.downloadCounts.getOrDefault(recordingName, -1);
    }

    public URL getHostUrl() throws UnknownHostException, MalformedURLException, SocketException {
        String hostname = System.getenv(HOST_VAR);
        if (hostname == null || hostname.isEmpty()) {
            hostname = resolver.getHostAddress();
        }

        int port = 8080;
        String portProperty = System.getenv(PORT_VAR);
        if (portProperty != null && !portProperty.isEmpty()) {
            port = Integer.valueOf(portProperty);
        }

        return new URL("http", hostname, port, "");
    }

    public String getDownloadURL(String recordingName) throws UnknownHostException, MalformedURLException, SocketException {
        return String.format("%s/%s", this.getHostUrl(), recordingName);
    }

    private class ServerImpl extends NanoHTTPD {

        private ServerImpl() {
            super(8080);
        }

        @Override
        public Response serve(IHTTPSession session) {
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

    }
}