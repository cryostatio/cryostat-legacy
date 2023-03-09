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
package io.cryostat.net.web.http.api.beta;

import java.util.List;
import java.util.Set;

import io.cryostat.MainModule;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.net.web.http.api.v2.IntermediateResponse;
import io.cryostat.net.web.http.api.v2.RequestParameters;
import io.cryostat.rules.MatchExpression;
import io.cryostat.rules.MatchExpressionManager;

import com.google.gson.Gson;
import io.vertx.core.http.HttpMethod;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MatchExpressionsGetHandlerTest {
    MatchExpressionsGetHandler handler;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock MatchExpressionManager expressionManager;
    @Mock Logger logger;
    Gson gson = MainModule.provideGson(logger);

    @BeforeEach
    void setup() {
        this.handler =
                new MatchExpressionsGetHandler(auth, credentialsManager, expressionManager, gson);
    }

    @Nested
    class BasicHandlerDefinition {
        @Test
        void shouldBeGETHandler() {
            MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.GET));
        }

        @Test
        void shouldBeAPIBeta() {
            MatcherAssert.assertThat(handler.apiVersion(), Matchers.equalTo(ApiVersion.BETA));
        }

        @Test
        void shouldHaveExpectedPath() {
            MatcherAssert.assertThat(
                    handler.path(), Matchers.equalTo("/api/beta/matchExpressions"));
        }

        @Test
        void shouldHaveExpectedRequiredPermissions() {
            MatcherAssert.assertThat(
                    handler.resourceActions(),
                    Matchers.equalTo(Set.of(ResourceAction.READ_MATCH_EXPRESSION)));
        }

        @Test
        void shouldProduceJson() {
            MatcherAssert.assertThat(
                    handler.produces(), Matchers.equalTo(List.of(HttpMimeType.JSON)));
        }

        @Test
        void shouldRequireAuthentication() {
            MatcherAssert.assertThat(handler.requiresAuthentication(), Matchers.is(true));
        }
    }

    @Nested
    class RequestHandling {

        @Mock RequestParameters requestParams;

        @Test
        void shouldDelegateToMatchExpressionManager() throws Exception {
            MatchExpression expr1 = Mockito.mock(MatchExpression.class);
            MatchExpression expr2 = Mockito.mock(MatchExpression.class);
            Mockito.when(expressionManager.getAll()).thenReturn(List.of(expr1, expr2));

            IntermediateResponse<List<MatchExpression>> response = handler.handle(requestParams);

            List<MatchExpression> actual = response.getBody();
            MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(200));
            MatcherAssert.assertThat(
                    actual,
                    Matchers.containsInAnyOrder(Matchers.equalTo(expr1), Matchers.equalTo(expr2)));
            MatcherAssert.assertThat(actual, Matchers.hasSize(2));

            Mockito.verify(expressionManager).getAll();
        }
    }
}
