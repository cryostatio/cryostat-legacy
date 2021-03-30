/*-
 * #%L
 * Cryostat
 * %%
 * Copyright (C) 2020 - 2021 Cryostat
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
package io.cryostat.net.web.http.generic;

import javax.inject.Inject;

import io.cryostat.core.sys.Environment;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.RequestHandler;
import io.cryostat.net.web.http.api.ApiVersion;

import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.CorsHandler;

class CorsEnablingHandler implements RequestHandler {
    protected static final String DEV_ORIGIN = "http://localhost:9000";
    protected static final String ENABLE_CORS_ENV = "CRYOSTAT_CORS_ORIGIN";
    protected final CorsHandler corsHandler;
    protected final Environment env;

    @Inject
    CorsEnablingHandler(Environment env) {
        this.env = env;
        this.corsHandler =
                CorsHandler.create(getOrigin())
                        .allowedHeader("Authorization")
                        .allowedHeader(AbstractAuthenticatedRequestHandler.JMX_AUTHORIZATION_HEADER)
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
        return this.env.hasEnv(ENABLE_CORS_ENV);
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.OTHER; // unused for ALL_PATHS handlers
    }

    @Override
    public String path() {
        return ALL_PATHS;
    }

    @Override
    public void handle(RoutingContext ctx) {
        this.corsHandler.handle(ctx);
    }

    String getOrigin() {
        return this.env.getEnv(ENABLE_CORS_ENV, DEV_ORIGIN);
    }
}
