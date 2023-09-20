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

import io.cryostat.net.AuthManager;
import io.cryostat.net.AuthenticationErrorException;
import io.cryostat.net.security.PermissionedAction;

import graphql.GraphQLContext;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.vertx.core.http.HttpHeaders;
import io.vertx.ext.web.RoutingContext;

abstract class AbstractPermissionedDataFetcher<T> implements DataFetcher<T>, PermissionedAction {

    protected final AuthManager auth;

    AbstractPermissionedDataFetcher(AuthManager auth) {
        this.auth = auth;
    }

    abstract Set<String> applicableContexts();

    abstract String name();

    boolean blocking() {
        return true;
    }

    @Override
    public final T get(DataFetchingEnvironment environment) throws Exception {
        GraphQLContext graphCtx = environment.getGraphQlContext();
        RoutingContext ctx = graphCtx.get(RoutingContext.class);
        boolean authenticated =
                auth.validateHttpHeader(
                                () -> ctx.request().getHeader(HttpHeaders.AUTHORIZATION),
                                resourceActions())
                        .get();
        if (!authenticated) {
            throw new AuthenticationErrorException("Unauthorized");
        }
        return getAuthenticated(environment);
    }

    abstract T getAuthenticated(DataFetchingEnvironment environment) throws Exception;
}
