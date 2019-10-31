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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

public class WebServer implements ConnectionListener {

    private static final String GRAFANA_DASHBOARD_ENV = "GRAFANA_DASHBOARD_URL";
    private static final String GRAFANA_DATASOURCE_ENV = "GRAFANA_DATASOURCE_URL";
    
    private static final String MIME_TYPE_JSON = "application/json";
    private static final String MIME_TYPE_HTML = "text/html";
    private static final String MIME_TYPE_PLAINTEXT = "text/plain";
    private static final String MIME_TYPE_OCTET_STREAM = "application/octet-stream";

    private static final int WRITE_BUFFER_SIZE = 64 * 1024; //64 KB
    
    private final ExecutorService TRIM_WORKER = Executors.newSingleThreadExecutor();

    private final HttpServer server;
    private final NetworkConfiguration netConf;
    private final Environment env;
    private final Path savedRecordingsPath;
    private final Logger logger;
    private IFlightRecorderService service;

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

    public void start() throws FlightRecorderException, SocketException, UnknownHostException {
        server.start();
        if (this.service != null) {
            this.service.getAvailableRecordings().forEach(this::addRecording);
        }

        Router router = Router.router(server.getVertx()); // a vertx is only available after server started

        // error page handler
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

    public void restart() throws FlightRecorderException, SocketException, UnknownHostException {
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

}
