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

import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.cryostat.MainModule;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.RequestHandler;
import io.cryostat.net.web.http.api.ApiVersion;

import com.google.gson.Gson;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
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
class ApiGetHandlerTest {

    AbstractV2RequestHandler<ApiGetHandler.ApiResponse> handler;
    @Mock WebServer webServer;
    Set<RequestHandler> requestHandlers;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock Logger logger;
    Gson gson = MainModule.provideGson(logger);

    @BeforeEach
    void setup() {
        this.requestHandlers = new HashSet<>();
        this.handler =
                new ApiGetHandler(
                        () -> webServer, () -> requestHandlers, auth, credentialsManager, gson);
    }

    @Nested
    class BasicHandlerDefinition {
        @Test
        void shouldBeGETHandler() {
            MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.GET));
        }

        @Test
        void shouldBeGenericAPIVersion() {
            MatcherAssert.assertThat(handler.apiVersion(), Matchers.equalTo(ApiVersion.GENERIC));
        }

        @Test
        void shouldHaveExpectedPath() {
            MatcherAssert.assertThat(handler.path(), Matchers.equalTo("/api"));
        }

        @Test
        void shouldHaveExpectedRequiredPermissions() {
            MatcherAssert.assertThat(handler.resourceActions(), Matchers.equalTo(Set.of()));
        }

        @Test
        void shouldProduceJson() {
            MatcherAssert.assertThat(
                    handler.produces(), Matchers.equalTo(List.of(HttpMimeType.JSON)));
        }

        @Test
        void shouldNotRequireAuthentication() {
            MatcherAssert.assertThat(handler.requiresAuthentication(), Matchers.is(false));
        }
    }

    @Nested
    class RequestHandling {

        @Mock RequestParameters requestParams;
        URL hostUrl;

        @BeforeEach
        void setup() throws Exception {
            this.hostUrl = new URL("http://localhost:8181/");
            Mockito.when(webServer.getHostUrl()).thenReturn(hostUrl);
        }

        @Test
        void shouldProduceResultWithOverviewAndEndpoints() throws Exception {
            RequestHandler testHandler1 =
                    new TestRequestHandler() {
                        @Override
                        public ApiVersion apiVersion() {
                            return ApiVersion.V1;
                        }

                        @Override
                        public String path() {
                            return basePath() + "test";
                        }

                        @Override
                        public HttpMethod httpMethod() {
                            return HttpMethod.GET;
                        }

                        @Override
                        public Set<ResourceAction> resourceActions() {
                            return Set.of();
                        }
                    };
            requestHandlers.add(testHandler1);

            IntermediateResponse<ApiGetHandler.ApiResponse> response =
                    handler.handle(requestParams);

            ApiGetHandler.ApiResponse body = response.getBody();
            MatcherAssert.assertThat(
                    body.handlers,
                    Matchers.equalTo(List.of(new ApiGetHandler.SerializedHandler(testHandler1))));
            MatcherAssert.assertThat(
                    body.resourceFilePath, Matchers.equalTo(new URL(hostUrl, "HTTP_API.md")));
        }

        @Test
        void shouldFilterNonAvailableHandlers() throws Exception {
            RequestHandler testHandler1 =
                    new TestRequestHandler() {
                        @Override
                        public ApiVersion apiVersion() {
                            return ApiVersion.V1;
                        }

                        @Override
                        public String path() {
                            return basePath() + "test1";
                        }

                        @Override
                        public HttpMethod httpMethod() {
                            return HttpMethod.GET;
                        }

                        @Override
                        public boolean isAvailable() {
                            return false;
                        }

                        @Override
                        public Set<ResourceAction> resourceActions() {
                            return Set.of();
                        }
                    };
            RequestHandler testHandler2 =
                    new TestRequestHandler() {
                        @Override
                        public ApiVersion apiVersion() {
                            return ApiVersion.V1;
                        }

                        @Override
                        public String path() {
                            return basePath() + "test2";
                        }

                        @Override
                        public HttpMethod httpMethod() {
                            return HttpMethod.GET;
                        }

                        @Override
                        public Set<ResourceAction> resourceActions() {
                            return Set.of();
                        }
                    };
            requestHandlers.add(testHandler1);
            requestHandlers.add(testHandler2);

            IntermediateResponse<ApiGetHandler.ApiResponse> response =
                    handler.handle(requestParams);

            ApiGetHandler.ApiResponse body = response.getBody();
            MatcherAssert.assertThat(
                    body.handlers,
                    Matchers.equalTo(List.of(new ApiGetHandler.SerializedHandler(testHandler2))));
        }

        @Test
        void shouldFilterGenericHandlers() throws Exception {
            RequestHandler testHandler1 =
                    new TestRequestHandler() {
                        @Override
                        public ApiVersion apiVersion() {
                            return ApiVersion.GENERIC;
                        }

                        @Override
                        public String path() {
                            return basePath() + "test1";
                        }

                        @Override
                        public HttpMethod httpMethod() {
                            return HttpMethod.GET;
                        }

                        @Override
                        public Set<ResourceAction> resourceActions() {
                            return Set.of();
                        }
                    };
            RequestHandler testHandler2 =
                    new TestRequestHandler() {
                        @Override
                        public ApiVersion apiVersion() {
                            return ApiVersion.V1;
                        }

                        @Override
                        public String path() {
                            return basePath() + "test2";
                        }

                        @Override
                        public HttpMethod httpMethod() {
                            return HttpMethod.GET;
                        }

                        @Override
                        public Set<ResourceAction> resourceActions() {
                            return Set.of();
                        }
                    };
            requestHandlers.add(testHandler1);
            requestHandlers.add(testHandler2);

            IntermediateResponse<ApiGetHandler.ApiResponse> response =
                    handler.handle(requestParams);

            ApiGetHandler.ApiResponse body = response.getBody();
            MatcherAssert.assertThat(
                    body.handlers,
                    Matchers.equalTo(List.of(new ApiGetHandler.SerializedHandler(testHandler2))));
        }

        @Test
        void shouldSortHandlersByPath() throws Exception {
            RequestHandler testHandler1 =
                    new TestRequestHandler() {
                        @Override
                        public ApiVersion apiVersion() {
                            return ApiVersion.V1;
                        }

                        @Override
                        public String path() {
                            return basePath() + "test1";
                        }

                        @Override
                        public HttpMethod httpMethod() {
                            return HttpMethod.GET;
                        }

                        @Override
                        public Set<ResourceAction> resourceActions() {
                            return Set.of();
                        }
                    };
            RequestHandler testHandler2 =
                    new TestRequestHandler() {
                        @Override
                        public ApiVersion apiVersion() {
                            return ApiVersion.V2;
                        }

                        @Override
                        public String path() {
                            return basePath() + "test2/foo";
                        }

                        @Override
                        public HttpMethod httpMethod() {
                            return HttpMethod.GET;
                        }

                        @Override
                        public Set<ResourceAction> resourceActions() {
                            return Set.of();
                        }
                    };
            RequestHandler testHandler3 =
                    new TestRequestHandler() {
                        @Override
                        public ApiVersion apiVersion() {
                            return ApiVersion.V2;
                        }

                        @Override
                        public String path() {
                            return basePath() + "test2";
                        }

                        @Override
                        public HttpMethod httpMethod() {
                            return HttpMethod.POST;
                        }

                        @Override
                        public Set<ResourceAction> resourceActions() {
                            return Set.of();
                        }
                    };
            RequestHandler testHandler4 =
                    new TestRequestHandler() {
                        @Override
                        public ApiVersion apiVersion() {
                            return ApiVersion.V1;
                        }

                        @Override
                        public String path() {
                            return basePath() + "a";
                        }

                        @Override
                        public HttpMethod httpMethod() {
                            return HttpMethod.PATCH;
                        }

                        @Override
                        public Set<ResourceAction> resourceActions() {
                            return Set.of();
                        }
                    };
            requestHandlers.add(testHandler1);
            requestHandlers.add(testHandler2);
            requestHandlers.add(testHandler3);
            requestHandlers.add(testHandler4);

            IntermediateResponse<ApiGetHandler.ApiResponse> response =
                    handler.handle(requestParams);

            ApiGetHandler.ApiResponse body = response.getBody();
            MatcherAssert.assertThat(
                    body.handlers,
                    Matchers.equalTo(
                            List.of(
                                    new ApiGetHandler.SerializedHandler(testHandler4),
                                    new ApiGetHandler.SerializedHandler(testHandler1),
                                    new ApiGetHandler.SerializedHandler(testHandler3),
                                    new ApiGetHandler.SerializedHandler(testHandler2))));
        }

        @Test
        void shouldFilterRepeatedHandlers() throws Exception {
            RequestHandler testHandler1 =
                    new TestRequestHandler() {
                        @Override
                        public ApiVersion apiVersion() {
                            return ApiVersion.V2;
                        }

                        @Override
                        public String path() {
                            return basePath() + "test";
                        }

                        @Override
                        public HttpMethod httpMethod() {
                            return HttpMethod.POST;
                        }

                        @Override
                        public Set<ResourceAction> resourceActions() {
                            return Set.of();
                        }
                    };
            // duplicate on purpose - this will serialize identically. This simulates ex.
            // TargtPostHandler and TargetPostBodyHandler, which also serialize identically.
            RequestHandler testHandler2 =
                    new TestRequestHandler() {
                        @Override
                        public ApiVersion apiVersion() {
                            return ApiVersion.V2;
                        }

                        @Override
                        public String path() {
                            return basePath() + "test";
                        }

                        @Override
                        public HttpMethod httpMethod() {
                            return HttpMethod.POST;
                        }

                        @Override
                        public Set<ResourceAction> resourceActions() {
                            return Set.of();
                        }
                    };
            requestHandlers.add(testHandler1);
            requestHandlers.add(testHandler2);

            IntermediateResponse<ApiGetHandler.ApiResponse> response =
                    handler.handle(requestParams);

            ApiGetHandler.ApiResponse body = response.getBody();
            MatcherAssert.assertThat(
                    body.handlers,
                    Matchers.equalTo(List.of(new ApiGetHandler.SerializedHandler(testHandler1))));
        }
    }

    abstract static class TestRequestHandler implements RequestHandler {
        @Override
        public void handle(RoutingContext ctx) {
            ctx.next();
        }
    }
}
