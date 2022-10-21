/*
 * Copyright The Cryostat Authors
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software (each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
