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
package io.cryostat.net.openshift;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;

import io.cryostat.MainModule;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Environment;
import io.cryostat.net.AuthenticationScheme;
import io.cryostat.net.MissingEnvironmentVariableException;
import io.cryostat.net.PermissionDeniedException;
import io.cryostat.net.UserInfo;
import io.cryostat.net.openshift.OpenShiftAuthManager.GroupResource;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.ResourceType;
import io.cryostat.net.security.ResourceVerb;
import io.cryostat.util.resource.ClassPropertiesLoader;

import com.github.benmanes.caffeine.cache.Scheduler;
import com.google.gson.Gson;
import io.fabric8.kubernetes.api.model.StatusCause;
import io.fabric8.kubernetes.api.model.StatusDetails;
import io.fabric8.kubernetes.api.model.authentication.TokenReview;
import io.fabric8.kubernetes.api.model.authentication.TokenReviewBuilder;
import io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectAccessReview;
import io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectAccessReviewBuilder;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.http.HttpClient;
import io.fabric8.kubernetes.client.http.HttpRequest;
import io.fabric8.kubernetes.client.http.HttpResponse;
import io.fabric8.openshift.api.model.OAuthAccessToken;
import io.fabric8.openshift.api.model.OAuthAccessTokenList;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.server.mock.EnableOpenShiftMockClient;
import io.fabric8.openshift.client.server.mock.OpenShiftMockServer;
import io.fabric8.openshift.client.server.mock.OpenShiftMockServerExtension;
import io.vertx.core.json.JsonObject;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, OpenShiftMockServerExtension.class})
@EnableOpenShiftMockClient(https = false, crud = false)
class OpenShiftAuthManagerTest {
    static final String SUBJECT_REVIEW_API_PATH =
            "/apis/authorization.k8s.io/v1/selfsubjectaccessreviews";
    static final String TOKEN_REVIEW_API_PATH = "/apis/authentication.k8s.io/v1/tokenreviews";
    static final String BASE_URL = "https://oauth-issuer";
    static final String AUTHORIZATION_URL = BASE_URL + "/oauth/authorize";
    static final String NAMESPACE = "namespace";
    static final String SERVICE_ACCOUNT_TOKEN = "serviceAccountToken";
    static final String CLIENT_ID = "oauth-client-id";
    static final String SERVICE_ACCOUNT =
            String.format("system:serviceaccount:%s:%s", NAMESPACE, CLIENT_ID);
    static final String BASE_ROLE_SCOPE = "oauth-role-scope";
    static final String CUSTOM_ROLE_SCOPE = "custom-oauth-role-scope";
    static final String BASE_TOKEN_SCOPE =
            String.format("user:check-access+role:%s:%s", BASE_ROLE_SCOPE, NAMESPACE);
    static final String CUSTOM_TOKEN_SCOPE =
            String.format("%s+role:%s:%s", BASE_TOKEN_SCOPE, CUSTOM_ROLE_SCOPE, NAMESPACE);
    static final String BASE_OAUTH_QUERY_PARAMETERS =
            String.format(
                    "?client_id=%s&response_type=token&response_mode=fragment&scope=%s",
                    SERVICE_ACCOUNT.replaceAll(":", "%3A"),
                    BASE_TOKEN_SCOPE.replaceAll(":", "%3A"));
    static final String CUSTOM_OAUTH_QUERY_PARAMETERS =
            String.format(
                    "?client_id=%s&response_type=token&response_mode=fragment&scope=%s",
                    SERVICE_ACCOUNT.replaceAll(":", "%3A"),
                    CUSTOM_TOKEN_SCOPE.replaceAll(":", "%3A"));
    static final String OAUTH_METADATA =
            new JsonObject(Map.of("issuer", BASE_URL, "authorization_endpoint", AUTHORIZATION_URL))
                    .toString();
    static final String BASE_EXPECTED_LOGIN_REDIRECT_URL =
            AUTHORIZATION_URL + BASE_OAUTH_QUERY_PARAMETERS;
    static final String CUSTOM_EXPECTED_LOGIN_REDIRECT_URL =
            AUTHORIZATION_URL + CUSTOM_OAUTH_QUERY_PARAMETERS;
    static final String EXPECTED_LOGOUT_REDIRECT_URL = BASE_URL + "/logout";

    OpenShiftAuthManager mgr;
    @Mock Environment env;
    @Mock ClassPropertiesLoader classPropertiesLoader;
    @Mock Logger logger;
    @Mock HttpClient httpClient;
    OpenShiftClient client;
    OpenShiftMockServer server;
    TokenProvider tokenProvider;
    Gson gson = MainModule.provideGson(logger);
    @Mock CompletableFuture<String> redirectUrl;

    @BeforeEach
    void setup() throws IOException {
        client = Mockito.spy(client);
        tokenProvider = new TokenProvider(client);
        Mockito.lenient()
                .when(classPropertiesLoader.loadAsMap(Mockito.any()))
                .thenReturn(
                        Map.of("RECORDING", "pods/exec", "CERTIFICATE", "deployments.apps,pods"));
        mgr =
                new OpenShiftAuthManager(
                        env,
                        () -> NAMESPACE,
                        () -> client,
                        tokenProvider,
                        classPropertiesLoader,
                        gson,
                        Runnable::run,
                        Scheduler.disabledScheduler(),
                        logger);
    }

    @ParameterizedTest
    @MethodSource("getResourceMaps")
    void testPropertiesResourceMapProcessing(Map<String, Object> map) throws IOException {
        ClassPropertiesLoader loader = Mockito.mock(ClassPropertiesLoader.class);

        Map<String, String> resourcesMap = new HashMap<>();
        map.entrySet().stream()
                .filter(e -> !e.getKey().equals("expected"))
                .forEach(e -> resourcesMap.put((String) e.getKey(), (String) e.getValue()));

        Map<ResourceType, Set<GroupResource>> expected =
                (Map<ResourceType, Set<GroupResource>>) map.get("expected");

        Mockito.when(loader.loadAsMap(Mockito.any())).thenReturn(resourcesMap);
        Map<ResourceType, Set<GroupResource>> result =
                OpenShiftAuthManager.processResourceMapping(loader, logger);

        MatcherAssert.assertThat(result, Matchers.equalTo(expected));
    }

    private static Stream<Map<String, Object>> getResourceMaps() {
        return Stream.of(
                Map.of("expected", Map.of()),
                Map.of(
                        ResourceType.RECORDING.name(),
                        "cryostats.operator.cryostat.io",
                        "expected",
                        Map.of(
                                ResourceType.RECORDING,
                                Set.of(
                                        new GroupResource(
                                                "operator.cryostat.io", "cryostats", null)))),
                Map.of(
                        ResourceType.RECORDING.name(),
                        "deployments.apps/scale",
                        "expected",
                        Map.of(
                                ResourceType.RECORDING,
                                Set.of(new GroupResource("apps", "deployments", "scale")))),
                Map.of(
                        ResourceType.RECORDING.name(),
                        "",
                        "expected",
                        Map.of(ResourceType.RECORDING, Set.<String>of())),
                Map.of(
                        ResourceType.RECORDING.name(),
                        ",",
                        "expected",
                        Map.of(ResourceType.RECORDING, Set.<String>of())),
                Map.of(
                        ResourceType.RECORDING.name(),
                        "pods/exec, deployments.apps",
                        "expected",
                        Map.of(
                                ResourceType.RECORDING,
                                Set.of(
                                        new GroupResource("", "pods", "exec"),
                                        new GroupResource("apps", "deployments", null)))));
    }

    @Test
    void shouldHandleBearerAuthentication() {
        MatcherAssert.assertThat(mgr.getScheme(), Matchers.equalTo(AuthenticationScheme.BEARER));
    }

    @Test
    void shouldReturnUserInfo() throws Exception {
        TokenReview tokenReview =
                new TokenReviewBuilder()
                        .withNewStatus()
                        .withAuthenticated(true)
                        .withNewUser()
                        .withUsername("fooUser")
                        .endUser()
                        .endStatus()
                        .build();
        server.expect()
                .post()
                .withPath(TOKEN_REVIEW_API_PATH)
                .andReturn(HttpURLConnection.HTTP_CREATED, tokenReview)
                .once();

        UserInfo userInfo = mgr.getUserInfo(() -> "Bearer abc123").get();
        MatcherAssert.assertThat(userInfo.getUsername(), Matchers.equalTo("fooUser"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldNotValidateBlankToken(String tok) throws Exception {
        MatcherAssert.assertThat(
                mgr.validateToken(() -> tok, ResourceAction.NONE).get(), Matchers.is(false));
    }

    @Test
    void shouldValidateTokenWithNoRequiredPermissions() throws Exception {
        TokenReview tokenReview =
                new TokenReviewBuilder()
                        .withNewStatus()
                        .withAuthenticated(true)
                        .endStatus()
                        .build();
        server.expect()
                .post()
                .withPath(TOKEN_REVIEW_API_PATH)
                .andReturn(HttpURLConnection.HTTP_CREATED, tokenReview)
                .once();

        MatcherAssert.assertThat(
                mgr.validateToken(() -> "userToken", ResourceAction.NONE).get(), Matchers.is(true));
    }

    @Test
    void shouldNotValidateTokenWithNoRequiredPermissionsButNoTokenAccess() throws Exception {
        TokenReview tokenReview =
                new TokenReviewBuilder()
                        .withNewStatus()
                        .withAuthenticated(false)
                        .endStatus()
                        .build();
        server.expect()
                .post()
                .withPath(TOKEN_REVIEW_API_PATH)
                .andReturn(HttpURLConnection.HTTP_CREATED, tokenReview)
                .once();

        MatcherAssert.assertThat(
                mgr.validateToken(() -> "userToken", ResourceAction.NONE).get(),
                Matchers.is(false));
    }

    @Test
    void shouldValidateTokenWithSufficientPermissions() throws Exception {
        SelfSubjectAccessReview accessReview =
                new SelfSubjectAccessReviewBuilder()
                        .withNewStatus()
                        .withAllowed(true)
                        .endStatus()
                        .build();
        server.expect()
                .post()
                .withPath(SUBJECT_REVIEW_API_PATH)
                .andReturn(HttpURLConnection.HTTP_CREATED, accessReview)
                .once();

        MatcherAssert.assertThat(
                mgr.validateToken(() -> "token", Set.of(ResourceAction.READ_RECORDING)).get(),
                Matchers.is(true));
    }

    @Test
    void shouldNotValidateTokenWithInsufficientPermissions() throws Exception {
        SelfSubjectAccessReview accessReview =
                new SelfSubjectAccessReviewBuilder()
                        .withNewStatus()
                        .withAllowed(false)
                        .endStatus()
                        .build();
        server.expect()
                .post()
                .withPath(SUBJECT_REVIEW_API_PATH)
                .andReturn(HttpURLConnection.HTTP_CREATED, accessReview)
                .once();

        ExecutionException ee =
                Assertions.assertThrows(
                        ExecutionException.class,
                        () ->
                                mgr.validateToken(
                                                () -> "token",
                                                Set.of(ResourceAction.READ_RECORDING))
                                        .get());
        ee.printStackTrace();
        ExceptionUtils.getRootCause(ee).printStackTrace();
        MatcherAssert.assertThat(
                ExceptionUtils.getRootCause(ee),
                Matchers.instanceOf(PermissionDeniedException.class));
        PermissionDeniedException pde = (PermissionDeniedException) ExceptionUtils.getRootCause(ee);
        MatcherAssert.assertThat(pde.getNamespace(), Matchers.equalTo(NAMESPACE));
        MatcherAssert.assertThat(pde.getResourceType(), Matchers.equalTo("pods/exec"));
        MatcherAssert.assertThat(pde.getVerb(), Matchers.equalTo("get"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Bearer ", "invalidHeader"})
    void shouldSendRedirectResponseOnEmptyOrInvalidHeaders(String headers) throws Exception {
        Mockito.when(env.getEnv(Mockito.anyString())).thenReturn(CLIENT_ID, BASE_ROLE_SCOPE, null);

        Mockito.when(client.getHttpClient()).thenReturn(httpClient);
        Mockito.when(client.getMasterUrl()).thenReturn(new URL("https://example.com"));

        HttpRequest.Builder requestBuilder = Mockito.mock(HttpRequest.Builder.class);
        Mockito.when(requestBuilder.uri(Mockito.any(URI.class))).thenReturn(requestBuilder);
        Mockito.when(requestBuilder.header(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(requestBuilder);

        HttpRequest request = Mockito.mock(HttpRequest.class);
        Mockito.when(requestBuilder.build()).thenReturn(request);
        Mockito.when(httpClient.newHttpRequestBuilder()).thenReturn(requestBuilder);

        HttpResponse<String> resp = Mockito.mock(HttpResponse.class);
        Mockito.when(resp.body()).thenReturn(OAUTH_METADATA);

        Mockito.when(httpClient.sendAsync(request, String.class))
                .thenReturn(CompletableFuture.completedFuture(resp));

        String actualLoginRedirectUrl =
                mgr.getLoginRedirectUrl(() -> headers, ResourceAction.NONE).get();

        MatcherAssert.assertThat(
                actualLoginRedirectUrl, Matchers.equalTo(BASE_EXPECTED_LOGIN_REDIRECT_URL));
    }

    @Test
    void shouldSendRedirectResponseWithValidCustomOAuthRoleScope() throws Exception {
        Mockito.when(env.getEnv(Mockito.anyString()))
                .thenReturn(CLIENT_ID, BASE_ROLE_SCOPE, CUSTOM_ROLE_SCOPE);

        Mockito.when(client.getHttpClient()).thenReturn(httpClient);
        Mockito.when(client.getMasterUrl()).thenReturn(new URL("https://example.com"));

        HttpRequest.Builder requestBuilder = Mockito.mock(HttpRequest.Builder.class);
        Mockito.when(requestBuilder.uri(Mockito.any(URI.class))).thenReturn(requestBuilder);
        Mockito.when(requestBuilder.header(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(requestBuilder);

        HttpRequest request = Mockito.mock(HttpRequest.class);
        Mockito.when(requestBuilder.build()).thenReturn(request);
        Mockito.when(httpClient.newHttpRequestBuilder()).thenReturn(requestBuilder);

        HttpResponse<String> resp = Mockito.mock(HttpResponse.class);
        Mockito.when(resp.body()).thenReturn(OAUTH_METADATA);

        Mockito.when(httpClient.sendAsync(request, String.class))
                .thenReturn(CompletableFuture.completedFuture(resp));

        String actualLoginRedirectUrl =
                mgr.getLoginRedirectUrl(() -> "Bearer ", ResourceAction.NONE).get();

        MatcherAssert.assertThat(
                actualLoginRedirectUrl, Matchers.equalTo(CUSTOM_EXPECTED_LOGIN_REDIRECT_URL));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void shouldThrowWhenCustomOAuthRoleScopeIsInvalid(String invalidCustomRoleScope)
            throws Exception {
        Mockito.when(env.getEnv(Mockito.anyString()))
                .thenReturn(CLIENT_ID, BASE_ROLE_SCOPE, invalidCustomRoleScope);
        ExecutionException ee =
                Assertions.assertThrows(
                        ExecutionException.class,
                        () -> mgr.getLoginRedirectUrl(() -> "Bearer ", ResourceAction.NONE).get());
        MatcherAssert.assertThat(
                ExceptionUtils.getRootCause(ee),
                Matchers.instanceOf(IllegalArgumentException.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer invalidToken", "Bearer 1234"})
    void shouldSendRedirectResponseOnInvalidToken(String headers) throws Exception {
        Mockito.when(env.getEnv(Mockito.anyString()))
                .thenReturn(CLIENT_ID, BASE_ROLE_SCOPE, null, CLIENT_ID, BASE_ROLE_SCOPE, null);

        Mockito.when(client.getHttpClient()).thenReturn(httpClient);
        Mockito.when(client.getMasterUrl()).thenReturn(new URL("https://example.com"));

        HttpRequest.Builder requestBuilder = Mockito.mock(HttpRequest.Builder.class);
        Mockito.when(requestBuilder.uri(Mockito.any(URI.class))).thenReturn(requestBuilder);
        Mockito.when(requestBuilder.header(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(requestBuilder);

        HttpRequest request = Mockito.mock(HttpRequest.class);
        Mockito.when(requestBuilder.build()).thenReturn(request);
        Mockito.when(httpClient.newHttpRequestBuilder()).thenReturn(requestBuilder);

        HttpResponse<String> resp = Mockito.mock(HttpResponse.class);
        Mockito.when(resp.body()).thenReturn(OAUTH_METADATA);

        Mockito.when(httpClient.sendAsync(request, String.class))
                .thenReturn(CompletableFuture.completedFuture(resp));

        String actualLoginRedirectUrl =
                mgr.getLoginRedirectUrl(() -> headers, ResourceAction.NONE).get();

        MatcherAssert.assertThat(
                actualLoginRedirectUrl, Matchers.equalTo(BASE_EXPECTED_LOGIN_REDIRECT_URL));
    }

    // CLIENT_ID and BASE_OAUTH_ROLE must be set while CUSTOM_OAUTH_ROLE is optional
    @ParameterizedTest
    @CsvSource(value = {",", CLIENT_ID + ",", "," + BASE_ROLE_SCOPE})
    void shouldThrowWhenEnvironmentVariablesMissing(String clientId, String tokenScope)
            throws Exception {
        Mockito.when(env.getEnv(Mockito.anyString())).thenReturn(clientId, tokenScope, null);

        ExecutionException ee =
                Assertions.assertThrows(
                        ExecutionException.class,
                        () -> mgr.getLoginRedirectUrl(() -> "Bearer ", ResourceAction.NONE).get());
        MatcherAssert.assertThat(
                ExceptionUtils.getRootCause(ee),
                Matchers.instanceOf(MissingEnvironmentVariableException.class));
    }

    @Test
    void shouldCacheOAuthServerResponse() throws Exception {
        Mockito.when(client.getHttpClient()).thenReturn(httpClient);
        Mockito.when(client.getMasterUrl()).thenReturn(new URL("https://example.com"));

        Mockito.when(env.getEnv(Mockito.anyString()))
                .thenReturn(CLIENT_ID, BASE_ROLE_SCOPE, null, CLIENT_ID, BASE_ROLE_SCOPE, null);

        HttpRequest.Builder requestBuilder = Mockito.mock(HttpRequest.Builder.class);
        Mockito.when(requestBuilder.uri(Mockito.any(URI.class))).thenReturn(requestBuilder);
        Mockito.when(requestBuilder.header(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(requestBuilder);

        HttpRequest request = Mockito.mock(HttpRequest.class);
        Mockito.when(requestBuilder.build()).thenReturn(request);
        Mockito.when(httpClient.newHttpRequestBuilder()).thenReturn(requestBuilder);

        HttpResponse<String> resp = Mockito.mock(HttpResponse.class);
        Mockito.when(resp.body()).thenReturn(OAUTH_METADATA);

        ArgumentCaptor<HttpRequest> reqCaptor = ArgumentCaptor.forClass(HttpRequest.class);

        Mockito.when(httpClient.sendAsync(reqCaptor.capture(), Mockito.eq(String.class)))
                .thenReturn(CompletableFuture.completedFuture(resp));

        String firstRedirectUrl =
                mgr.getLoginRedirectUrl(() -> "Bearer", ResourceAction.NONE).get();
        MatcherAssert.assertThat(
                firstRedirectUrl, Matchers.equalTo(BASE_EXPECTED_LOGIN_REDIRECT_URL));

        String secondRedirectUrl =
                mgr.getLoginRedirectUrl(() -> "Bearer", ResourceAction.NONE).get();
        MatcherAssert.assertThat(
                secondRedirectUrl, Matchers.equalTo(BASE_EXPECTED_LOGIN_REDIRECT_URL));

        Mockito.verify(httpClient, Mockito.times(1))
                .sendAsync(Mockito.eq(request), Mockito.eq(String.class));
    }

    @Test
    void shouldReturnLogoutRedirectUrl() throws Exception {
        Resource<OAuthAccessToken> token = Mockito.mock(Resource.class);
        NonNamespaceOperation<OAuthAccessToken, OAuthAccessTokenList, Resource<OAuthAccessToken>>
                tokens = Mockito.mock(NonNamespaceOperation.class);

        Mockito.when(client.oAuthAccessTokens()).thenReturn(tokens);
        Mockito.when(tokens.withName(Mockito.anyString())).thenReturn(token);
        Mockito.when(token.delete()).thenReturn(List.of());

        Mockito.when(client.getHttpClient()).thenReturn(httpClient);
        Mockito.when(client.getMasterUrl()).thenReturn(new URL("https://example.com"));

        HttpRequest.Builder requestBuilder = Mockito.mock(HttpRequest.Builder.class);
        Mockito.when(requestBuilder.uri(Mockito.any(URI.class))).thenReturn(requestBuilder);
        Mockito.when(requestBuilder.header(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(requestBuilder);

        HttpRequest request = Mockito.mock(HttpRequest.class);
        Mockito.when(requestBuilder.build()).thenReturn(request);
        Mockito.when(httpClient.newHttpRequestBuilder()).thenReturn(requestBuilder);

        HttpResponse<String> resp = Mockito.mock(HttpResponse.class);
        Mockito.when(resp.body()).thenReturn(OAUTH_METADATA);

        Mockito.when(httpClient.sendAsync(request, String.class))
                .thenReturn(CompletableFuture.completedFuture(resp));

        String logoutRedirectUrl = mgr.logout(() -> "Bearer myToken").get();

        MatcherAssert.assertThat(logoutRedirectUrl, Matchers.equalTo(EXPECTED_LOGOUT_REDIRECT_URL));
    }

    @Test
    void shouldLogWhenTokenDeletionFailsOnLogout() throws Exception {
        Resource<OAuthAccessToken> token = Mockito.mock(Resource.class);
        NonNamespaceOperation<OAuthAccessToken, OAuthAccessTokenList, Resource<OAuthAccessToken>>
                tokens = Mockito.mock(NonNamespaceOperation.class);

        Mockito.when(client.oAuthAccessTokens()).thenReturn(tokens);
        Mockito.when(tokens.withName(Mockito.anyString())).thenReturn(token);

        StatusDetails status = Mockito.mock(StatusDetails.class);
        StatusCause cause = Mockito.mock(StatusCause.class);
        Mockito.when(cause.getField()).thenReturn("token");
        Mockito.when(cause.getReason()).thenReturn("some reason");
        Mockito.when(cause.getMessage()).thenReturn("some message");
        Mockito.when(status.getCauses()).thenReturn(List.of(cause));
        Mockito.when(token.delete()).thenReturn(List.of(status));

        Mockito.when(client.getHttpClient()).thenReturn(httpClient);
        Mockito.when(client.getMasterUrl()).thenReturn(new URL("https://example.com"));

        HttpRequest.Builder requestBuilder = Mockito.mock(HttpRequest.Builder.class);
        Mockito.when(requestBuilder.uri(Mockito.any(URI.class))).thenReturn(requestBuilder);
        Mockito.when(requestBuilder.header(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(requestBuilder);

        HttpRequest request = Mockito.mock(HttpRequest.class);
        Mockito.when(requestBuilder.build()).thenReturn(request);
        Mockito.when(httpClient.newHttpRequestBuilder()).thenReturn(requestBuilder);

        HttpResponse<String> resp = Mockito.mock(HttpResponse.class);
        Mockito.when(resp.body()).thenReturn(OAUTH_METADATA);

        Mockito.when(httpClient.sendAsync(request, String.class))
                .thenReturn(CompletableFuture.completedFuture(resp));

        Mockito.verifyNoInteractions(logger);

        mgr.logout(() -> "Bearer myToken").get();

        ArgumentCaptor<Exception> logCaptor = ArgumentCaptor.forClass(Exception.class);
        Mockito.verify(logger).warn(logCaptor.capture());
        MatcherAssert.assertThat(
                logCaptor.getValue().toString(),
                Matchers.equalTo(
                        "io.cryostat.net.TokenNotFoundException: Token not found: [[token] some"
                                + " reason: some message]"));
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.MATCH_ANY, names = "^([a-zA-Z]+_(RECORDING|CERTIFICATE))$")
    void shouldValidateExpectedPermissionsPerSecuredResource(ResourceAction resourceAction)
            throws Exception {
        String expectedVerb;
        if (resourceAction.getVerb() == ResourceVerb.CREATE) {
            expectedVerb = "create";
        } else if (resourceAction.getVerb() == ResourceVerb.READ) {
            expectedVerb = "get";
        } else if (resourceAction.getVerb() == ResourceVerb.UPDATE) {
            expectedVerb = "patch";
        } else if (resourceAction.getVerb() == ResourceVerb.DELETE) {
            expectedVerb = "delete";
        } else {
            throw new IllegalArgumentException(resourceAction.getVerb().toString());
        }

        Set<String> expectedGroups;
        Set<String> expectedResources;
        Set<String> expectedSubResources;
        if (resourceAction.getResource() == ResourceType.RECORDING) {
            expectedGroups = Set.of("");
            expectedResources = Set.of("pods");
            expectedSubResources = Set.of("exec");
        } else if (resourceAction.getResource() == ResourceType.CERTIFICATE) {
            expectedGroups = Set.of("apps", "");
            expectedResources = Set.of("deployments", "pods");
            expectedSubResources = Set.of("");
        } else {
            throw new IllegalArgumentException(resourceAction.getResource().toString());
        }

        SelfSubjectAccessReview accessReview =
                new SelfSubjectAccessReviewBuilder()
                        .withNewStatus()
                        .withAllowed(true)
                        .endStatus()
                        .build();
        server.expect()
                .post()
                .withPath(SUBJECT_REVIEW_API_PATH)
                .andReturn(HttpURLConnection.HTTP_CREATED, accessReview)
                .times(expectedResources.size());

        String token = "abcd1234";
        MatcherAssert.assertThat(
                mgr.validateToken(() -> token, Set.of(resourceAction)).get(), Matchers.is(true));

        // server.takeRequest() returns each request fired in order, so do that repeatedly and drop
        // any initial requests that are made by the OpenShiftClient that aren't directly
        // SelfSubjectAccessReview requests made by the OpenShiftAuthManager
        int maxDroppedRequests = 2;
        int requestCount = 0;
        RecordedRequest req;
        while ((req = server.takeRequest()) != null) {
            if (++requestCount > maxDroppedRequests) {
                throw new IllegalStateException();
            }
            String path = req.getPath();
            if (SUBJECT_REVIEW_API_PATH.equals(path)) {
                break;
            }
        }
        MatcherAssert.assertThat(req.getPath(), Matchers.equalTo(SUBJECT_REVIEW_API_PATH));
        MatcherAssert.assertThat(tokenProvider.token, Matchers.equalTo(token));
        MatcherAssert.assertThat(req.getMethod(), Matchers.equalTo("POST"));

        SelfSubjectAccessReview body =
                gson.fromJson(req.getBody().readUtf8(), SelfSubjectAccessReview.class);
        MatcherAssert.assertThat(
                body.getSpec().getResourceAttributes().getVerb(), Matchers.equalTo(expectedVerb));

        Set<String> actualGroups = new HashSet<>();
        Set<String> actualResources = new HashSet<>();
        Set<String> actualSubResources = new HashSet<>();
        actualGroups.add(body.getSpec().getResourceAttributes().getGroup());
        actualResources.add(body.getSpec().getResourceAttributes().getResource());
        actualSubResources.add(body.getSpec().getResourceAttributes().getSubresource());
        // start at 1 because we've already checked the first request above
        for (int i = 1; i < expectedResources.size(); i++) {
            // request should already have been made, so there should be no time waiting for a
            // request to come in
            req = server.takeRequest(1, TimeUnit.SECONDS);
            if (req == null) {
                throw new IllegalStateException("Expected request not received in time");
            }
            body = gson.fromJson(req.getBody().readUtf8(), SelfSubjectAccessReview.class);

            MatcherAssert.assertThat(req.getPath(), Matchers.equalTo(SUBJECT_REVIEW_API_PATH));
            MatcherAssert.assertThat(tokenProvider.token, Matchers.equalTo(token));
            MatcherAssert.assertThat(req.getMethod(), Matchers.equalTo("POST"));
            MatcherAssert.assertThat(
                    body.getSpec().getResourceAttributes().getVerb(),
                    Matchers.equalTo(expectedVerb));
            actualGroups.add(body.getSpec().getResourceAttributes().getGroup());
            actualResources.add(body.getSpec().getResourceAttributes().getResource());
            actualSubResources.add(body.getSpec().getResourceAttributes().getSubresource());
        }

        MatcherAssert.assertThat(actualGroups, Matchers.equalTo(expectedGroups));
        MatcherAssert.assertThat(actualResources, Matchers.equalTo(expectedResources));
        MatcherAssert.assertThat(actualSubResources, Matchers.equalTo(expectedSubResources));
    }

    @ParameterizedTest
    @EnumSource(
            mode = EnumSource.Mode.MATCH_ALL,
            names = {
                "^[a-zA-Z]+_(?!RECORDING).*$",
                "^[a-zA-Z]+_(?!CERTIFICATE).*$",
            })
    void shouldValidateExpectedPermissionsForUnsecuredResources(ResourceAction resourceAction)
            throws Exception {
        MatcherAssert.assertThat(
                mgr.validateToken(() -> "token", Set.of(resourceAction)).get(), Matchers.is(true));
    }

    // the below parsing tests should be in a @Nested class, but this doesn't play nicely with the
    // OpenShiftMockServerExtension and results in a test NPE
    @Test
    void shouldParseBareResource() {
        GroupResource gr = OpenShiftAuthManager.GroupResource.fromString("apps");
        MatcherAssert.assertThat(gr.getGroup(), Matchers.emptyString());
        MatcherAssert.assertThat(gr.getResource(), Matchers.equalTo("apps"));
        MatcherAssert.assertThat(gr.getSubResource(), Matchers.emptyString());
    }

    @Test
    void shouldParseResourceWithGroup() {
        GroupResource gr = OpenShiftAuthManager.GroupResource.fromString("deployments.apps");
        MatcherAssert.assertThat(gr.getGroup(), Matchers.equalTo("apps"));
        MatcherAssert.assertThat(gr.getResource(), Matchers.equalTo("deployments"));
        MatcherAssert.assertThat(gr.getSubResource(), Matchers.emptyString());
    }

    @Test
    void shouldParseResourceWithGroupAndSub() {
        GroupResource gr = OpenShiftAuthManager.GroupResource.fromString("deployments.apps/scale");
        MatcherAssert.assertThat(gr.getGroup(), Matchers.equalTo("apps"));
        MatcherAssert.assertThat(gr.getResource(), Matchers.equalTo("deployments"));
        MatcherAssert.assertThat(gr.getSubResource(), Matchers.equalTo("scale"));
    }

    @Test
    void shouldParseResourceWithSub() {
        GroupResource gr = OpenShiftAuthManager.GroupResource.fromString("apps/scale");
        MatcherAssert.assertThat(gr.getGroup(), Matchers.emptyString());
        MatcherAssert.assertThat(gr.getResource(), Matchers.equalTo("apps"));
        MatcherAssert.assertThat(gr.getSubResource(), Matchers.equalTo("scale"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                ".invalid",
                "/invalid",
                "bad+format",
                "with whitespace",
            })
    void shouldThrowForInvalidInputs(String s) {
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> OpenShiftAuthManager.GroupResource.fromString(s));
    }

    private static class TokenProvider implements Function<String, OpenShiftClient> {

        private final OpenShiftClient osc;
        String token;

        TokenProvider(OpenShiftClient osc) {
            this.osc = osc;
        }

        @Override
        public OpenShiftClient apply(String token) {
            this.token = token;
            return osc;
        }
    }
}
