package com.redhat.rhjmc.containerjfr.net;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.net.internal.reports.ReportGenerator;

import org.openjdk.jmc.flightrecorder.CouldNotLoadRecordingException;
import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;

public class WebServer implements ConnectionListener {

    // TODO extract the name pattern (here and AbstractConnectedCommand) to shared utility
    private static final Pattern RECORDING_NAME_PATTERN = Pattern.compile("^/recordings/([\\w-_]+)(?:\\.jfr)?$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    private static final Pattern REPORT_PATTERN = Pattern.compile("^/reports/([\\w-_.]+)$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    private static final Pattern CLIENT_PATTERN = Pattern.compile("^/(.*)$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    private static final String GRAFANA_DASHBOARD_ENV = "GRAFANA_DASHBOARD_URL";
    private static final String GRAFANA_DATASOURCE_ENV = "GRAFANA_DATASOURCE_URL";

    private final NetworkConfiguration netConf;
    private final Environment env;
    private final Path savedRecordingsPath;
    private final Logger logger;
    private IFlightRecorderService service;
    private final NanoHTTPD server;
    private final Map<String, IRecordingDescriptor> recordings = new ConcurrentHashMap<>();
    private final Map<String, Integer> downloadCounts = new ConcurrentHashMap<>();
    private final ReportGenerator reportGenerator;

    WebServer(NetworkConfiguration netConf, Environment env, Path savedRecordingsPath, ReportGenerator reportGenerator, Logger logger) {
        this.netConf = netConf;
        this.env = env;
        this.savedRecordingsPath = savedRecordingsPath;
        this.logger = logger;
        this.reportGenerator = reportGenerator;
        this.server = new ServerImpl();
    }

    // Testing-only constructor
    WebServer(NetworkConfiguration netConf, Environment env, Path savedRecordingsPath, ReportGenerator reportGenerator, Logger logger, NanoHTTPD server) {
        this.netConf = netConf;
        this.env = env;
        this.savedRecordingsPath = savedRecordingsPath;
        this.logger = logger;
        this.reportGenerator = reportGenerator;
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
        return new URL("http", netConf.getWebServerHost(), netConf.getExternalWebServerPort(), "");
    }

    public String getDownloadURL(String recordingName)
            throws UnknownHostException, MalformedURLException, SocketException {
        return String.format("%s/recordings/%s", this.getHostUrl(), recordingName);
    }

    public String getReportURL(String recordingName)
            throws UnknownHostException, MalformedURLException, SocketException {
        return String.format("%s/reports/%s", this.getHostUrl(), recordingName);
    }

    private class ServerImpl extends NanoHTTPD {

        private final ExecutorService TRIM_WORKER = Executors.newSingleThreadExecutor();

        private ServerImpl() {
            super(netConf.getInternalWebServerPort());
        }

        @Override
        public void start() throws IOException {
            setAsyncRunner(new PooledAsyncRunner());
            super.start();
        }

        @Override
        public Response serve(IHTTPSession session) {
            String requestUrl = session.getUri();
            logger.info("Serving " + requestUrl);
            Matcher recordingMatcher = RECORDING_NAME_PATTERN.matcher(requestUrl);
            Matcher reportMatcher = REPORT_PATTERN.matcher(requestUrl);
            Matcher clientMatcher = CLIENT_PATTERN.matcher(requestUrl);
            if (requestUrl.equals("/")) {
                return serveClientIndex();
            } else if (requestUrl.endsWith("/clienturl")) {
                try {
                    return serveJsonKeyValueResponse("clientUrl", String.format("ws://%s:%d/command", netConf.getCommandChannelHost(), netConf.getExternalCommandChannelPort()));
                } catch (UnknownHostException | SocketException e) {
                    logger.error(e.getLocalizedMessage());
                    return newFixedLengthResponse(Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, e.getLocalizedMessage());
                }
            } else if (requestUrl.equals("/grafana_datasource_url")) {
                return serveJsonKeyValueResponse("grafanaDatasourceUrl", env.getEnv(GRAFANA_DATASOURCE_ENV, ""));
            } else if (requestUrl.equals("/grafana_dashboard_url")) {
                return serveJsonKeyValueResponse("grafanaDashboardUrl", env.getEnv(GRAFANA_DASHBOARD_ENV, ""));
            } else if (recordingMatcher.find()) {
                return serveRecording(recordingMatcher);
            } else if (reportMatcher.find()) {
                return serveReport(reportMatcher);
            } else if (clientMatcher.find()) {
                return serveClient(clientMatcher);
            }
            return newNotFoundResponse(requestUrl);
        }

        private Response serveJsonKeyValueResponse(String key, String value) {
            return serveTextResponse(String.format("{\"%s\":\"%s\"}", key, value));
        }

        private Response serveTextResponse(String message) {
            return newFixedLengthResponse(Status.OK, NanoHTTPD.MIME_PLAINTEXT, message);
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
                logger.error(e);
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
                logger.error(e);
                return newCouldNotBeOpenedResponse(recordingName);
            }
        }

        private Response serveClient(Matcher matcher) {
            return serveClientAsset(matcher.group(1));
        }

        private Response serveClientIndex() {
            return serveClientAsset("index.html");
        }

        @SuppressFBWarnings("DMI_HARDCODED_ABSOLUTE_FILENAME")
        private Response serveClientAsset(String assetName) {
            Path assetPath = Paths.get("/", "web-client", assetName);
            if (!assetPath.toFile().isFile()) {
                return newFixedLengthResponse(Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT,
                        String.format("%s not found", assetName));
            }
            try {
                String mime = NanoHTTPD.getMimeTypeForFile(assetPath.toUri().toString());
                InputStream assetStream = Files.newInputStream(assetPath);
                Response r = new Response(Status.OK, mime, assetStream, -1) {
                    @Override
                    public void close() throws IOException {
                        try (assetStream) {
                            super.close();
                        }
                    }
                };
                r.addHeader("Access-Control-Allow-Origin", "*");
                return r;
            } catch (IOException e) {
                return newFixedLengthResponse(Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT,
                        String.format("%s could not be opened", assetName));
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
                logger.error(e);
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
                Response response = serveTextResponse(reportGenerator.generateReport(recording));
                response.setMimeType(NanoHTTPD.MIME_HTML);
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
                                    } catch (FlightRecorderException fre) {
                                        logger.debug(fre);
                                    }
                                });
                    } catch (FlightRecorderException fre) {
                        logger.debug(fre);
                    }
                });

                return response;
            }
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
