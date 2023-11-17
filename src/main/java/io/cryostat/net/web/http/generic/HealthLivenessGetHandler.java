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

import java.util.Set;

import javax.inject.Inject;

import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.RequestHandler;
import io.cryostat.net.web.http.api.ApiVersion;

import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;

class HealthLivenessGetHandler implements RequestHandler {

    @Inject
    HealthLivenessGetHandler() {}

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.GENERIC;
    }

    @Override
    public String path() {
        return basePath() + "health/liveness";
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
        // This response handler does not actually block, but we force it to execute on the worker
        // pool so that the status check reports not only that the event loop dispatch thread is
        // alive and responsive, but that the worker pool is also actively servicing requests. If we
        // don't force this then this handler only checks if the event loop is alive, but the worker
        // pool may be blocked or otherwise unresponsive and the application as a whole will not be
        // usable.
        return false;
    }

    @Override
    public boolean isOrdered() {
        return true;
    }

    @Override
    public void handle(RoutingContext ctx) {
        ctx.response().setStatusCode(204).end();
    }
}
