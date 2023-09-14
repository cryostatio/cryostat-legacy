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

import static org.mockito.Mockito.when;

import java.util.Set;
import java.util.concurrent.CompletableFuture;

import io.cryostat.net.AuthManager;
import io.cryostat.net.AuthenticationErrorException;
import io.cryostat.net.security.ResourceAction;

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import io.vertx.ext.web.RoutingContext;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbstractPermissionedDataFetcherTest {
    AbstractPermissionedDataFetcher<?> fetcher;

    @Mock AuthManager auth;

    @Mock DataFetchingEnvironment env;
    @Mock GraphQLContext graphCtx;
    @Mock RoutingContext ctx;
    @Mock FilterInput filter;

    @BeforeEach
    void setup() {
        this.fetcher = new PermissionedDataFetcher(auth);
    }

    @Test
    void shouldHaveExpectedRequiredPermissions() {
        MatcherAssert.assertThat(fetcher.resourceActions(), Matchers.equalTo(ResourceAction.NONE));
    }

    @Test
    void shouldThrowAuthorizationError() throws Exception {
        when(env.getGraphQlContext()).thenReturn(graphCtx);

        when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(false));

        AuthenticationErrorException ex =
                Assertions.assertThrows(AuthenticationErrorException.class, () -> fetcher.get(env));
        MatcherAssert.assertThat(ex.getMessage(), Matchers.equalTo("Unauthorized"));
    }

    static class PermissionedDataFetcher extends AbstractPermissionedDataFetcher<String> {
        PermissionedDataFetcher(AuthManager auth) {
            super(auth);
        }

        @Override
        public Set<ResourceAction> resourceActions() {
            return ResourceAction.NONE;
        }

        @Override
        String getAuthenticated(DataFetchingEnvironment environment) throws Exception {
            return null;
        }

        @Override
        Set<String> applicableContexts() {
            return null;
        }

        @Override
        String name() {
            return null;
        }
    }
}
