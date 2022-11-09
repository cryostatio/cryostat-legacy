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
package io.cryostat.net.web.http.api.v1;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import io.cryostat.configuration.Variables;
import io.cryostat.core.sys.Environment;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.SecurityContext;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.RequestHandler;
import io.cryostat.net.web.http.api.ApiVersion;

import com.google.gson.Gson;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;

class GrafanaDashboardUrlGetHandler implements RequestHandler<Void> {

    private final Environment env;
    private final Gson gson;

    @Inject
    GrafanaDashboardUrlGetHandler(Environment env, Gson gson) {
        this.env = env;
        this.gson = gson;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.V1;
    }

    @Override
    public String path() {
        return basePath() + "grafana_dashboard_url";
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.GET;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return ResourceAction.NONE;
    }

    // This handler is not async, but it's simple enough that it doesn't need
    // to be run in a seperate worker thread.
    @Override
    public boolean isAsync() {
        return true;
    }

    @Override
    public List<HttpMimeType> produces() {
        return List.of(HttpMimeType.JSON);
    }

    @Override
    public SecurityContext securityContext(Void ctx) {
        return SecurityContext.DEFAULT;
    }

    @Override
    public void handle(RoutingContext ctx) {
        String dashboardURL;
        if (env.hasEnv(Variables.GRAFANA_DASHBOARD_EXT_ENV)) {
            dashboardURL = env.getEnv(Variables.GRAFANA_DASHBOARD_EXT_ENV);
        } else if (this.env.hasEnv(Variables.GRAFANA_DASHBOARD_ENV)) {
            // Fall back to GRAFANA_DASHBOARD_URL if no external URL is provided
            dashboardURL = env.getEnv(Variables.GRAFANA_DASHBOARD_ENV);
        } else {
            throw new HttpException(500, "Deployment has no Grafana configuration");
        }
        ctx.response()
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime())
                .end(gson.toJson(Map.of("grafanaDashboardUrl", dashboardURL)));
    }
}
