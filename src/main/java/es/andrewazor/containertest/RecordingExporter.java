package es.andrewazor.containertest;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;

public class RecordingExporter {

    private final IFlightRecorderService service;
    private final ServerImpl server;
    private final Map<String, IRecordingDescriptor> recordings = new HashMap<>();
    private final Map<String, Integer> downloadCounts = new HashMap<>();

    RecordingExporter(IFlightRecorderService service) throws IOException {
        this.service = service;
        this.server = new ServerImpl();

        System.out.println(String.format("Recordings available at http://%s:%d/$RECORDING_NAME",
                InetAddress.getLocalHost().getHostAddress(), server.getListeningPort()));
    }

    void start() throws IOException, FlightRecorderException {
        if (!this.server.wasStarted()) {
            this.server.start();
            this.service.getAvailableRecordings().forEach(this::addRecording);
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

    public String getDownloadURL(String recordingName) throws UnknownHostException {
        return String.format("http://%s:%d/%s", InetAddress.getLocalHost().getHostAddress(), server.getListeningPort(), recordingName);
    }

    private class ServerImpl extends NanoHTTPD {
        private ServerImpl() throws IOException {
            super(8080);
            start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
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