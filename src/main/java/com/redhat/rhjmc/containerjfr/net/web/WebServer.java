/*-
 * #%L
 * Container JFR
 * %%
 * Copyright (C) 2020 Red Hat, Inc.
 * %%
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software (each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * #L%
 */
package com.redhat.rhjmc.containerjfr.net.web;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.client.utils.URIBuilder;

import org.openjdk.jmc.flightrecorder.CouldNotLoadRecordingException;
import org.openjdk.jmc.flightrecorder.JfrLoaderToolkit;
import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import com.google.gson.Gson;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.reports.ReportGenerator;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.net.AuthManager;
import com.redhat.rhjmc.containerjfr.net.HttpServer;
import com.redhat.rhjmc.containerjfr.net.NetworkConfiguration;
import com.redhat.rhjmc.containerjfr.net.TargetConnectionManager;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public class WebServer {

    private static final String WEB_CLIENT_ASSETS_BASE =
            WebServer.class.getPackageName().replaceAll("\\.", "/");

    private static final String ENABLE_CORS_ENV = "CONTAINER_JFR_ENABLE_CORS";
    private static final String GRAFANA_DASHBOARD_ENV = "GRAFANA_DASHBOARD_URL";
    private static final String GRAFANA_DATASOURCE_ENV = "GRAFANA_DATASOURCE_URL";
    private static final String USE_LOW_MEM_PRESSURE_STREAMING_ENV =
            "USE_LOW_MEM_PRESSURE_STREAMING";

    private static final String MIME_TYPE_JSON = "application/json";
    private static final String MIME_TYPE_HTML = "text/html";
    private static final String MIME_TYPE_PLAINTEXT = "text/plain";
    private static final String MIME_TYPE_OCTET_STREAM = "application/octet-stream";

    // Use X- prefix so as to not trigger web-browser auth dialogs
    private static final String AUTH_SCHEME_HEADER = "X-WWW-Authenticate";

    private static final int WRITE_BUFFER_SIZE = 64 * 1024; // 64 KB

    private static final Pattern RECORDING_FILENAME_PATTERN =
            Pattern.compile("([A-Za-z\\d-]*)_([A-Za-z\\d-_]*)_([\\d]*T[\\d]*Z)(.[\\d]+)?");

    private final HttpServer server;
    private final NetworkConfiguration netConf;
    private final Environment env;
    private final Path savedRecordingsPath;
    private final FileSystem fs;
    private final AuthManager auth;
    private final Gson gson;
    private final Logger logger;

    private final ReportGenerator reportGenerator;
    private final TargetConnectionManager targetConnectionManager;

    WebServer(
            HttpServer server,
            NetworkConfiguration netConf,
            Environment env,
            Path savedRecordingsPath,
            FileSystem fs,
            AuthManager auth,
            Gson gson,
            ReportGenerator reportGenerator,
            TargetConnectionManager targetConnectionManager,
            Logger logger) {
        this.server = server;
        this.netConf = netConf;
        this.env = env;
        this.savedRecordingsPath = savedRecordingsPath;
        this.fs = fs;
        this.auth = auth;
        this.gson = gson;
        this.logger = logger;
        this.reportGenerator = reportGenerator;
        this.targetConnectionManager = targetConnectionManager;

        if (env.hasEnv(USE_LOW_MEM_PRESSURE_STREAMING_ENV)) {
            logger.info("low memory pressure streaming enabled for web server");
        } else {
            logger.info("low memory pressure streaming disabled for web server");
        }
    }

    public void start() throws FlightRecorderException, SocketException, UnknownHostException {
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

                    if (exception.getStatusCode() == 401) {
                        ctx.response().putHeader(AUTH_SCHEME_HEADER, auth.getScheme().toString());
                    }

                    String payload =
                            exception.getPayload() != null
                                    ? exception.getPayload()
                                    : exception.getMessage();

                    ctx.response()
                            .setStatusCode(exception.getStatusCode())
                            .setStatusMessage(exception.getMessage());

                    String accept = ctx.request().getHeader(HttpHeaders.ACCEPT);
                    if (accept.contains(MIME_TYPE_JSON)
                            && accept.indexOf(MIME_TYPE_JSON)
                                    < accept.indexOf(MIME_TYPE_PLAINTEXT)) {
                        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, MIME_TYPE_JSON);
                        endWithJsonKeyValue("message", payload, ctx.response());
                        return;
                    }

                    ctx.response()
                            .putHeader(HttpHeaders.CONTENT_TYPE, MIME_TYPE_PLAINTEXT)
                            .end(payload);
                };

        router.post("/api/v1/auth")
                .blockingHandler(this::handleAuthRequest, false)
                .failureHandler(failureHandler);

        if (isCorsEnabled()) {
            router.options("/*")
                    .blockingHandler(
                            ctx -> {
                                enableCors(ctx.response());
                                ctx.response().end();
                            },
                            false)
                    .failureHandler(failureHandler);
        }

        router.get("/api/v1/clienturl")
                .handler(this::handleClientUrlRequest)
                .failureHandler(failureHandler);

        router.get("/api/v1/grafana_datasource_url")
                .handler(this::handleGrafanaDatasourceUrlRequest)
                .failureHandler(failureHandler);

        router.get("/api/v1/grafana_dashboard_url")
                .handler(this::handleGrafanaDashboardUrlRequest)
                .failureHandler(failureHandler);

        router.get("/api/v1/hosts/:hostId/recordings/:recordingName")
                .blockingHandler(
                        ctx -> {
                            String hostId = ctx.pathParam("hostId");
                            String recordingName = ctx.pathParam("recordingName");
                            if (recordingName != null && recordingName.endsWith(".jfr")) {
                                recordingName =
                                        recordingName.substring(0, recordingName.length() - 4);
                            }
                            handleRecordingDownloadRequest(hostId, recordingName, ctx);
                        },
                        false)
                .failureHandler(failureHandler);

        router.post("/api/v1/recordings")
                .handler(BodyHandler.create(true))
                .handler(this::handleRecordingUploadRequest)
                .failureHandler(failureHandler);

        router.get("/api/v1/hosts/:hostId/reports/:recordingName")
                .blockingHandler(
                        ctx ->
                                handleReportPageRequest(
                                        ctx.pathParam("hostId"),
                                        ctx.pathParam("recordingName"),
                                        ctx))
                .failureHandler(failureHandler);

        router.get("/*")
                .handler(StaticHandler.create(WEB_CLIENT_ASSETS_BASE))
                .handler(this::handleWebClientIndexRequest)
                .failureHandler(failureHandler);

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
                    enableCors(req.response());
                    router.handle(req);
                });
    }

    public void stop() {
        this.server.requestHandler(null);
    }

    public URL getHostUrl()
            throws MalformedURLException, SocketException, UnknownHostException,
                    URISyntaxException {
        return getHostUri().toURL();
    }

    URI getHostUri() throws SocketException, UnknownHostException, URISyntaxException {
        return new URIBuilder()
                .setScheme(server.isSsl() ? "https" : "http")
                .setHost(netConf.getWebServerHost())
                .setPort(netConf.getExternalWebServerPort())
                .build()
                .normalize();
    }

    public String getDownloadURL(String recordingName)
            throws UnknownHostException, URISyntaxException, SocketException {
        return new URIBuilder(getHostUri())
                .setPathSegments("api", "v1", "recordings", recordingName)
                .build()
                .normalize()
                .toString();
    }

    public String getDownloadURL(JFRConnection connection, String recordingName)
            throws UnknownHostException, URISyntaxException, SocketException {
        return new URIBuilder(getHostUri())
                .setPathSegments(
                        "api", "v1", "hosts", getHostId(connection), "recordings", recordingName)
                .build()
                .normalize()
                .toString();
    }

    public String getReportURL(String recordingName)
            throws SocketException, UnknownHostException, URISyntaxException {
        return new URIBuilder(getHostUri())
                .setPathSegments("api", "v1", "reports", recordingName)
                .build()
                .normalize()
                .toString();
    }

    public String getReportURL(JFRConnection connection, String recordingName)
            throws SocketException, UnknownHostException, URISyntaxException {
        return new URIBuilder(getHostUri())
                .setPathSegments(
                        "api", "v1", "hosts", getHostId(connection), "reports", recordingName)
                .build()
                .normalize()
                .toString();
    }

    private String getHostId(JFRConnection conn) {
        // FIXME replace this with the connection JMX service URL
        return String.format("%s:%d", conn.getHost(), conn.getPort());
    }

    private Optional<DownloadDescriptor> getRecordingDescriptor(String hostId, String recordingName)
            throws Exception {
        return getTargetRecordingDescriptor(hostId, recordingName)
                .or(() -> getSavedRecordingDescriptor(recordingName));
    }

    private Optional<DownloadDescriptor> getTargetRecordingDescriptor(
            String hostId, String recordingName) throws Exception {
        JFRConnection connection = targetConnectionManager.connect(hostId);
        Optional<IRecordingDescriptor> desc =
                connection.getService().getAvailableRecordings().stream()
                        .filter(r -> Objects.equals(recordingName, r.getName()))
                        .findFirst();
        if (desc.isPresent()) {
            return Optional.of(
                    new DownloadDescriptor(
                            connection.getService().openStream(desc.get(), false),
                            null,
                            connection));
        } else {
            connection.close();
            return Optional.empty();
        }
    }

    private Optional<DownloadDescriptor> getSavedRecordingDescriptor(String recordingName) {
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
                                Files.size(savedRecording.get()),
                                null));
            }
        } catch (Exception e) {
            logger.error(e);
        }
        return Optional.empty();
    }

    private <T> void endWithJsonKeyValue(String key, T value, HttpServerResponse response) {
        response.end(String.format("{\"%s\":%s}", key, gson.toJson(value)));
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
                                    worker.submit(this); // recursive call on this runnable itself
                                });
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
        boolean authd = false;
        try {
            authd = validateRequestAuthorization(ctx.request()).get();
        } catch (Exception e) {
            throw new HttpStatusException(500, e);
        }
        if (authd) {
            ctx.response().setStatusCode(200);
            ctx.response().end();
        } else {
            throw new HttpStatusException(401);
        }
    }

    void handleWebClientIndexRequest(RoutingContext ctx) {
        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, MIME_TYPE_HTML);
        ctx.response().sendFile(WEB_CLIENT_ASSETS_BASE + "/index.html");
    }

    void handleClientUrlRequest(RoutingContext ctx) {
        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, MIME_TYPE_JSON);
        try {
            endWithJsonKeyValue(
                    "clientUrl",
                    String.format(
                            "%s://%s:%d/api/v1/command",
                            server.isSsl() ? "wss" : "ws",
                            netConf.getWebServerHost(),
                            netConf.getExternalWebServerPort()),
                    ctx.response());
        } catch (SocketException | UnknownHostException e) {
            throw new HttpStatusException(500, e);
        }
    }

    void handleGrafanaDatasourceUrlRequest(RoutingContext ctx) {
        if (!this.env.hasEnv(GRAFANA_DATASOURCE_ENV)) {
            throw new HttpStatusException(500, "Deployment has no Grafana configuration");
        }
        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, MIME_TYPE_JSON);
        endWithJsonKeyValue(
                "grafanaDatasourceUrl", env.getEnv(GRAFANA_DATASOURCE_ENV, ""), ctx.response());
    }

    void handleGrafanaDashboardUrlRequest(RoutingContext ctx) {
        if (!this.env.hasEnv(GRAFANA_DASHBOARD_ENV)) {
            throw new HttpStatusException(500, "Deployment has no Grafana configuration");
        }
        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, MIME_TYPE_JSON);
        endWithJsonKeyValue(
                "grafanaDashboardUrl", env.getEnv(GRAFANA_DASHBOARD_ENV, ""), ctx.response());
    }

    void handleRecordingUploadRequest(RoutingContext ctx) {
        try {
            if (!validateRequestAuthorization(ctx.request()).get()) {
                throw new HttpStatusException(401);
            }
        } catch (Exception e) {
            throw new HttpStatusException(500, e);
        }

        if (!fs.isDirectory(savedRecordingsPath)) {
            throw new HttpStatusException(503, "Recording saving not available");
        }

        FileUpload upload = null;
        for (FileUpload fu : ctx.fileUploads()) {
            // ignore unrecognized form fields
            if ("recording".equals(fu.name())) {
                upload = fu;
                break;
            }
        }

        if (upload == null) {
            throw new HttpStatusException(400, "No recording submission");
        }

        String fileName = upload.fileName();
        if (fileName == null || fileName.isEmpty()) {
            throw new HttpStatusException(400, "Recording name must not be empty");
        }

        if (fileName.endsWith(".jfr")) {
            fileName = fileName.substring(0, fileName.length() - 4);
        }

        Matcher m = RECORDING_FILENAME_PATTERN.matcher(fileName);
        if (!m.matches()) {
            throw new HttpStatusException(400, "Incorrect recording file name pattern");
        }

        String targetName = m.group(1);
        String recordingName = m.group(2);
        String timestamp = m.group(3);
        int count =
                m.group(4) == null || m.group(4).isEmpty()
                        ? 0
                        : Integer.parseInt(m.group(4).substring(1));

        final String basename = String.format("%s_%s_%s", targetName, recordingName, timestamp);
        final String uploadedFileName = upload.uploadedFileName();
        validateRecording(
                upload.uploadedFileName(),
                (res) ->
                        saveRecording(
                                basename,
                                uploadedFileName,
                                count,
                                (res2) -> {
                                    if (res2.failed()) {
                                        ctx.fail(res2.cause());
                                        return;
                                    }

                                    ctx.response()
                                            .putHeader(HttpHeaders.CONTENT_TYPE, MIME_TYPE_JSON);
                                    endWithJsonKeyValue("name", res2.result(), ctx.response());

                                    logger.info(
                                            String.format("Recording saved as %s", res2.result()));
                                }));
    }

    // try-with-resources generates a "redundant" nullcheck in bytecode
    @SuppressFBWarnings("RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE")
    void handleRecordingDownloadRequest(String hostId, String recordingName, RoutingContext ctx) {
        try {
            if (!validateRequestAuthorization(ctx.request()).get()) {
                throw new HttpStatusException(401);
            }

            Optional<DownloadDescriptor> descriptor = getRecordingDescriptor(hostId, recordingName);
            if (descriptor.isEmpty()) {
                throw new HttpStatusException(404, String.format("%s not found", recordingName));
            }

            ctx.response().setChunked(true);
            ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, MIME_TYPE_OCTET_STREAM);
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
            } finally {
                descriptor
                        .get()
                        .resource
                        .ifPresent(
                                resource -> {
                                    try {
                                        resource.close();
                                    } catch (Exception e) {
                                        logger.warn(e);
                                    }
                                });
            }
        } catch (HttpStatusException e) {
            throw e;
        } catch (FlightRecorderException e) {
            throw new HttpStatusException(
                    500, String.format("%s could not be opened", recordingName), e);
        } catch (Exception e) {
            throw new HttpStatusException(500, e);
        }
    }

    void handleReportPageRequest(String hostId, String recordingName, RoutingContext ctx) {
        try {
            if (!validateRequestAuthorization(ctx.request()).get()) {
                throw new HttpStatusException(401);
            }
            Optional<DownloadDescriptor> descriptor = getRecordingDescriptor(hostId, recordingName);
            if (descriptor.isEmpty()) {
                throw new HttpStatusException(404, String.format("%s not found", recordingName));
            }

            ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, MIME_TYPE_HTML);
            try (InputStream stream = descriptor.get().stream) {
                // blocking function, must be called from a blocking handler
                ctx.response().end(reportGenerator.generateReport(stream));
            }
        } catch (HttpStatusException e) {
            throw e;
        } catch (FlightRecorderException e) {
            throw new HttpStatusException(
                    500, String.format("%s could not be opened", recordingName), e);
        } catch (CouldNotLoadRecordingException e) {
            throw new HttpStatusException(
                    500, String.format("%s could not be loaded", recordingName), e);
        } catch (Exception e) {
            throw new HttpStatusException(500, e);
        }
    }

    private Future<Boolean> validateRequestAuthorization(HttpServerRequest req) throws Exception {
        return auth.validateHttpHeader(() -> req.getHeader(HttpHeaders.AUTHORIZATION));
    }

    private boolean isCorsEnabled() {
        return this.env.hasEnv(ENABLE_CORS_ENV);
    }

    private void enableCors(HttpServerResponse response) {
        if (isCorsEnabled()) {
            response.putHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:9000");
            response.putHeader("Vary", "Origin");
            response.putHeader(
                    HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST, OPTIONS, HEAD");
            response.putHeader(
                    HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                    String.join(", ", Arrays.asList("authorization", "Authorization")));
            response.putHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
            response.putHeader(
                    HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                    String.join(", ", Arrays.asList(AUTH_SCHEME_HEADER)));
        }
    }

    private <T> AsyncResult<T> makeAsyncResult(T result) {
        return new AsyncResult<>() {
            @Override
            public T result() {
                return result;
            }

            @Override
            public Throwable cause() {
                return null;
            }

            @Override
            public boolean succeeded() {
                return true;
            }

            @Override
            public boolean failed() {
                return false;
            }
        };
    }

    private <T> AsyncResult<T> makeFailedAsyncResult(Throwable cause) {
        return new AsyncResult<>() {
            @Override
            public T result() {
                return null;
            }

            @Override
            public Throwable cause() {
                return cause;
            }

            @Override
            public boolean succeeded() {
                return false;
            }

            @Override
            public boolean failed() {
                return true;
            }
        };
    }

    private void validateRecording(String recordingFile, Handler<AsyncResult<Void>> handler) {
        server.getVertx()
                .executeBlocking(
                        event -> {
                            try {
                                JfrLoaderToolkit.loadEvents(
                                        new File(recordingFile)); // try loading events to see if
                                // it's a valid file
                                event.complete();
                            } catch (CouldNotLoadRecordingException | IOException e) {
                                event.fail(e);
                            }
                        },
                        res -> {
                            if (res.failed()) {
                                Throwable t;
                                if (res.cause() instanceof CouldNotLoadRecordingException) {
                                    t =
                                            new HttpStatusException(
                                                    400,
                                                    "Not a valid JFR recording file",
                                                    res.cause());
                                } else {
                                    t = res.cause();
                                }

                                handler.handle(makeFailedAsyncResult(t));
                                return;
                            }

                            handler.handle(makeAsyncResult(null));
                        });
    }

    private void saveRecording(
            String basename, String tmpFile, int counter, Handler<AsyncResult<String>> handler) {
        // TODO byte-sized rename limit is arbitrary. Probably plenty since recordings
        // are also differentiated by second-resolution timestamp
        if (counter >= Byte.MAX_VALUE) {
            handler.handle(
                    makeFailedAsyncResult(
                            new IOException(
                                    "Recording could not be saved. File already exists and rename attempts were exhausted.")));
            return;
        }

        String filename = counter > 1 ? basename + "." + counter + ".jfr" : basename + ".jfr";

        server.getVertx()
                .fileSystem()
                .exists(
                        savedRecordingsPath.resolve(filename).toString(),
                        (res) -> {
                            if (res.failed()) {
                                handler.handle(makeFailedAsyncResult(res.cause()));
                                return;
                            }

                            if (res.result()) {
                                saveRecording(basename, tmpFile, counter + 1, handler);
                                return;
                            }

                            // verified no name clash at this time
                            server.getVertx()
                                    .fileSystem()
                                    .move(
                                            tmpFile,
                                            savedRecordingsPath.resolve(filename).toString(),
                                            (res2) -> {
                                                if (res2.failed()) {
                                                    handler.handle(
                                                            makeFailedAsyncResult(res2.cause()));
                                                    return;
                                                }

                                                handler.handle(makeAsyncResult(filename));
                                            });
                        });
    }

    private static class DownloadDescriptor {
        final InputStream stream;
        final Optional<Long> bytes;
        final Optional<AutoCloseable> resource;

        DownloadDescriptor(InputStream stream, Long bytes, AutoCloseable resource) {
            this.stream = Objects.requireNonNull(stream);
            this.bytes = Optional.ofNullable(bytes);
            this.resource = Optional.ofNullable(resource);
        }
    }
}
