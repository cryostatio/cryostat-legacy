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
import io.cryostat.net.security.SecurityContext;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.RequestHandler;
import io.cryostat.net.web.http.api.ApiVersion;

import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.CorsHandler;
import org.apache.http.HttpHeaders;

class CorsEnablingHandler implements RequestHandler<Void> {
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
    public SecurityContext securityContext(Void ctx) {
        return SecurityContext.DEFAULT;
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
