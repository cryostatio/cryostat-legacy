/*
 * Copyright The Cryostat Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.cryostat.net.web;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Named;

import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;

import io.cryostat.MainModule;
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
import io.cryostat.util.URIUtil;

import com.google.gson.Gson;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;
import io.vertx.ext.web.impl.BlockingHandlerDecorator;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.EnglishReasonPhraseCatalog;

public class WebServer extends AbstractVerticle {

    // Use X- prefix so as to not trigger web-browser auth dialogs
    public static final String AUTH_SCHEME_HEADER = "X-WWW-Authenticate";
    public static final String DATASOURCE_FILENAME = "cryostat-analysis.jfr";

    private final HttpServer server;
    private final NetworkConfiguration netConf;
    private final List<RequestHandler> requestHandlers;
    private final Path recordingsPath;
    private final Gson gson;
    private final AuthManager auth;
    private final Logger logger;

    WebServer(
            HttpServer server,
            NetworkConfiguration netConf,
            Set<RequestHandler> requestHandlers,
            Gson gson,
            AuthManager auth,
            Logger logger,
            @Named(MainModule.RECORDINGS_PATH) Path recordingsPath) {
        this.server = server;
        this.netConf = netConf;
        this.requestHandlers = new ArrayList<>(requestHandlers);
        Collections.sort(this.requestHandlers, (a, b) -> a.path().compareTo(b.path()));
        this.recordingsPath = recordingsPath;
        this.gson = gson;
        this.auth = auth;
        this.logger = logger;
    }

    @Override
    public void start() throws FlightRecorderException, SocketException, UnknownHostException {
        Router router =
                Router.router(server.getVertx()); // a vertx is only available after server started

        var fs = server.getVertx().fileSystem();
        var fileUploads = recordingsPath.resolve("file-uploads").toAbsolutePath().toString();
        if (!fs.existsBlocking(fileUploads)) {
            fs.mkdirBlocking(fileUploads);
        }

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

        this.server.requestHandler(router::handle);
    }

    @Override
    public void stop() {
        this.server.requestHandler(null);
    }

    public URL getHostUrl()
            throws MalformedURLException,
                    SocketException,
                    UnknownHostException,
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
        return URIUtil.getConnectionUri(conn).toString();
    }

    public FileUpload getTempFileUpload(
            Collection<FileUpload> fileUploads, Path tempUploadPath, String name) {
        FileUpload upload = null;
        for (var fu : fileUploads) {
            if (fu.name().equals(name)) {
                upload = fu;
            } else {
                vertx.fileSystem()
                        .deleteBlocking(tempUploadPath.resolve(fu.uploadedFileName()).toString());
            }
        }
        return upload;
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
