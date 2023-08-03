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
package io.cryostat.net.web.http.generic;

import java.util.Set;

import javax.inject.Inject;

import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.RequestHandler;
import io.cryostat.net.web.http.api.ApiVersion;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.LoggerFormat;
import io.vertx.ext.web.handler.LoggerFormatter;
import io.vertx.ext.web.handler.LoggerHandler;
import io.vertx.ext.web.impl.Utils;
import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import org.apache.commons.io.FileUtils;

class RequestLoggingHandler implements RequestHandler {

    private final LoggerHandler delegate;

    @Inject
    RequestLoggingHandler() {
        this.delegate =
                LoggerHandler.create(LoggerFormat.CUSTOM)
                        .customFormatter(new VertxDefaultFormatterWithDuration());
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.GENERIC;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public HttpMethod httpMethod() {
        return null;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return ResourceAction.NONE;
    }

    @Override
    public String path() {
        return ALL_PATHS;
    }

    @Override
    public void handle(RoutingContext ctx) {
        HttpServerRequest req = ctx.request();

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
                            evt.setStatusCode(req.response().getStatusCode());
                            evt.end();
                            if (evt.shouldCommit()) {
                                evt.commit();
                            }
                        });

        this.delegate.handle(ctx);
    }

    // this is ripped from the LoggerHandler's `DEFAULT` format, but with the addition of the
    // request duration after the timestamp and formatting the content-length
    private static class VertxDefaultFormatterWithDuration implements LoggerFormatter {

        @Override
        public String format(RoutingContext context, long ms) {
            HttpServerRequest request = context.request();

            String remoteClient = getClientAddress(context.request().remoteAddress());
            String rfc1123DateTime = Utils.formatRFC1123DateTime(System.currentTimeMillis());
            HttpMethod method = context.request().method();
            String uri = context.request().uri();
            String httpVersion = httpVersion(context.request().version());
            int status = request.response().getStatusCode();
            String contentLength =
                    FileUtils.byteCountToDisplaySize(request.response().bytesWritten());

            MultiMap headers = request.headers();
            String referrer =
                    orDefault(
                            headers.contains("referrer")
                                    ? headers.get("referrer")
                                    : headers.get("referer"),
                            "-");
            String userAgent = orDefault(request.headers().get("user-agent"), "-");

            return String.format(
                    "%s - - [%s] %dms \"%s %s %s\" %d %s \"%s\"" + " \"%s\"",
                    remoteClient,
                    rfc1123DateTime,
                    ms,
                    method,
                    uri,
                    httpVersion,
                    status,
                    contentLength,
                    referrer,
                    userAgent);
        }

        private String httpVersion(HttpVersion version) {
            String versionFormatted = "-";
            switch (version) {
                case HTTP_1_0:
                    versionFormatted = "HTTP/1.0";
                    break;
                case HTTP_1_1:
                    versionFormatted = "HTTP/1.1";
                    break;
                case HTTP_2:
                    versionFormatted = "HTTP/2.0";
                    break;
            }
            return versionFormatted;
        }

        private String getClientAddress(SocketAddress inetSocketAddress) {
            if (inetSocketAddress == null) {
                return null;
            }
            return inetSocketAddress.host();
        }

        private String orDefault(String v, String d) {
            return v == null ? d : v;
        }
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
}
