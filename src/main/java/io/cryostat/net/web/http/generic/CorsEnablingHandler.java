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

import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import io.cryostat.configuration.Variables;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Environment;
import io.cryostat.net.NetworkConfiguration;
import io.cryostat.net.SslConfiguration;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.RequestHandler;
import io.cryostat.net.web.http.api.ApiVersion;

import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.CorsHandler;
import org.apache.http.HttpHeaders;

class CorsEnablingHandler implements RequestHandler {
    protected static final String DEV_ORIGIN = "http://localhost:9000";
    protected final CorsHandler corsHandler;
    protected final Environment env;
    protected final NetworkConfiguration netConf;
    protected final SslConfiguration sslConf;
    protected final Logger logger;

    @Inject
    CorsEnablingHandler(
            Environment env,
            NetworkConfiguration netConf,
            SslConfiguration sslConf,
            Logger logger) {
        this.env = env;
        this.netConf = netConf;
        this.sslConf = sslConf;
        this.logger = logger;
        this.corsHandler =
                CorsHandler.create()
                        .addOrigin(getWebClientOrigin())
                        .addOrigins(getSelfOrigin())
                        .allowedHeader(HttpHeaders.AUTHORIZATION)
                        .allowedHeader(AbstractAuthenticatedRequestHandler.JMX_AUTHORIZATION_HEADER)
                        .allowedHeader(HttpHeaders.CONTENT_TYPE)
                        .allowedMethod(HttpMethod.GET)
                        .allowedMethod(HttpMethod.POST)
                        .allowedMethod(HttpMethod.PATCH)
                        .allowedMethod(HttpMethod.OPTIONS)
                        .allowedMethod(HttpMethod.HEAD)
                        .allowedMethod(HttpMethod.DELETE)
                        .allowCredentials(true)
                        .exposedHeader(WebServer.AUTH_SCHEME_HEADER)
                        .exposedHeader(AbstractAuthenticatedRequestHandler.JMX_AUTHENTICATE_HEADER);
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.GENERIC;
    }

    @Override
    public int getPriority() {
        return 1;
    }

    @Override
    public boolean isAvailable() {
        return this.env.hasEnv(Variables.ENABLE_CORS_ENV);
    }

    @Override
    public HttpMethod httpMethod() {
        return null; // unused for ALL_PATHS handlers
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
        this.corsHandler.handle(ctx);
    }

    String getWebClientOrigin() {
        return this.env.getEnv(Variables.ENABLE_CORS_ENV, DEV_ORIGIN);
    }

    List<String> getSelfOrigin() {
        try {
            return List.of(
                    String.format(
                            "%s://%s:%d",
                            sslConf.enabled() || netConf.isSslProxied() ? "https" : "http",
                            netConf.getWebServerHost(),
                            netConf.getExternalWebServerPort()));
        } catch (SocketException | UnknownHostException e) {
            logger.warn(e);
            return List.of();
        }
    }
}
