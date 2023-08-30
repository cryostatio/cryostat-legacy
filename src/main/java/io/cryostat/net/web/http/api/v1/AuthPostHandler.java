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

import java.util.Set;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.api.ApiVersion;

import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;

class AuthPostHandler extends AbstractAuthenticatedRequestHandler {

    @Inject
    AuthPostHandler(AuthManager auth, CredentialsManager credentialsManager, Logger logger) {
        super(auth, credentialsManager, logger);
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.V1;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.POST;
    }

    @Override
    public String path() {
        return basePath() + "auth";
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
    public void handleAuthenticated(RoutingContext ctx) throws Exception {
        ctx.response().setStatusCode(200);
        ctx.response().putHeader(WebServer.AUTH_SCHEME_HEADER, auth.getScheme().toString());
        ctx.response().end();
    }
}
