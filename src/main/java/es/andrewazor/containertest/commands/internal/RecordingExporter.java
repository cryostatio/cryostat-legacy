package es.andrewazor.containertest.commands.internal;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;

public class RecordingExporter {

    private static final String HOST_PROPERTY = "es.andrewazor.containertest.download.host";
    private static final String PORT_PROPERTY = "es.andrewazor.containertest.download.port";

    private final IFlightRecorderService service;
    private final ServerImpl server;
    private final Map<String, IRecordingDescriptor> recordings = new ConcurrentHashMap<>();
    private final Map<String, Integer> downloadCounts = new ConcurrentHashMap<>();

    public RecordingExporter(IFlightRecorderService service) throws IOException {
        this.service = service;
        this.server = new ServerImpl();
    }

    void start() throws IOException, FlightRecorderException {
        if (!this.server.wasStarted()) {
            this.server.start();
            this.service.getAvailableRecordings().forEach(this::addRecording);

            System.out.println(String.format("Recordings available at %s", this.getDownloadURL("$RECORDING_NAME")));
        }
    }

    void stop() {
        this.server.stop();
    }

    public void addRecording(IRecordingDescriptor descriptor) {
        recordings.put(descriptor.getName(), descriptor);
    }

    public int getDownloadCount(String recordingName) {
        return this.downloadCounts.getOrDefault(recordingName, -1);
    }

    public String getHostName() throws SocketException, UnknownHostException {
        return getLocalAddressProperty(InetAddress::getHostName);
    }

    public String getHostAddress() throws SocketException, UnknownHostException {
        return getLocalAddressProperty(InetAddress::getHostAddress);
    }

    private <T> T getLocalAddressProperty(Function<InetAddress, T> fn) throws SocketException, UnknownHostException {
        try (DatagramSocket s = new DatagramSocket()) {
            s.connect(InetAddress.getByName("1.1.1.1"), 80);
            return fn.apply(s.getLocalAddress());
        }
    }

    public URL getHostUrl() throws UnknownHostException, MalformedURLException, SocketException {
        String hostname = System.getProperty(HOST_PROPERTY);
        if (hostname == null || hostname.isEmpty()) {
            hostname = getHostAddress();
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

    private class ServerImpl extends NanoHTTPD {
        private ServerImpl() throws IOException {
            super(8080);
        }

        @Override
        public Response serve(IHTTPSession session) {
            String recordingName = session.getUri().substring(1);
            if (!recordings.containsKey(recordingName)) {
                return newFixedLengthResponse(Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT,
                        String.format("%s not found", recordingName));
            }
            try {
                return newFlightRecorderResponse(recordingName);
            } catch (FlightRecorderException fre) {
                fre.printStackTrace();
                return newFixedLengthResponse(Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT,
                        String.format("%s could not be opened", recordingName));
            }
        }

        @Override
        protected boolean useGzipWhenAccepted(Response r) {
            return true;
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