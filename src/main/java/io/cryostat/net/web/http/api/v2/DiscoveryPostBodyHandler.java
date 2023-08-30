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
package io.cryostat.net.web.http.api.v2;

import java.util.Set;

import javax.inject.Inject;

import io.cryostat.core.log.Logger;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.RequestHandler;
import io.cryostat.net.web.http.api.ApiVersion;

import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

class DiscoveryPostBodyHandler implements RequestHandler {

    static final BodyHandler BODY_HANDLER = BodyHandler.create(true);

    @Inject
    DiscoveryPostBodyHandler(Logger logger) {}

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.V2_2;
    }

    @Override
    public int getPriority() {
        return DEFAULT_PRIORITY - 1;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.POST;
    }

    @Override
    public String path() {
        return basePath() + DiscoveryPostHandler.PATH;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return ResourceAction.NONE;
    }

    @Override
    public void handle(RoutingContext ctx) {
        BODY_HANDLER.handle(ctx);
    }
}
