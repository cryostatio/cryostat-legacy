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

class HealthGetHandler implements RequestHandler {

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
