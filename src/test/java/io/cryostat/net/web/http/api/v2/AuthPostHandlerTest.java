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

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import io.cryostat.MainModule;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;
import io.cryostat.net.AuthenticationScheme;
import io.cryostat.net.UnknownUserException;
import io.cryostat.net.UserInfo;
import io.cryostat.net.security.ResourceAction;

import com.google.gson.Gson;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.exception.ExceptionUtils;
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
class AuthPostHandlerTest {

    AuthPostHandler handler;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock Logger logger;
    Gson gson = MainModule.provideGson(logger);

    @Mock RoutingContext ctx;
    @Mock RequestParameters requestParams;

    @BeforeEach
    void setup() {
        this.handler = new AuthPostHandler(auth, credentialsManager, gson);

        HttpServerRequest req = Mockito.mock(HttpServerRequest.class);
        MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        Mockito.lenient().when(req.headers()).thenReturn(headers);
        Mockito.lenient().when(ctx.request()).thenReturn(req);
    }

    @Test
    void shouldHandlePOST() {
        MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.POST));
    }

    @Test
    void shouldHandleCorrectPath() {
        MatcherAssert.assertThat(handler.path(), Matchers.equalTo("/api/v2.1/auth"));
    }

    @Test
    void shouldHaveExpectedRequiredPermissions() {
        MatcherAssert.assertThat(handler.resourceActions(), Matchers.equalTo(ResourceAction.NONE));
    }

    @Test
    void shouldThrow401OnInvalidToken() throws Exception {
        Mockito.when(auth.getLoginRedirectUrl(Mockito.any(), Mockito.any()))
                .thenReturn(Optional.empty());
        Mockito.when(auth.getScheme()).thenReturn(AuthenticationScheme.BASIC);

        Mockito.when(auth.getUserInfo(Mockito.any()))
                .thenReturn(CompletableFuture.failedFuture(new UnknownUserException("unknown")));

        ApiException ex =
                Assertions.assertThrows(ApiException.class, () -> handler.handle(requestParams));

        MatcherAssert.assertThat(
                ExceptionUtils.getRootCause(ex), Matchers.instanceOf(UnknownUserException.class));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(401));
    }

    @Test
    void shouldSend302WhenRedirectRequired() throws Exception {
        Mockito.when(auth.getLoginRedirectUrl(Mockito.any(), Mockito.any()))
                .thenReturn(Optional.of("https://oauth.redirect-url.com"));

        IntermediateResponse<UserInfo> response = handler.handle(requestParams);

        Mockito.verify(auth, Mockito.never()).getUserInfo(Mockito.any());

        MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(302));
        MatcherAssert.assertThat(
                response.getHeaders().get("X-Location"),
                Matchers.equalTo("https://oauth.redirect-url.com"));
        MatcherAssert.assertThat(
                response.getHeaders().get("access-control-expose-headers"),
                Matchers.equalTo("Location"));
        MatcherAssert.assertThat(response.getBody(), Matchers.equalTo(null));
    }
}
