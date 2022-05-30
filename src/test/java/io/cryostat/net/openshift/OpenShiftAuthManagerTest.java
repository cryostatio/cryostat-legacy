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
package io.cryostat.net.openshift;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
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
import io.cryostat.net.OpenShiftAuthManager.PermissionDeniedException;
import io.cryostat.net.TokenNotFoundException;
import io.cryostat.net.UserInfo;
import io.cryostat.net.openshift.OpenShiftAuthManager.GroupResource;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.ResourceType;
import io.cryostat.net.security.ResourceVerb;
import io.cryostat.util.resource.ClassPropertiesLoader;

import com.github.benmanes.caffeine.cache.Scheduler;
import com.google.gson.Gson;
import io.fabric8.kubernetes.api.model.authentication.TokenReview;
import io.fabric8.kubernetes.api.model.authentication.TokenReviewBuilder;
import io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectAccessReview;
import io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectAccessReviewBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.openshift.api.model.OAuthAccessToken;
import io.fabric8.openshift.api.model.OAuthAccessTokenList;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.server.mock.EnableOpenShiftMockClient;
import io.fabric8.openshift.client.server.mock.OpenShiftMockServer;
import io.fabric8.openshift.client.server.mock.OpenShiftMockServerExtension;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

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
    static final String ROLE_SCOPE = "oauth-role-scope";
    static final String TOKEN_SCOPE =
            String.format("user:check-access+role:%s:%s", ROLE_SCOPE, NAMESPACE);
    static final String OAUTH_QUERY_PARAMETERS =
            String.format(
                    "?client_id=%s&response_type=token&response_mode=fragment&scope=%s",
                    SERVICE_ACCOUNT.replaceAll(":", "%3A"), TOKEN_SCOPE.replaceAll(":", "%3A"));
    static final String OAUTH_METADATA =
            new JsonObject(Map.of("issuer", BASE_URL, "authorization_endpoint", AUTHORIZATION_URL))
                    .toString();
    static final String EXPECTED_LOGIN_REDIRECT_URL = AUTHORIZATION_URL + OAUTH_QUERY_PARAMETERS;
    static final String EXPECTED_LOGOUT_REDIRECT_URL = BASE_URL + "/logout";

    OpenShiftAuthManager mgr;
    @Mock Environment env;
    @Mock ClassPropertiesLoader classPropertiesLoader;
    @Mock Logger logger;
    @Mock OkHttpClient httpClient;
    OpenShiftClient client;
    OpenShiftMockServer server;
    TokenProvider tokenProvider;
    Gson gson = MainModule.provideGson(logger);
    @Mock CompletableFuture<String> redirectUrl;

    @BeforeAll
    static void disableKubeConfig() {
        // FIXME Disable reading ~/.kube/config. Remove once fabric8-client updated to 5.5.0 or
        // newer.
        System.setProperty(Config.KUBERNETES_AUTH_TRYKUBECONFIG_SYSTEM_PROPERTY, "false");
        System.setProperty(Config.KUBERNETES_AUTH_TRYSERVICEACCOUNT_SYSTEM_PROPERTY, "false");
    }

    @BeforeEach
    void setup() throws IOException {
        client = Mockito.spy(client);
        tokenProvider = new TokenProvider(client);
        MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        headers.set(HttpHeaders.AUTHORIZATION, "abcd1234==");
        Mockito.lenient()
                .when(classPropertiesLoader.loadAsMap(Mockito.any()))
                .thenReturn(Map.of("RECORDING", "recordings", "CERTIFICATE", "deployments,pods"));
        mgr =
                new OpenShiftAuthManager(
                        env,
                        () -> NAMESPACE,
                        () -> client,
                        tokenProvider,
                        classPropertiesLoader,
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
                        "operator.cryostat.io/recordings",
                        "expected",
                        Map.of(ResourceType.RECORDING, Set.of(GroupResource.RECORDINGS))),
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
                        "operator.cryostat.io/recordings, apps/deployments",
                        "expected",
                        Map.of(
                                ResourceType.RECORDING,
                                Set.of(GroupResource.RECORDINGS, GroupResource.DEPLOYMENTS))));
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
                mgr.validateToken(() -> "token", Set.of(ResourceAction.READ_TARGET)).get(),
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
        MatcherAssert.assertThat(
                ExceptionUtils.getRootCause(ee),
                Matchers.instanceOf(PermissionDeniedException.class));
        PermissionDeniedException pde = (PermissionDeniedException) ExceptionUtils.getRootCause(ee);
        MatcherAssert.assertThat(pde.getNamespace(), Matchers.equalTo(NAMESPACE));
        MatcherAssert.assertThat(pde.getResourceType(), Matchers.equalTo("recordings"));
        MatcherAssert.assertThat(pde.getVerb(), Matchers.equalTo("get"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "Bearer ", "invalidHeader"})
    void shouldSendRedirectResponseOnEmptyOrInvalidHeaders(String headers) throws Exception {
        Mockito.when(env.getEnv(Mockito.anyString())).thenReturn(CLIENT_ID, ROLE_SCOPE);

        Mockito.when(client.adapt(OkHttpClient.class)).thenReturn(httpClient);
        Mockito.when(client.getMasterUrl()).thenReturn(new URL("https://example.com"));
        Response resp = Mockito.mock(Response.class);
        ResponseBody body = Mockito.mock(ResponseBody.class);
        Mockito.when(body.string()).thenReturn(OAUTH_METADATA);
        Mockito.when(resp.body()).thenReturn(body);
        Call call = Mockito.mock(Call.class);
        Mockito.when(httpClient.newCall(Mockito.any(Request.class))).thenReturn(call);
        Mockito.doAnswer(
                        new Answer<Void>() {
                            @Override
                            public Void answer(InvocationOnMock args) throws Throwable {
                                Callback callback = args.getArgument(0);
                                callback.onResponse(call, resp);
                                return null;
                            }
                        })
                .when(call)
                .enqueue(Mockito.any(Callback.class));

        String actualLoginRedirectUrl =
                mgr.getLoginRedirectUrl(() -> headers, ResourceAction.NONE).get();

        MatcherAssert.assertThat(
                actualLoginRedirectUrl, Matchers.equalTo(EXPECTED_LOGIN_REDIRECT_URL));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer invalidToken", "Bearer 1234"})
    void shouldSendRedirectResponseOnInvalidToken(String headers) throws Exception {
        Mockito.when(env.getEnv(Mockito.anyString()))
                .thenReturn(CLIENT_ID, ROLE_SCOPE, CLIENT_ID, ROLE_SCOPE);

        Mockito.when(client.adapt(OkHttpClient.class)).thenReturn(httpClient);
        Mockito.when(client.getMasterUrl()).thenReturn(new URL("https://example.com"));
        Response resp = Mockito.mock(Response.class);
        ResponseBody body = Mockito.mock(ResponseBody.class);
        Mockito.when(body.string()).thenReturn(OAUTH_METADATA);
        Mockito.when(resp.body()).thenReturn(body);
        Call call = Mockito.mock(Call.class);
        Mockito.when(httpClient.newCall(Mockito.any(Request.class))).thenReturn(call);
        Mockito.doAnswer(
                        new Answer<Void>() {
                            @Override
                            public Void answer(InvocationOnMock args) throws Throwable {
                                Callback callback = args.getArgument(0);
                                callback.onResponse(call, resp);
                                return null;
                            }
                        })
                .when(call)
                .enqueue(Mockito.any(Callback.class));

        String actualLoginRedirectUrl =
                mgr.getLoginRedirectUrl(() -> headers, ResourceAction.NONE).get();

        MatcherAssert.assertThat(
                actualLoginRedirectUrl, Matchers.equalTo(EXPECTED_LOGIN_REDIRECT_URL));
    }

    @ParameterizedTest
    @CsvSource(value = {",", CLIENT_ID + ",", "," + ROLE_SCOPE})
    void shouldThrowWhenEnvironmentVariablesMissing(String clientId, String tokenScope)
            throws Exception {
        Mockito.when(env.getEnv(Mockito.anyString())).thenReturn(clientId, tokenScope);

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
        Mockito.when(env.getEnv(Mockito.anyString()))
                .thenReturn(CLIENT_ID, ROLE_SCOPE, CLIENT_ID, ROLE_SCOPE);

        Mockito.when(client.adapt(OkHttpClient.class)).thenReturn(httpClient);
        Mockito.when(client.getMasterUrl()).thenReturn(new URL("https://example.com"));
        ArgumentCaptor<Request> reqCaptor = ArgumentCaptor.forClass(Request.class);
        Response resp = Mockito.mock(Response.class);
        ResponseBody body = Mockito.mock(ResponseBody.class);
        Mockito.when(body.string()).thenReturn(OAUTH_METADATA);
        Mockito.when(resp.body()).thenReturn(body);
        Call call = Mockito.mock(Call.class);
        Mockito.when(httpClient.newCall(reqCaptor.capture())).thenReturn(call);
        Mockito.doAnswer(
                        new Answer<Void>() {
                            @Override
                            public Void answer(InvocationOnMock args) throws Throwable {
                                Callback callback = args.getArgument(0);
                                callback.onResponse(call, resp);
                                return null;
                            }
                        })
                .when(call)
                .enqueue(Mockito.any(Callback.class));

        String firstRedirectUrl =
                mgr.getLoginRedirectUrl(() -> "Bearer", ResourceAction.NONE).get();

        MatcherAssert.assertThat(firstRedirectUrl, Matchers.equalTo(EXPECTED_LOGIN_REDIRECT_URL));

        String secondRedirectUrl =
                mgr.getLoginRedirectUrl(() -> "Bearer", ResourceAction.NONE).get();
        MatcherAssert.assertThat(secondRedirectUrl, Matchers.equalTo(EXPECTED_LOGIN_REDIRECT_URL));

        Mockito.verify(httpClient, Mockito.atMostOnce()).newCall(Mockito.any(Request.class));
        Mockito.verify(call, Mockito.atMostOnce()).enqueue(Mockito.any(Callback.class));

        Mockito.verify(body).close();
        Request req = reqCaptor.getValue();
        MatcherAssert.assertThat(
                req.url(),
                Matchers.equalTo(
                        HttpUrl.parse(
                                "https://example.com/.well-known/oauth-authorization-server")));
        MatcherAssert.assertThat(req.header("accept"), Matchers.equalTo("application/json"));
    }

    @Test
    void shouldReturnLogoutRedirectUrl() throws Exception {
        Resource<OAuthAccessToken> token = Mockito.mock(Resource.class);
        NonNamespaceOperation<OAuthAccessToken, OAuthAccessTokenList, Resource<OAuthAccessToken>>
                tokens = Mockito.mock(NonNamespaceOperation.class);

        Mockito.when(client.oAuthAccessTokens()).thenReturn(tokens);
        Mockito.when(tokens.withName(Mockito.anyString())).thenReturn(token);
        Mockito.when(token.delete()).thenReturn(true);

        Mockito.when(client.adapt(OkHttpClient.class)).thenReturn(httpClient);
        Mockito.when(client.getMasterUrl()).thenReturn(new URL("https://example.com"));
        Response resp = Mockito.mock(Response.class);
        ResponseBody body = Mockito.mock(ResponseBody.class);
        Mockito.when(body.string()).thenReturn(OAUTH_METADATA);
        Mockito.when(resp.body()).thenReturn(body);
        Call call = Mockito.mock(Call.class);
        Mockito.when(httpClient.newCall(Mockito.any(Request.class))).thenReturn(call);
        Mockito.doAnswer(
                        new Answer<Void>() {
                            @Override
                            public Void answer(InvocationOnMock args) throws Throwable {
                                Callback callback = args.getArgument(0);
                                callback.onResponse(call, resp);
                                return null;
                            }
                        })
                .when(call)
                .enqueue(Mockito.any(Callback.class));

        String logoutRedirectUrl = mgr.logout(() -> "Bearer myToken").get();

        MatcherAssert.assertThat(logoutRedirectUrl, Matchers.equalTo(EXPECTED_LOGOUT_REDIRECT_URL));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(booleans = {false})
    void shouldThrowWhenTokenDeletionFailsOnLogout(Boolean deletionFailure) throws Exception {
        Resource<OAuthAccessToken> token = Mockito.mock(Resource.class);
        NonNamespaceOperation<OAuthAccessToken, OAuthAccessTokenList, Resource<OAuthAccessToken>>
                tokens = Mockito.mock(NonNamespaceOperation.class);

        Mockito.when(client.oAuthAccessTokens()).thenReturn(tokens);
        Mockito.when(tokens.withName(Mockito.anyString())).thenReturn(token);
        Mockito.when(token.delete()).thenReturn(deletionFailure);

        Assertions.assertThrows(
                TokenNotFoundException.class, () -> mgr.logout(() -> "Bearer myToken").get());
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
        if (resourceAction.getResource() == ResourceType.RECORDING) {
            expectedGroups = Set.of("operator.cryostat.io");
            expectedResources = Set.of("recordings");
        } else if (resourceAction.getResource() == ResourceType.CERTIFICATE) {
            expectedGroups = Set.of("apps", "", "operator.cryostat.io");
            expectedResources = Set.of("deployments", "pods");
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
        RecordedRequest req = server.takeRequest();
        while (true) {
            if (++requestCount > maxDroppedRequests) {
                throw new IllegalStateException();
            }
            String path = req.getPath();
            if (SUBJECT_REVIEW_API_PATH.equals(path)) {
                break;
            }
            req = server.takeRequest();
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
        actualGroups.add(body.getSpec().getResourceAttributes().getGroup());
        actualResources.add(body.getSpec().getResourceAttributes().getResource());
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
        }

        MatcherAssert.assertThat(actualGroups, Matchers.equalTo(expectedGroups));
        MatcherAssert.assertThat(actualResources, Matchers.equalTo(expectedResources));
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
