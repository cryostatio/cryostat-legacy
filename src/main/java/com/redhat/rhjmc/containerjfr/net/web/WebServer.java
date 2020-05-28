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

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.apache.http.client.utils.URIBuilder;

import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;

import com.google.gson.Gson;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.net.AuthManager;
import com.redhat.rhjmc.containerjfr.net.HttpServer;
import com.redhat.rhjmc.containerjfr.net.NetworkConfiguration;
import com.redhat.rhjmc.containerjfr.net.web.handlers.RequestHandler;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public class WebServer {

    private static final String ENABLE_CORS_ENV = "CONTAINER_JFR_ENABLE_CORS";

    public static final String MIME_TYPE_JSON = "application/json";
    public static final String MIME_TYPE_HTML = "text/html";
    private static final String MIME_TYPE_PLAINTEXT = "text/plain";

    // Use X- prefix so as to not trigger web-browser auth dialogs
    private static final String AUTH_SCHEME_HEADER = "X-WWW-Authenticate";

    private final HttpServer server;
    private final NetworkConfiguration netConf;
    private final Environment env;
    private final List<RequestHandler> requestHandlers;
    private final Gson gson;
    private final AuthManager auth;
    private final Logger logger;

    WebServer(
            HttpServer server,
            NetworkConfiguration netConf,
            Environment env,
            Set<RequestHandler> requestHandlers,
            Gson gson,
            AuthManager auth,
            Logger logger) {
        this.server = server;
        this.netConf = netConf;
        this.env = env;
        this.requestHandlers = new ArrayList<>(requestHandlers);
        Collections.sort(this.requestHandlers, (a, b) -> a.path().compareTo(b.path()));
        this.gson = gson;
        this.auth = auth;
        this.logger = logger;
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

        requestHandlers.forEach(
                handler -> {
                    logger.trace(
                            String.format(
                                    "Registering request handler (priority %d) for [%s]\t%s",
                                    handler.getPriority(), handler.httpMethod(), handler.path()));
                    Route route = router.route(handler.httpMethod(),
                            handler.path()).order(handler.getPriority());
                    if (handler.isAsync()) {
                        route = route.handler(handler);
                    } else {
                        route = route.blockingHandler(handler, handler.isOrdered());
                    }
                    route = route.failureHandler(failureHandler);
                    if (!handler.isAvailable()) {
                        logger.trace("Handler disabled");
                        route = route.disable();
                    }
                });

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

    public String getArchivedDownloadURL(String recordingName)
            throws UnknownHostException, URISyntaxException, SocketException {
        return new URIBuilder(getHostUri())
                .setScheme(server.isSsl() ? "https" : "http")
                .setPathSegments("api", "v1", "recordings", recordingName)
                .build()
                .normalize()
                .toString();
    }

    public String getDownloadURL(JFRConnection connection, String recordingName)
            throws URISyntaxException, IOException {
        return new URIBuilder(getHostUri())
                .setScheme(server.isSsl() ? "https" : "http")
                .setPathSegments(
                        "api",
                        "v1",
                        "targets",
                        getTargetId(connection),
                        "recordings",
                        recordingName)
                .build()
                .normalize()
                .toString();
    }

    public String getArchivedReportURL(String recordingName)
            throws SocketException, UnknownHostException, URISyntaxException {
        return new URIBuilder(getHostUri())
                .setScheme(server.isSsl() ? "https" : "http")
                .setPathSegments("api", "v1", "reports", recordingName)
                .build()
                .normalize()
                .toString();
    }

    public String getReportURL(JFRConnection connection, String recordingName)
            throws URISyntaxException, IOException {
        return new URIBuilder(getHostUri())
                .setScheme(server.isSsl() ? "https" : "http")
                .setPathSegments(
                        "api", "v1", "targets", getTargetId(connection), "reports", recordingName)
                .build()
                .normalize()
                .toString();
    }

    private String getTargetId(JFRConnection conn) throws IOException {
        return conn.getJMXURL().toString();
    }

    private <T> void endWithJsonKeyValue(String key, T value, HttpServerResponse response) {
        response.end(String.format("{\"%s\":%s}", key, gson.toJson(value)));
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

    public static class DownloadDescriptor {
        public final InputStream stream;
        public final Optional<Long> bytes;
        public final Optional<AutoCloseable> resource;

        public DownloadDescriptor(InputStream stream, Long bytes, AutoCloseable resource) {
            this.stream = Objects.requireNonNull(stream);
            this.bytes = Optional.ofNullable(bytes);
            this.resource = Optional.ofNullable(resource);
        }
    }
}
