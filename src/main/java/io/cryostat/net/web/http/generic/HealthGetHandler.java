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
package io.cryostat.net.web.http.generic;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;

import io.cryostat.ApplicationVersion;
import io.cryostat.configuration.Variables;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Environment;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.SecurityContext;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.RequestHandler;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.util.HttpStatusCodeIdentifier;

import com.google.gson.Gson;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;

class HealthGetHandler implements RequestHandler<Void> {

    private final ApplicationVersion appVersion;
    private final WebClient webClient;
    private final Environment env;
    private final Gson gson;
    private final Logger logger;

    @Inject
    HealthGetHandler(
            ApplicationVersion appVersion,
            WebClient webClient,
            Environment env,
            Gson gson,
            Logger logger) {
        this.appVersion = appVersion;
        this.webClient = webClient;
        this.env = env;
        this.gson = gson;
        this.logger = logger;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.GENERIC;
    }

    @Override
    public String path() {
        return basePath() + "health";
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.GET;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return ResourceAction.NONE;
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public SecurityContext securityContext(Void ctx) {
        return SecurityContext.DEFAULT;
    }

    @Override
    public void handle(RoutingContext ctx) {
        CompletableFuture<Boolean> datasourceAvailable = new CompletableFuture<>();
        CompletableFuture<Boolean> dashboardAvailable = new CompletableFuture<>();
        CompletableFuture<Boolean> reportsAvailable = new CompletableFuture<>();

        checkUri(Variables.GRAFANA_DATASOURCE_ENV, "/", datasourceAvailable);
        checkUri(Variables.GRAFANA_DASHBOARD_ENV, "/api/health", dashboardAvailable);
        if (!this.env.hasEnv(Variables.REPORT_GENERATOR_ENV)) {
            // using subprocess generation, so it is available
            reportsAvailable.complete(true);
        } else {
            checkUri(Variables.REPORT_GENERATOR_ENV, "/health", reportsAvailable);
        }

        ctx.response()
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime())
                .end(
                        gson.toJson(
                                Map.of(
                                        "cryostatVersion",
                                        appVersion.getVersionString(),
                                        "dashboardConfigured",
                                        env.hasEnv(Variables.GRAFANA_DASHBOARD_ENV),
                                        "dashboardAvailable",
                                        dashboardAvailable.join(),
                                        "datasourceConfigured",
                                        env.hasEnv(Variables.GRAFANA_DATASOURCE_ENV),
                                        "datasourceAvailable",
                                        datasourceAvailable.join(),
                                        "reportsConfigured",
                                        env.hasEnv(Variables.REPORT_GENERATOR_ENV),
                                        "reportsAvailable",
                                        reportsAvailable.join())));
    }

    private void checkUri(String envName, String path, CompletableFuture<Boolean> future) {
        if (this.env.hasEnv(envName)) {
            URI uri;
            try {
                uri = new URI(this.env.getEnv(envName));
            } catch (URISyntaxException e) {
                logger.error(e);
                future.complete(false);
                return;
            }
            logger.debug("Testing health of {}={} {}", envName, uri.toString(), path);
            HttpRequest<Buffer> req = webClient.get(uri.getHost(), path);
            if (uri.getPort() != -1) {
                req = req.port(uri.getPort());
            }
            req.ssl("https".equals(uri.getScheme()))
                    .timeout(5000)
                    .send(
                            handler -> {
                                if (handler.failed()) {
                                    this.logger.warn(new IOException(handler.cause()));
                                    future.complete(false);
                                    return;
                                }
                                future.complete(
                                        HttpStatusCodeIdentifier.isSuccessCode(
                                                handler.result().statusCode()));
                            });
        } else {
            future.complete(false);
        }
    }
}
