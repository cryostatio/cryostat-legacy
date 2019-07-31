package com.redhat.rhjmc.containerjfr.net;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.core.util.log.Logger;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.openjdk.jmc.flightrecorder.CouldNotLoadRecordingException;
import org.openjdk.jmc.flightrecorder.rules.report.html.JfrHtmlRulesReport;
import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;

public class RecordingExporter implements ConnectionListener {

    // TODO extract the name pattern (here and AbstractConnectedCommand) to shared
    // utility
    private static final Pattern RECORDING_NAME_PATTERN = Pattern.compile("^/([\\w-_]+)(?:\\.jfr)?$",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    private static final Pattern REPORT_PATTERN = Pattern.compile("^/reports/([\\w-_.]+)$", Pattern.MULTILINE);
    static final String HOST_VAR = "CONTAINER_JFR_DOWNLOAD_HOST";
    static final String PORT_VAR = "CONTAINER_JFR_DOWNLOAD_PORT";

    private final Path savedRecordingsPath;
    private final Environment env;
    private final ClientWriter cw;
    private IFlightRecorderService service;
    private final NetworkResolver resolver;
    private final NanoHTTPD server;
    private final Map<String, IRecordingDescriptor> recordings = new ConcurrentHashMap<>();
    private final Map<String, Integer> downloadCounts = new ConcurrentHashMap<>();

    RecordingExporter(Path savedRecordingsPath, Environment env, ClientWriter cw, NetworkResolver resolver) {
        this.savedRecordingsPath = savedRecordingsPath;
        this.env = env;
        this.cw = cw;
        this.resolver = resolver;
        this.server = new ServerImpl();
    }

    // Testing-only constructor
    RecordingExporter(Path savedRecordingsPath, Environment env, ClientWriter cw, NetworkResolver resolver, NanoHTTPD server) {
        this.savedRecordingsPath = savedRecordingsPath;
        this.env = env;
        this.cw = cw;
        this.resolver = resolver;
        this.server = server;
    }

    @Override
    public void connectionChanged(JFRConnection connection) {
        if (connection != null) {
            this.service = connection.getService();
        } else {
            this.service = null;
        }

        try {
            restart();
        } catch (Exception e) {
            stop();
            throw new RuntimeException(e);
        }
    }

    public void start() throws IOException, FlightRecorderException {
        if (!this.server.isAlive()) {
            this.server.start();
            if (this.service != null) {
                this.service.getAvailableRecordings().forEach(this::addRecording);
            }
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
        public void start() throws IOException {
            setAsyncRunner(new PooledAsyncRunner());
            super.start();
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
            try {
                Optional<InputStream> recording = getRecordingInputStream(recordingName);
                if (recording.isPresent()) {
                    return newFlightRecorderResponse(recordingName, recording.get());
                }
                return newNotFoundResponse(recordingName);
            } catch (Exception e) {
                cw.println(e);
                return newCouldNotBeOpenedResponse(recordingName);
            }
        }

        private Response serveReport(Matcher matcher) {
            String recordingName = matcher.group(1);
            try {
                Optional<InputStream> recording = getRecordingInputStream(recordingName);
                if (recording.isPresent()) {
                    return newReportResponse(recordingName, recording.get());
                } else {
                    return newNotFoundResponse(recordingName);
                }
            } catch (Exception e) {
                cw.println(e);
                return newCouldNotBeOpenedResponse(recordingName);
            }
        }

        private Optional<InputStream> getRecordingInputStream(String recordingName) throws FlightRecorderException {
            if (recordings.containsKey(recordingName)) {
                return Optional.of(service.openStream(recordings.get(recordingName), false));
            }
            try {
                Optional<Path> savedRecording = Files.list(savedRecordingsPath)
                        .filter(saved -> saved.getFileName().toFile().getName().equals(recordingName) || saved.getFileName().toFile().getName().equals(recordingName + ".jfr"))
                        .findFirst();
                if (savedRecording.isPresent()) {
                    return Optional.of(Files.newInputStream(savedRecording.get(), StandardOpenOption.READ));
                }
            } catch (Exception e) {
                cw.println(e);
            }
            return Optional.empty();
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

        private Response newFlightRecorderResponse(String recordingName, InputStream recording) {
            Response r = new Response(Status.OK, "application/octet-stream", recording, -1) {
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

        private Response newReportResponse(String recordingName, InputStream recording) throws IOException, CouldNotLoadRecordingException {
            try (recording) {
                String report = JfrHtmlRulesReport.createReport(recording);
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

		public ExecutorService getTRIM_WORKER() {
			return TRIM_WORKER;
		}
    }

    private static class PooledAsyncRunner implements NanoHTTPD.AsyncRunner {

        private final List<NanoHTTPD.ClientHandler> handlers = new ArrayList<>();
        private final ReentrantLock lock = new ReentrantLock();
        private final ExecutorService executor = Executors.newCachedThreadPool(new ThreadFactory() {
            private final ThreadFactory defaultFactory = Executors.defaultThreadFactory();
            private long numWorkers;

            @Override
            public Thread newThread(Runnable runnable) {
                Thread t = defaultFactory.newThread(runnable);
                t.setName(String.format("NanoHttpd Worker #%d", numWorkers++));
                return t;
            }
        });
        private final Map<NanoHTTPD.ClientHandler, Future<?>> futures = new HashMap<>();

        @Override
        public void closeAll() {
            try {
                lock.lock();
                handlers.forEach(NanoHTTPD.ClientHandler::close);
                executor.shutdownNow();
                futures.clear();
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void closed(NanoHTTPD.ClientHandler clientHandler) {
            try {
                lock.lock();
                handlers.remove(clientHandler);
                futures.get(clientHandler).cancel(true);
            } finally {
                lock.unlock();
            }
        }

        @Override
        public void exec(NanoHTTPD.ClientHandler clientHandler) {
            try {
                lock.lock();
                handlers.add(clientHandler);
                futures.put(clientHandler, executor.submit(clientHandler));
            } finally {
                lock.unlock();
            }
        }
    }
}
