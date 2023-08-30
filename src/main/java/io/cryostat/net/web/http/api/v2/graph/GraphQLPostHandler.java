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
package io.cryostat.net.web.http.api.v2.graph;

import java.util.Set;
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;

import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.RequestHandler;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.net.web.http.api.v2.ApiException;

import graphql.GraphQL;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.graphql.GraphQLHandler;

class GraphQLPostHandler implements RequestHandler {

    static final String PATH = "graphql";

    private final GraphQLHandler handler;
    private final AuthManager auth;
    private final Logger logger;

    @Inject
    GraphQLPostHandler(GraphQL graph, AuthManager auth, Logger logger) {
        this.handler = GraphQLHandler.create(graph);
        this.auth = auth;
        this.logger = logger;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.V2_2;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.POST;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        // no permissions directly required here. Specific permissions may be required by fetchers
        // and mutators that we invoke - see AbstractPermissionedDataFetcher
        return ResourceAction.NONE;
    }

    @Override
    public String path() {
        return basePath() + PATH;
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public void handle(RoutingContext ctx) {
        try {
            if (!auth.validateHttpHeader(
                            () -> ctx.request().getHeader(HttpHeaders.AUTHORIZATION),
                            resourceActions())
                    .get()) {
                throw new ApiException(401);
            }
        } catch (InterruptedException | ExecutionException e) {
            throw new ApiException(500, e);
        }
        JsonObject body = ctx.body().asJsonObject();
        logger.info("GraphQL query: {}", body.getString("query"));
        this.handler.handle(ctx);
    }
}
