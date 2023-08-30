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

import java.nio.file.Path;
import java.util.Set;

import javax.inject.Inject;

import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.RequestHandler;
import io.cryostat.net.web.http.api.ApiVersion;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;

class WebClientAssetsGetHandler implements RequestHandler {

    static final String WEB_CLIENT_ASSETS_BASE =
            WebServer.class.getPackageName().replaceAll("\\.", "/");
    private static final Path INDEX_HTML =
            Path.of(WEB_CLIENT_ASSETS_BASE, "index.html").normalize();

    private final boolean hasIndexHtml;

    @Inject
    WebClientAssetsGetHandler(Vertx vertx) {
        this.hasIndexHtml = vertx.fileSystem().existsBlocking(INDEX_HTML.toString());
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.GENERIC;
    }

    @Override
    public int getPriority() {
        return DEFAULT_PRIORITY + 10;
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
        return pathRegex();
    }

    @Override
    public String pathRegex() {
        return HttpGenericModule.NON_API_PATH;
    }

    @Override
    public void handle(RoutingContext ctx) {
        if (!hasIndexHtml) {
            throw new HttpException(404);
        }
        ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.HTML.mime());
        ctx.response().sendFile(INDEX_HTML.toString());
    }
}
