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
import java.util.Arrays;
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
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.net.web.http.api.v2.ApiException;
import io.cryostat.util.HttpStatusCodeIdentifier;

import com.google.gson.Gson;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;
import io.vertx.ext.web.impl.BlockingHandlerDecorator;
import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.EnglishReasonPhraseCatalog;

public class WebServer extends AbstractVerticle {

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

    @Override
    public void start() throws FlightRecorderException, SocketException, UnknownHostException {
        Router router =
                Router.router(server.getVertx()); // a vertx is only available after server started

        // error page handler
        Handler<RoutingContext> failureHandler =
                ctx -> {
                    HttpException exception;
                    if (ctx.failure() instanceof HttpException) {
                        exception = (HttpException) ctx.failure();
                    } else if (ctx.failure() instanceof ApiException) {
                        ApiException ex = (ApiException) ctx.failure();
                        exception =
                                new HttpException(ex.getStatusCode(), ex.getFailureReason(), ex);
                    } else {
                        exception = new HttpException(500, ctx.failure());
                    }

                    String payload =
                            exception.getPayload() != null
                                    ? exception.getPayload()
                                    : exception.getMessage();
                    if (!HttpStatusCodeIdentifier.isServerErrorCode(exception.getStatusCode())) {
                        logger.warn(
                                "HTTP {}: {}\n{}",
                                exception.getStatusCode(),
                                payload,
                                ExceptionUtils.getStackTrace(exception).trim());
                    } else {
                        logger.error(
                                "HTTP {}: {}\n{}",
                                exception.getStatusCode(),
                                payload,
                                ExceptionUtils.getStackTrace(exception).trim());
                    }

                    if (exception.getStatusCode() == 401) {
                        ctx.response().putHeader(AUTH_SCHEME_HEADER, auth.getScheme().toString());
                    }

                    ctx.response().setStatusCode(exception.getStatusCode());

                    if (exception.getCause() instanceof ApiException) {
                        ApiException ex = (ApiException) exception.getCause();
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
                        if (ExceptionUtils.hasCause(exception, HttpException.class)) {
                            payload +=
                                    " caused by " + ExceptionUtils.getRootCauseMessage(exception);
                        }

                        String accept = ctx.request().getHeader(HttpHeaders.ACCEPT);
                        if (accept != null
                                && accept.contains(HttpMimeType.JSON.mime())
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
                    } else if (handler.pathRegex() != null) {
                        route = router.routeWithRegex(handler.httpMethod(), handler.pathRegex());
                    } else {
                        route = router.route(handler.httpMethod(), handler.path());
                    }
                    route = route.order(handler.getPriority());
                    for (HttpMimeType mime : handler.produces()) {
                        route = route.produces(mime.mime());
                    }
                    for (HttpMimeType mime : handler.consumes()) {
                        route = route.consumes(mime.mime());
                    }
                    DeprecatedApi deprecated =
                            handler.getClass().getAnnotation(DeprecatedApi.class);
                    if (deprecated != null) {
                        route =
                                route.handler(
                                        new DeprecatedHandlerDecorator(
                                                deprecated.deprecated().forRemoval(),
                                                deprecated.alternateLocation()));
                    }
                    if (handler.isAsync()) {
                        route = route.handler(handler);
                    } else {
                        BlockingHandlerDecorator async =
                                new BlockingHandlerDecorator(handler, handler.isOrdered());
                        route = route.handler(async);
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
                                    req.remoteAddress().host(),
                                    req.remoteAddress().port(),
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

    @Name("io.cryostat.net.web.WebServer.WebServerRequest")
    @Label("Web Server Request")
    @Category("Cryostat")
    @SuppressFBWarnings(
            value = "URF_UNREAD_FIELD",
            justification = "The event fields are recorded with JFR instead of accessed directly")
    public static class WebServerRequest extends Event {
        String host;
        int port;
        String method;
        String path;
        int statusCode;

        public WebServerRequest(String host, int port, String method, String path) {
            this.host = host;
            this.port = port;
            this.method = method;
            this.path = path;
        }

        public void setStatusCode(int code) {
            this.statusCode = code;
        }
    }

    @Override
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

    // FIXME this has an implicit dependency on the RecordingGetHandler path
    public String getArchivedDownloadURL(String sourceTarget, String recordingName)
            throws UnknownHostException, URISyntaxException, SocketException {
        return getAssetDownloadURL(ApiVersion.BETA, "recordings", sourceTarget, recordingName);
    }

    // FIXME this has a an implicit dependency on the TargetRecordingGetHandler path
    public String getDownloadURL(JFRConnection connection, String recordingName)
            throws URISyntaxException, IOException {
        return getAssetDownloadURL(
                ApiVersion.V1, "targets", getTargetId(connection), "recordings", recordingName);
    }

    // FIXME this has a an implicit dependency on the ReportGetHandler path
    public String getArchivedReportURL(String sourceTarget, String recordingName)
            throws SocketException, UnknownHostException, URISyntaxException {
        return getAssetDownloadURL(ApiVersion.BETA, "reports", sourceTarget, recordingName);
    }

    // FIXME this has a an implicit dependency on the TargetReportGetHandler path
    public String getReportURL(JFRConnection connection, String recordingName)
            throws URISyntaxException, IOException {
        return getAssetDownloadURL(
                ApiVersion.V1, "targets", getTargetId(connection), "reports", recordingName);
    }

    public String getAssetDownloadURL(ApiVersion apiVersion, String... pathSegments)
            throws SocketException, UnknownHostException, URISyntaxException {
        List<String> segments = new ArrayList<>();
        segments.add("api");
        segments.add(apiVersion.getVersionString());
        segments.addAll(Arrays.asList(pathSegments));
        return new URIBuilder(getHostUri())
                .setScheme(server.isSsl() ? "https" : "http")
                .setPathSegments(segments)
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
