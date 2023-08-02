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

import javax.inject.Inject;

import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;

import graphql.GraphQL;
import io.vertx.core.http.HttpMethod;

class GraphQLGetHandler extends GraphQLPostHandler {

    @Inject
    GraphQLGetHandler(GraphQL graph, AuthManager auth, Logger logger) {
        super(graph, auth, logger);
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.GET;
    }
}
