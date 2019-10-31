package com.redhat.rhjmc.containerjfr.net.web;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
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
import com.redhat.rhjmc.containerjfr.net.ConnectionListener;
import com.redhat.rhjmc.containerjfr.net.HttpServer;
import com.redhat.rhjmc.containerjfr.net.NetworkConfiguration;
import com.redhat.rhjmc.containerjfr.net.internal.reports.ReportGenerator;

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import org.openjdk.jmc.flightrecorder.CouldNotLoadRecordingException;
import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response.Status;

public class WebServer implements ConnectionListener {

    // TODO extract the name pattern (here and AbstractConnectedCommand) to shared utility
    private static final Pattern RECORDING_NAME_PATTERN = Pattern.compile("^/recordings/([\\w-_]+)(?:\\.jfr)?$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    private static final Pattern REPORT_PATTERN = Pattern.compile("^/reports/([\\w-_.]+)$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);
    private static final Pattern CLIENT_PATTERN = Pattern.compile("^/(.*)$", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    private static final String GRAFANA_DASHBOARD_ENV = "GRAFANA_DASHBOARD_URL";
    private static final String GRAFANA_DATASOURCE_ENV = "GRAFANA_DATASOURCE_URL";
    private static final String MIME_TYPE_JSON = "application/json";
    private static final String MIME_TYPE_HTML = "text/html";
    private static final String MIME_TYPE_PLAINTEXT = "text/plain";
    private static final String MIME_TYPE_OCTET_STREAM = "application/octet-stream";

    private final ExecutorService TRIM_WORKER = Executors.newSingleThreadExecutor();

    private final HttpServer server;
    private final NetworkConfiguration netConf;
    private final Environment env;
    private final Path savedRecordingsPath;
    private final Logger logger;
    private IFlightRecorderService service;
//    private final NanoHTTPD server;

    private final Map<String, IRecordingDescriptor> recordings = new ConcurrentHashMap<>();
    private final Map<String, Integer> downloadCounts = new ConcurrentHashMap<>();
    private final ReportGenerator reportGenerator;

    WebServer(HttpServer server, NetworkConfiguration netConf, Environment env, Path savedRecordingsPath, ReportGenerator reportGenerator, Logger logger) {
        this.server = server;
        this.netConf = netConf;
        this.env = env;
        this.savedRecordingsPath = savedRecordingsPath;
        this.logger = logger;
        this.reportGenerator = reportGenerator;
//        this.server = new ServerImpl();
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

    public void start() throws FlightRecorderException {
        server.start();
        if (this.service != null) {
            this.service.getAvailableRecordings().forEach(this::addRecording);
        }

        Router router = Router.router(server.getVertx()); // a vertx is only available after server started

        Handler<RoutingContext> failureHandler = ctx -> {
            HttpStatusException exception;
            if (ctx.failure() instanceof HttpStatusException) {
                exception = (HttpStatusException) ctx.failure();
            } else {
                exception = new HttpStatusException(500, ctx.failure());
            }

            if (exception.getStatusCode() < 500) {
                logger.warn(exception);
            } else {
                logger.error(exception);
            }

            String payload = exception.getPayload() != null ? exception.getPayload() : exception.getMessage();
            ctx.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, MIME_TYPE_PLAINTEXT)
                    .setStatusCode(exception.getStatusCode())
                    .setStatusMessage(exception.getMessage())
                    .end(payload);
        };

        router.get("/clienturl").handler(ctx -> {
            ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, MIME_TYPE_JSON);
            try {
                endWithClientUrl(ctx.response());
            } catch (SocketException | UnknownHostException e) {
                throw new HttpStatusException(500, e);
            }
        }).failureHandler(failureHandler);

        router.get("/grafana_datasource_url").handler(ctx -> {
            ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, MIME_TYPE_JSON);
            endWithGrafanaDatasourceUrl(ctx.response());
        }).failureHandler(failureHandler);

        router.get("/grafana_dashboard_url").handler(ctx -> {
            ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, MIME_TYPE_JSON);
            endWithGrafanaDashboardUrl(ctx.response());
        }).failureHandler(failureHandler);

        router.get("/recordings/:name").blockingHandler(ctx -> {
            String recordingName = ctx.pathParam("name");
            try {
                Optional<InputStream> recording = getRecordingInputStream(recordingName);
                if (recording.isEmpty()) {
                    throw new HttpStatusException(404, String.format("%s not found", recordingName));
                }

                ctx.response().setChunked(true);
                ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, MIME_TYPE_OCTET_STREAM);
                writeInputStream(recording.get(), ctx.response())
                        .endHandler((e) -> downloadCounts.merge(recordingName, 1, Integer::sum))
                        .end();
                recording.get().close();
            } catch (FlightRecorderException e) {
                throw new HttpStatusException(500, String.format("%s could not be opened", recordingName), e);
            } catch (IOException e) {
                throw new HttpStatusException(500, e);
            }
        }, false).failureHandler(failureHandler);

        router.get("/reports/:name").blockingHandler(ctx -> {
            String recordingName = ctx.pathParam("name");
            try {
                Optional<InputStream> recording = getRecordingInputStream(recordingName);
                if (recording.isEmpty()) {
                    throw new HttpStatusException(404, String.format("%s not found", recordingName));
                }

                ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, MIME_TYPE_HTML);
                endWithReport(recording.get(), recordingName, ctx.response());
                recording.get().close();
            } catch (FlightRecorderException e) {
                throw new HttpStatusException(500, String.format("%s could not be opened", recordingName), e);
            } catch (CouldNotLoadRecordingException e) {
                throw new HttpStatusException(500, String.format("%s could not be loaded", recordingName), e);
            } catch (IOException e) {
                throw new HttpStatusException(500, e);
            }
        }).failureHandler(failureHandler);

        router.get("/*")
                .handler(StaticHandler.create(WebServer.class.getPackageName().replaceAll("\\.", "/")));

        this.server.requestHandler(req -> {
            Instant start = Instant.now();
            req.response().endHandler((res) -> logger.info(String.format("(%s): %s %s %d %dms",
                    req.remoteAddress().toString(),
                    req.method().toString(),
                    req.path(),
                    req.response().getStatusCode(),
                    Duration.between(start, Instant.now()).toMillis()
            )));
            router.handle(req);
        });
    }

    public void stop() {
        this.server.requestHandler(null);

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

    private void endWithJsonKeyValue(String key, String value, HttpServerResponse response) {
        response.end(String.format("{\"%s\":\"%s\"}", key, value));
    }

    private void endWithClientUrl(HttpServerResponse response) throws SocketException, UnknownHostException {
//        endWithJsonKeyValue("clientUrl", String.format("ws://%s:%d/command", netConf.getCommandChannelHost(), netConf.getExternalCommandChannelPort()), response);
        endWithJsonKeyValue("clientUrl", String.format("ws://%s:%d/command", netConf.getWebServerHost(), netConf.getExternalWebServerPort()), response);
    }

    private void endWithGrafanaDatasourceUrl(HttpServerResponse response) {
        endWithJsonKeyValue("grafanaDatasourceUrl", env.getEnv(GRAFANA_DATASOURCE_ENV, ""), response);
    }

    private void endWithGrafanaDashboardUrl(HttpServerResponse response) {
        endWithJsonKeyValue("grafanaDashboardUrl", env.getEnv(GRAFANA_DASHBOARD_ENV, ""), response);
    }

    private HttpServerResponse writeInputStream(InputStream inputStream, HttpServerResponse response) throws IOException {
        // blocking function, must be calling from a blocking handler
        byte[] buff = new byte[1024 * 64]; // 64 KB
        int n;
        while (true) {
            n = inputStream.read(buff);
            if (n == -1) {
                break;
            }

            response.write(Buffer.buffer().appendBytes(buff, 0, n));
        }

        return response;
    }

    private void endWithReport(InputStream recording, String recordingName, HttpServerResponse response) throws IOException, CouldNotLoadRecordingException {
        // blocking function, must be calling from a blocking handler
        try (recording) {
            response.end(reportGenerator.generateReport(recording));

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
        }
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

        private Response serveClientAsset(String assetName) {
            InputStream assetStream = WebServer.class.getResourceAsStream(assetName);
            if (assetStream == null) {
                return newFixedLengthResponse(Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT,
                        String.format("%s not found", assetName));
            }
            return new Response(Status.OK, NanoHTTPD.getMimeTypeForFile(assetName), assetStream, -1) {
                @Override
                public void close() throws IOException {
                    try (assetStream) {
                        super.close();
                    }
                }
            };
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
            return new Response(Status.OK, "application/octet-stream", recording, -1) {
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

        private Response newReportResponse(String recordingName, InputStream recording) throws IOException, CouldNotLoadRecordingException {
            try (recording) {
                Response response = serveTextResponse(reportGenerator.generateReport(recording));
                response.setMimeType(NanoHTTPD.MIME_HTML);

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
