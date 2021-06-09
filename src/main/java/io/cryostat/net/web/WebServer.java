/*
 * Copyright The Cryostat Authors
 *
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
 */
package io.cryostat.net.web;

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

import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;

import io.cryostat.core.log.Logger;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.net.AuthManager;
import io.cryostat.net.HttpServer;
import io.cryostat.net.NetworkConfiguration;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.RequestHandler;
import io.cryostat.net.web.http.api.ApiData;
import io.cryostat.net.web.http.api.ApiMeta;
import io.cryostat.net.web.http.api.ApiResponse;
import io.cryostat.net.web.http.api.v2.ApiException;
import io.cryostat.net.web.http.generic.TimeoutHandler;
import io.cryostat.util.HttpStatusCodeIdentifier;

import com.google.gson.Gson;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.Threshold;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.EnglishReasonPhraseCatalog;

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

                    if (!HttpStatusCodeIdentifier.isServerErrorCode(exception.getStatusCode())) {
                        logger.warn(exception);
                    } else {
                        logger.error(exception);
                    }

                    if (exception.getStatusCode() == 401) {
                        ctx.response().putHeader(AUTH_SCHEME_HEADER, auth.getScheme().toString());
                    }

                    ctx.response().setStatusCode(exception.getStatusCode());

                    if (exception instanceof ApiException) {
                        ApiException ex = (ApiException) exception;
                        String apiStatus =
                                ex.getApiStatus() != null
                                        ? ex.getApiStatus()
                                        : EnglishReasonPhraseCatalog.INSTANCE.getReason(
                                                ex.getStatusCode(), null);
                        ApiErrorResponse resp =
                                new ApiErrorResponse(
                                        new ApiMeta(HttpMimeType.PLAINTEXT, apiStatus),
                                        new ApiErrorData(ex.getFailureReason()));
                        ctx.response()
                                .putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime())
                                .end(gson.toJson(resp));
                    } else {
                        // kept for V1 API handler compatibility
                        String payload =
                                exception.getPayload() != null
                                        ? exception.getPayload()
                                        : exception.getMessage();

                        if (ExceptionUtils.hasCause(exception, HttpStatusException.class)) {
                            payload +=
                                    " caused by " + ExceptionUtils.getRootCauseMessage(exception);
                        }

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
                    }
                };

        requestHandlers.forEach(
                handler -> {
                    logger.trace(
                            "Registering request handler (priority {}) for [{}]\t{}",
                            handler.getPriority(),
                            handler.httpMethod(),
                            handler.path());
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
                        logger.trace("{} handler disabled", handler.getClass().getSimpleName());
                        route = route.disable();
                    }
                });

        this.server.requestHandler(
                req -> {
                    Instant start = Instant.now();
                    WebServerRequest evt =
                            new WebServerRequest(
                                    req.remoteAddress().toString(),
                                    req.method().toString(),
                                    req.path());
                    evt.begin();

                    req.response()
                            .endHandler(
                                    (res) -> {
                                        logger.info(
                                                "({}): {} {} {} {}ms",
                                                req.remoteAddress().toString(),
                                                req.method().toString(),
                                                req.path(),
                                                req.response().getStatusCode(),
                                                Duration.between(start, Instant.now()).toMillis());
                                        evt.setStatusCode(req.response().getStatusCode());
                                        evt.end();
                                        if (evt.shouldCommit()) {
                                            evt.commit();
                                        }
                                    });
                    router.handle(req);
                });
    }

    @Name("io.cryostat.next.web.WebServer.WebServerRequest")
    @Label("Web Server Request")
    @Category("Cryostat")
    @Threshold(TimeoutHandler.TIMEOUT_MS + " ms")
    @SuppressFBWarnings(
            value = "URF_UNREAD_FIELD",
            justification = "The event fields are recorded with JFR instead of accessed directly")
    public static class WebServerRequest extends Event {
        String remoteAddr;
        String method;
        String path;
        int statusCode;

        public WebServerRequest(String remoteAddr, String method, String path) {
            this.remoteAddr = remoteAddr;
            this.method = method;
            this.path = path;
        }

        public void setStatusCode(int code) {
            this.statusCode = code;
        }
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
        // FIXME replace URIBuilder with another implementation. This is the only
        // remaining use
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

    static class ApiErrorResponse extends ApiResponse<ApiErrorData> {
        ApiErrorResponse(ApiMeta meta, ApiErrorData data) {
            super(meta, data);
        }
    }

    static class ApiErrorData extends ApiData {
        private final String reason;

        ApiErrorData(String reason) {
            this.reason = reason;
        }
    }
}
