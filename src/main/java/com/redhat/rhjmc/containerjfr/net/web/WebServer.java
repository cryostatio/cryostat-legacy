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

import static com.redhat.rhjmc.containerjfr.util.HttpStatusCodeIdentifier.isServerErrorCode;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.http.client.utils.URIBuilder;

import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;

import com.google.gson.Gson;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.net.AuthManager;
import com.redhat.rhjmc.containerjfr.net.HttpServer;
import com.redhat.rhjmc.containerjfr.net.NetworkConfiguration;
import com.redhat.rhjmc.containerjfr.net.web.handlers.RequestHandler;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.impl.HttpStatusException;

public class WebServer {

    // Use X- prefix so as to not trigger web-browser auth dialogs
    public static final String AUTH_SCHEME_HEADER = "X-WWW-Authenticate";

    private final HttpServer server;
    private final NetworkConfiguration netConf;
    private final List<RequestHandler> requestHandlers;
    private final Gson gson;
    private final AuthManager auth;
    private final Logger logger;

    WebServer(
            HttpServer server,
            NetworkConfiguration netConf,
            Set<RequestHandler> requestHandlers,
            Gson gson,
            AuthManager auth,
            Logger logger) {
        this.server = server;
        this.netConf = netConf;
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

                    if (!isServerErrorCode(exception.getStatusCode())) {
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
                            .setStatusMessage(payload);

                    String accept = ctx.request().getHeader(HttpHeaders.ACCEPT);
                    if (accept.contains(HttpMimeType.JSON.mime())
                            && accept.indexOf(HttpMimeType.JSON.mime())
                                    < accept.indexOf(HttpMimeType.PLAINTEXT.mime())) {
                        ctx.response()
                                .putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime())
                                .end(gson.toJson(Map.of("message", payload)));
                        return;
                    }

                    ctx.response()
                            .putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.PLAINTEXT.mime())
                            .end(payload);
                };

        requestHandlers.forEach(
                handler -> {
                    logger.trace(
                            String.format(
                                    "Registering request handler (priority %d) for [%s]\t%s",
                                    handler.getPriority(), handler.httpMethod(), handler.path()));
                    Route route;
                    if (RequestHandler.ALL_PATHS.equals(handler.path())) {
                        route = router.route();
                    } else {
                        route = router.route(handler.httpMethod(), handler.path());
                    }
                    route = route.order(handler.getPriority());
                    if (handler.isAsync()) {
                        route = route.handler(handler);
                    } else {
                        route = route.blockingHandler(handler, handler.isOrdered());
                    }
                    route = route.failureHandler(failureHandler);
                    if (!handler.isAvailable()) {
                        logger.trace(
                                String.format(
                                        "%s handler disabled", handler.getClass().getSimpleName()));
                        route = route.disable();
                    }
                });

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
    }

    public URL getHostUrl()
            throws MalformedURLException, SocketException, UnknownHostException,
                    URISyntaxException {
        return getHostUri().toURL();
    }

    URI getHostUri() throws SocketException, UnknownHostException, URISyntaxException {
        // FIXME replace URIBuilder with another implementation. This is the only remaining use
        // of the Apache HttpComponents dependency
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
}
