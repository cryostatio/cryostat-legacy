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
package io.cryostat.net.web.http.api.v1;

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import io.cryostat.configuration.Variables;
import io.cryostat.core.sys.Environment;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.RequestHandler;
import io.cryostat.net.web.http.api.ApiVersion;

import com.google.gson.Gson;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;

class GrafanaDashboardUrlGetHandler implements RequestHandler {

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
