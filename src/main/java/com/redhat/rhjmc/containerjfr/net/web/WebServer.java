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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openjdk.jmc.flightrecorder.CouldNotLoadRecordingException;
import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.net.AuthManager;
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
import org.apache.commons.lang3.StringUtils;

public class WebServer implements ConnectionListener {

    private static final String GRAFANA_DASHBOARD_ENV = "GRAFANA_DASHBOARD_URL";
    private static final String GRAFANA_DATASOURCE_ENV = "GRAFANA_DATASOURCE_URL";
    private static final String USE_LOW_MEM_PRESSURE_STREAMING_ENV =
            "USE_LOW_MEM_PRESSURE_STREAMING";

    private static final String MIME_TYPE_JSON = "application/json";
    private static final String MIME_TYPE_HTML = "text/html";
    private static final String MIME_TYPE_PLAINTEXT = "text/plain";
    private static final String MIME_TYPE_OCTET_STREAM = "application/octet-stream";

    private static final int WRITE_BUFFER_SIZE = 64 * 1024; // 64 KB

    private final HttpServer server;
    private final NetworkConfiguration netConf;
    private final Environment env;
    private final Path savedRecordingsPath;
    private final AuthManager auth;
    private final Logger logger;
    private IFlightRecorderService service;

    private final Map<String, IRecordingDescriptor> recordings = new ConcurrentHashMap<>();
    private final Map<String, Integer> downloadCounts = new ConcurrentHashMap<>();
    private final ReportGenerator reportGenerator;

    WebServer(
            HttpServer server,
            NetworkConfiguration netConf,
            Environment env,
            Path savedRecordingsPath,
            AuthManager auth,
            ReportGenerator reportGenerator,
            Logger logger) {
        this.server = server;
        this.netConf = netConf;
        this.env = env;
        this.savedRecordingsPath = savedRecordingsPath;
        this.auth = auth;
        this.logger = logger;
        this.reportGenerator = reportGenerator;

        if (env.hasEnv(USE_LOW_MEM_PRESSURE_STREAMING_ENV)) {
            logger.info("low memory pressure streaming enabled for web server");
        } else {
            logger.info("low memory pressure streaming disabled for web server");
        }
    }

    private void refreshAvailableRecordings() throws FlightRecorderException {
        recordings.clear();
        downloadCounts.clear();

        if (this.service != null) {
            this.service.getAvailableRecordings().forEach(this::addRecording);
        }
    }

    @Override
    public void connectionChanged(JFRConnection connection) {
        if (connection != null) {
            this.service = connection.getService();

            try {
                refreshAvailableRecordings();
            } catch (FlightRecorderException e) {
                logger.warn(e);
                throw new RuntimeException(e);
            }
        } else {
            this.service = null;
        }
    }

    public void start() throws FlightRecorderException, SocketException, UnknownHostException {
        if (this.server.isAlive()) {
            return;
        }

        refreshAvailableRecordings();
        server.start();

        Router router =
                Router.router(server.getVertx()); // a vertx is only available after server started

        // error page handler
        Handler<RoutingContext> failureHandler =
                ctx -> {
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

                    String payload =
                            exception.getPayload() != null
                                    ? exception.getPayload()
                                    : exception.getMessage();
                    ctx.response()
                            .putHeader(HttpHeaders.CONTENT_TYPE, MIME_TYPE_PLAINTEXT)
                            .setStatusCode(exception.getStatusCode())
                            .setStatusMessage(exception.getMessage())
                            .end(payload);
                };

        router.post("/auth").handler(this::handleAuthRequest).failureHandler(failureHandler);

        router.get("/clienturl")
                .handler(this::handleClientUrlRequest)
                .failureHandler(failureHandler);

        router.get("/grafana_datasource_url")
                .handler(this::handleGrafanaDatasourceUrlRequest)
                .failureHandler(failureHandler);

        router.get("/grafana_dashboard_url")
                .handler(this::handleGrafanaDashboardUrlRequest)
                .failureHandler(failureHandler);

        router.get("/recordings/:name")
                .blockingHandler(
                        ctx -> {
                            String recordingName = ctx.pathParam("name");
                            if (recordingName != null && recordingName.endsWith(".jfr")) {
                                recordingName =
                                        recordingName.substring(0, recordingName.length() - 4);
                            }
                            handleRecordingDownloadRequest(recordingName, ctx);
                        },
                        false)
                .failureHandler(failureHandler);

        router.get("/reports/:name")
                .blockingHandler(ctx -> this.handleReportPageRequest(ctx.pathParam("name"), ctx))
                .failureHandler(failureHandler);

        router.get("/*")
                .handler(
                        StaticHandler.create(
                                WebServer.class.getPackageName().replaceAll("\\.", "/")));

        this.server.requestHandler(
                req -> {
                    Instant start = Instant.now();
                    req.response()
                            .endHandler(
                                    (res) ->
                                            logger.info(
                                                    String.format(
                                                            "(%s): %s %s %d %dms",
                                                            req.remoteAddress().toString(),
                                                            req.method().toString(),
                                                            req.path(),
                                                            req.response().getStatusCode(),
                                                            Duration.between(start, Instant.now())
                                                                    .toMillis())));
                    router.handle(req);
                });
    }

    public void stop() {
        this.server.requestHandler(null);

        recordings.clear();
        downloadCounts.clear();
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
        return new URL(
                "http" + (server.isSsl() ? "s" : ""),
                netConf.getWebServerHost(),
                netConf.getExternalWebServerPort(),
                "");
    }

    public String getDownloadURL(String recordingName)
            throws UnknownHostException, MalformedURLException, SocketException {
        return String.format("%s/recordings/%s", this.getHostUrl(), recordingName);
    }

    public String getReportURL(String recordingName)
            throws UnknownHostException, MalformedURLException, SocketException {
        return String.format("%s/reports/%s", this.getHostUrl(), recordingName);
    }

    private Optional<DownloadDescriptor> getDownloadDescriptor(String recordingName)
            throws FlightRecorderException {
        if (recordings.containsKey(recordingName)) {
            return Optional.of(
                    new DownloadDescriptor(
                            service.openStream(recordings.get(recordingName), false), null));
        }
        try {
            // TODO refactor Files calls into FileSystem for testability
            Optional<Path> savedRecording =
                    Files.list(savedRecordingsPath)
                            .filter(
                                    saved ->
                                            saved.getFileName()
                                                            .toFile()
                                                            .getName()
                                                            .equals(recordingName)
                                                    || saved.getFileName()
                                                            .toFile()
                                                            .getName()
                                                            .equals(recordingName + ".jfr"))
                            .findFirst();
            if (savedRecording.isPresent()) {
                return Optional.of(
                        new DownloadDescriptor(
                                Files.newInputStream(savedRecording.get(), StandardOpenOption.READ),
                                Files.size(savedRecording.get())));
            }
        } catch (Exception e) {
            logger.error(e);
        }
        return Optional.empty();
    }

    private void endWithJsonKeyValue(String key, String value, HttpServerResponse response) {
        response.end(String.format("{\"%s\":\"%s\"}", key, value));
    }

    private HttpServerResponse writeInputStreamLowMemPressure(
            InputStream inputStream, HttpServerResponse response) throws IOException {
        // blocking function, must be called from a blocking handler
        byte[] buff = new byte[WRITE_BUFFER_SIZE];
        Buffer chunk = Buffer.buffer();

        ExecutorService worker = Executors.newSingleThreadExecutor();
        CompletableFuture<Void> future = new CompletableFuture<>();
        worker.submit(
                new Runnable() {
                    @Override
                    public void run() {
                        {
                            int n;
                            try {
                                n = inputStream.read(buff);
                            } catch (IOException e) {
                                future.completeExceptionally(e);
                                return;
                            }

                            if (n == -1) {
                                future.complete(null);
                                return;
                            }

                            chunk.setBytes(0, buff, 0, n);
                            response.write(
                                    chunk.slice(0, n),
                                    (res) -> {
                                        if (res.failed()) {
                                            future.completeExceptionally(res.cause());
                                            return;
                                        }
                                        worker.submit(
                                                this); // recursive call on this runnable itself
                                    });
                        }
                    }
                });

        try {
            future.join();
            worker.shutdownNow();
        } catch (CompletionException e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            } else {
                throw e;
            }
        }

        return response;
    }

    private HttpServerResponse writeInputStream(
            InputStream inputStream, HttpServerResponse response) throws IOException {
        // blocking function, must be called from a blocking handler
        byte[] buff = new byte[WRITE_BUFFER_SIZE]; // 64 KB
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

    void handleAuthRequest(RoutingContext ctx) {
        boolean valid =
                auth.validateToken(
                        () -> {
                            String authorization = ctx.request().getHeader("Authorization");
                            Pattern basicPattern = Pattern.compile("Bearer (.*)");
                            if (StringUtils.isBlank(authorization)) {
                                throw new HttpStatusException(401);
                            }
                            Matcher matcher = basicPattern.matcher(authorization);
                            if (!matcher.matches()) {
                                throw new HttpStatusException(401);
                            }
                            return matcher.group(1);
                        });
        if (valid) {
            ctx.response().setStatusCode(200);
            endWithJsonKeyValue("valid", "true", ctx.response());
        } else {
            ctx.response().setStatusCode(401);
            endWithJsonKeyValue("valid", "false", ctx.response());
        }
    }

    void handleClientUrlRequest(RoutingContext ctx) {
        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, MIME_TYPE_JSON);
        try {
            endWithJsonKeyValue(
                    "clientUrl",
                    String.format(
                            "%s://%s:%d/command",
                            server.isSsl() ? "wss" : "ws",
                            netConf.getWebServerHost(),
                            netConf.getExternalWebServerPort()),
                    ctx.response());
        } catch (SocketException | UnknownHostException e) {
            throw new HttpStatusException(500, e);
        }
    }

    void handleGrafanaDatasourceUrlRequest(RoutingContext ctx) {
        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, MIME_TYPE_JSON);
        endWithJsonKeyValue(
                "grafanaDatasourceUrl", env.getEnv(GRAFANA_DATASOURCE_ENV, ""), ctx.response());
    }

    void handleGrafanaDashboardUrlRequest(RoutingContext ctx) {
        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, MIME_TYPE_JSON);
        endWithJsonKeyValue(
                "grafanaDashboardUrl", env.getEnv(GRAFANA_DASHBOARD_ENV, ""), ctx.response());
    }

    void handleRecordingDownloadRequest(String recordingName, RoutingContext ctx) {
        try {
            Optional<DownloadDescriptor> descriptor = getDownloadDescriptor(recordingName);
            if (descriptor.isEmpty()) {
                throw new HttpStatusException(404, String.format("%s not found", recordingName));
            }

            ctx.response().setChunked(true);
            ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, MIME_TYPE_OCTET_STREAM);
            ctx.response().endHandler((e) -> downloadCounts.merge(recordingName, 1, Integer::sum));
            descriptor
                    .get()
                    .bytes
                    .ifPresent(
                            b ->
                                    ctx.response()
                                            .putHeader(
                                                    HttpHeaders.CONTENT_LENGTH, Long.toString(b)));
            try (InputStream stream = descriptor.get().stream) {
                if (env.hasEnv(USE_LOW_MEM_PRESSURE_STREAMING_ENV)) {
                    writeInputStreamLowMemPressure(stream, ctx.response());
                } else {
                    writeInputStream(stream, ctx.response());
                }
                ctx.response().end();
            }
        } catch (FlightRecorderException e) {
            throw new HttpStatusException(
                    500, String.format("%s could not be opened", recordingName), e);
        } catch (IOException e) {
            throw new HttpStatusException(500, e);
        }
    }

    void handleReportPageRequest(String recordingName, RoutingContext ctx) {
        try {
            Optional<DownloadDescriptor> descriptor = getDownloadDescriptor(recordingName);
            if (descriptor.isEmpty()) {
                throw new HttpStatusException(404, String.format("%s not found", recordingName));
            }

            ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, MIME_TYPE_HTML);
            try (InputStream stream = descriptor.get().stream) {
                // blocking function, must be called from a blocking handler
                ctx.response().end(reportGenerator.generateReport(stream));
            }
        } catch (FlightRecorderException e) {
            throw new HttpStatusException(
                    500, String.format("%s could not be opened", recordingName), e);
        } catch (CouldNotLoadRecordingException e) {
            throw new HttpStatusException(
                    500, String.format("%s could not be loaded", recordingName), e);
        } catch (IOException e) {
            throw new HttpStatusException(500, e);
        }
    }

    private static class DownloadDescriptor {
        final InputStream stream;
        final Optional<Long> bytes;

        DownloadDescriptor(InputStream stream, Long bytes) {
            this.stream = Objects.requireNonNull(stream);
            this.bytes = Optional.ofNullable(bytes);
        }
    }
}
