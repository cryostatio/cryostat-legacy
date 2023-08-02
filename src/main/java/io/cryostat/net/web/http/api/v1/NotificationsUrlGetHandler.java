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

import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import io.cryostat.net.HttpServer;
import io.cryostat.net.NetworkConfiguration;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.RequestHandler;
import io.cryostat.net.web.http.api.ApiVersion;

import com.google.gson.Gson;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;

class NotificationsUrlGetHandler implements RequestHandler {

    private final Gson gson;
    private final boolean isSsl;
    private final NetworkConfiguration netConf;

    @Inject
    NotificationsUrlGetHandler(Gson gson, HttpServer server, NetworkConfiguration netConf) {
        this.gson = gson;
        this.isSsl = server.isSsl();
        this.netConf = netConf;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.V1;
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
    public String path() {
        return basePath() + "notifications_url";
    }

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
        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime());
        try {
            // TODO replace String.format with URIBuilder or something else than manual string
            // construction
            String notificationsUrl =
                    String.format(
                            "%s://%s:%d/api/v1/notifications",
                            isSsl ? "wss" : "ws",
                            netConf.getWebServerHost(),
                            netConf.getExternalWebServerPort());
            ctx.response().end(gson.toJson(Map.of("notificationsUrl", notificationsUrl)));
        } catch (SocketException | UnknownHostException e) {
            throw new HttpException(500, e);
        }
    }
}
