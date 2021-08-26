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
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Scheduler;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;

import dagger.Lazy;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Environment;
import io.cryostat.net.AbstractAuthManager;
import io.cryostat.net.AuthenticationScheme;
import io.cryostat.net.AuthorizationErrorException;
import io.cryostat.net.MissingEnvironmentVariableException;
import io.cryostat.net.PermissionDeniedException;
import io.cryostat.net.TokenNotFoundException;
import io.cryostat.net.UserInfo;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.ResourceType;
import io.cryostat.net.security.ResourceVerb;
import io.cryostat.util.resource.ClassPropertiesLoader;
import io.fabric8.kubernetes.api.model.authentication.TokenReview;
import io.fabric8.kubernetes.api.model.authentication.TokenReviewBuilder;
import io.fabric8.kubernetes.api.model.authentication.TokenReviewStatus;
import io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectAccessReview;
import io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectAccessReviewBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.client.OpenShiftClient;
import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class OpenShiftAuthManager extends AbstractAuthManager {

    private static final Set<GroupResource> PERMISSION_NOT_REQUIRED =
            Set.of(GroupResource.PERMISSION_NOT_REQUIRED);
    private static final String WELL_KNOWN_PATH = ".well-known";
    private static final String OAUTH_SERVER_PATH = "oauth-authorization-server";
    private static final String AUTHORIZATION_URL_KEY = "authorization_endpoint";
    private static final String LOGOUT_URL_KEY = "logout";
    private static final String OAUTH_METADATA_KEY = "oauth_metadata";
    private static final String CRYOSTAT_OAUTH_CLIENT_ID = "CRYOSTAT_OAUTH_CLIENT_ID";
    private static final String CRYOSTAT_OAUTH_ROLE = "CRYOSTAT_OAUTH_ROLE";

    private final Environment env;
    private final Lazy<String> namespace;
    private final Lazy<OpenShiftClient> serviceAccountClient;
    private final ConcurrentHashMap<String, CompletableFuture<String>> oauthUrls;
    private final ConcurrentHashMap<String, CompletableFuture<OAuthMetadata>> oauthMetadata;
    private final Map<ResourceType, Set<GroupResource>> resourceMap;

    private final LoadingCache<String, OpenShiftClient> userClients;

    OpenShiftAuthManager(
            Environment env,
            Lazy<String> namespace,
            Lazy<OpenShiftClient> serviceAccountClient,
            Function<String, OpenShiftClient> clientProvider,
            ClassPropertiesLoader classPropertiesLoader,
            Executor cacheExecutor,
            Scheduler cacheScheduler,
            Logger logger) {
        super(logger);
        this.env = env;
        this.namespace = namespace;
        this.serviceAccountClient = serviceAccountClient;
        this.oauthUrls = new ConcurrentHashMap<>(2);
        this.oauthMetadata = new ConcurrentHashMap<>(1);

        Caffeine<String, OpenShiftClient> cacheBuilder =
                Caffeine.newBuilder()
                        .executor(cacheExecutor)
                        .scheduler(cacheScheduler)
                        .expireAfterAccess(Duration.ofMinutes(5)) // should this be configurable?
                        .removalListener((k, v, cause) -> v.close());
        this.userClients = cacheBuilder.build(clientProvider::apply);

        this.resourceMap = new HashMap<>();
        try {
            Properties props = classPropertiesLoader.loadProperties(getClass());
            props.entrySet()
                .forEach(entry -> {
                    try {
                        String key = (String) entry.getKey();
                        Set<GroupResource> values = Arrays.asList(((String) entry.getValue()).split(","))
                            .stream()
                            .map(String::strip)
                            .filter(StringUtils::isNotBlank)
                            .map(GroupResource::valueOf)
                            .collect(Collectors.toSet());
                        ResourceType type = ResourceType.valueOf(key);
                        resourceMap.put(type, values);
                    } catch (IllegalArgumentException iae) {
                        logger.error(iae);
                    }
                });
        } catch (IOException ioe) {
            logger.error(ioe);
        }
    }

    @Override
    public AuthenticationScheme getScheme() {
        return AuthenticationScheme.BEARER;
    }

    @Override
    public Future<UserInfo> getUserInfo(Supplier<String> httpHeaderProvider) {
        String token = getTokenFromHttpHeader(httpHeaderProvider.get());
        Future<TokenReviewStatus> fStatus = performTokenReview(token);
        try {
            TokenReviewStatus status = fStatus.get();
            if (!Boolean.TRUE.equals(status.getAuthenticated())) {
                return CompletableFuture.failedFuture(
                        new AuthorizationErrorException("Authentication Failed"));
            }
            return CompletableFuture.completedFuture(new UserInfo(status.getUser().getUsername()));
        } catch (ExecutionException ee) {
            return CompletableFuture.failedFuture(ee.getCause());
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public Optional<String> getLoginRedirectUrl(
            Supplier<String> headerProvider, Set<ResourceAction> resourceActions)
            throws ExecutionException, InterruptedException {
        Boolean hasValidHeader = false;
        try {
            hasValidHeader = this.validateHttpHeader(headerProvider, resourceActions).get();

            if (Boolean.TRUE.equals(hasValidHeader)) {
                return Optional.empty();
            }
            return Optional.of(this.computeAuthorizationEndpoint().get());
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause();
            if (cause instanceof PermissionDeniedException
                    || cause instanceof AuthorizationErrorException
                    || cause instanceof KubernetesClientException) {
                return Optional.of(this.computeAuthorizationEndpoint().get());
            }
            throw ee;
        }
    }

    @Override
    public Optional<String> logout(Supplier<String> httpHeaderProvider)
            throws ExecutionException, InterruptedException, TokenNotFoundException {

        String token = getTokenFromHttpHeader(httpHeaderProvider.get());
        deleteToken(token);

        return Optional.of(this.computeLogoutRedirectEndpoint().get());
    }

    @Override
    public Future<Boolean> validateToken(
            Supplier<String> tokenProvider, Set<ResourceAction> resourceActions) {
        String token = tokenProvider.get();
        if (StringUtils.isBlank(token)) {
            return CompletableFuture.completedFuture(false);
        }
        if (resourceActions.isEmpty()) {
            return reviewToken(token);
        }

        OpenShiftClient client = userClients.get(token);
        try {
            List<CompletableFuture<Void>> results =
                    resourceActions
                            .parallelStream()
                            .flatMap(
                                    resourceAction ->
                                            validateAction(client, namespace.get(), resourceAction))
                            .collect(Collectors.toList());

            CompletableFuture.allOf(results.toArray(new CompletableFuture[0]))
                    .get(15, TimeUnit.SECONDS);
            // if we get here then all requests were successful and granted, otherwise an exception
            // was thrown on allOf().get() above
            return CompletableFuture.completedFuture(true);
        } catch (KubernetesClientException | ExecutionException e) {
            userClients.invalidate(token);
            logger.info(e);
            return CompletableFuture.failedFuture(e);
        } catch (Exception e) {
            userClients.invalidate(token);
            logger.error(e);
            return CompletableFuture.failedFuture(e);
        }
    }

    Future<Boolean> reviewToken(String token) {
        Future<TokenReviewStatus> fStatus = performTokenReview(token);
        try {
            TokenReviewStatus status = fStatus.get();
            Boolean authenticated = status.getAuthenticated();
            return CompletableFuture.completedFuture(authenticated != null && authenticated);
        } catch (ExecutionException ee) {
            return CompletableFuture.failedFuture(ee.getCause());
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private Stream<CompletableFuture<Void>> validateAction(
            OpenShiftClient client, String namespace, ResourceAction resourceAction) {
        Set<GroupResource> resources = resourceMap.getOrDefault(resourceAction.getResource(),
                PERMISSION_NOT_REQUIRED);
        if (PERMISSION_NOT_REQUIRED.equals(resources) || resources.isEmpty()) {
            return Stream.of(CompletableFuture.completedFuture(null));
        }
        String verb = map(resourceAction.getVerb());
        return resources
                .parallelStream()
                .map(
                        resource -> {
                            AuthRequest evt = new AuthRequest();
                            evt.begin();
                            try {
                                SelfSubjectAccessReview accessReview =
                                        new SelfSubjectAccessReviewBuilder()
                                                .withNewSpec()
                                                .withNewResourceAttributes()
                                                .withNamespace(namespace)
                                                .withGroup(resource.getGroup())
                                                .withResource(resource.getResource())
                                                .withVerb(verb)
                                                .endResourceAttributes()
                                                .endSpec()
                                                .build();
                                accessReview =
                                        client.authorization()
                                                .v1()
                                                .selfSubjectAccessReview()
                                                .create(accessReview);
                                evt.setRequestSuccessful(true);
                                if (!accessReview.getStatus().getAllowed()) {
                                    return CompletableFuture.failedFuture(
                                            new PermissionDeniedException(
                                                    namespace,
                                                    resource.getGroup(),
                                                    resource.getResource(),
                                                    verb,
                                                    accessReview.getStatus().getReason()));
                                } else {
                                    return CompletableFuture.completedFuture(null);
                                }
                            } catch (Exception e) {
                                return CompletableFuture.failedFuture(e);
                            } finally {
                                if (evt.shouldCommit()) {
                                    evt.end();
                                    evt.commit();
                                }
                            }
                        });
    }

    @Override
    public Future<Boolean> validateHttpHeader(
            Supplier<String> headerProvider, Set<ResourceAction> resourceActions) {
        String authorization = headerProvider.get();
        String token = getTokenFromHttpHeader(authorization);
        if (token == null) {
            return CompletableFuture.completedFuture(false);
        }
        return validateToken(() -> token, resourceActions);
    }

    @Override
    public Future<Boolean> validateWebSocketSubProtocol(
            Supplier<String> subProtocolProvider, Set<ResourceAction> resourceActions) {
        String subprotocol = subProtocolProvider.get();
        if (StringUtils.isBlank(subprotocol)) {
            return CompletableFuture.completedFuture(false);
        }
        Pattern pattern =
                Pattern.compile(
                        "base64url\\.bearer\\.authorization\\.cryostat\\.([\\S]+)",
                        Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(subprotocol);
        if (!matcher.matches()) {
            return CompletableFuture.completedFuture(false);
        }
        String b64 = matcher.group(1);
        try {
            String decoded =
                    new String(Base64.getUrlDecoder().decode(b64), StandardCharsets.UTF_8).trim();
            return validateToken(() -> decoded, resourceActions);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(false);
        }
    }

    private boolean deleteToken(String token) throws TokenNotFoundException {
        Boolean deleted =
                Optional.ofNullable(
                                serviceAccountClient
                                        .get()
                                        .oAuthAccessTokens()
                                        .withName(this.getOauthAccessTokenName(token))
                                        .delete())
                        .orElseThrow(TokenNotFoundException::new);

        if (Boolean.FALSE.equals(deleted)) {
            throw new TokenNotFoundException();
        }

        return deleted;
    }

    private String getTokenFromHttpHeader(String rawHttpHeader) {
        if (StringUtils.isBlank(rawHttpHeader)) {
            return null;
        }
        Pattern bearerPattern = Pattern.compile("Bearer[\\s]+(.*)");
        Matcher matcher = bearerPattern.matcher(rawHttpHeader);
        if (!matcher.matches()) {
            return null;
        }
        String b64 = matcher.group(1);
        try {
            return new String(Base64.getUrlDecoder().decode(b64), StandardCharsets.UTF_8).trim();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Future<TokenReviewStatus> performTokenReview(String token) {
        try {
            TokenReview review =
                    new TokenReviewBuilder().withNewSpec().withToken(token).endSpec().build();
            review = serviceAccountClient.get().tokenReviews().create(review);
            TokenReviewStatus status = review.getStatus();
            if (StringUtils.isNotBlank(status.getError())) {
                return CompletableFuture.failedFuture(
                        new AuthorizationErrorException(status.getError()));
            }
            return CompletableFuture.completedFuture(status);
        } catch (KubernetesClientException e) {
            logger.info(e);
            return CompletableFuture.failedFuture(e);
        } catch (Exception e) {
            logger.error(e);
            return CompletableFuture.failedFuture(e);
        }
    }

    private CompletableFuture<String> computeAuthorizationEndpoint() {

        return oauthUrls.computeIfAbsent(
                AUTHORIZATION_URL_KEY,
                key -> {
                    try {
                        String oauthClient = this.getServiceAccountName();
                        String tokenScope = this.getTokenScope();

                        CompletableFuture<OAuthMetadata> oauthMetadata =
                                this.computeOauthMetadata();

                        String authorizeEndpoint = oauthMetadata.get().getAuthorizationEndpoint();

                        URIBuilder builder = new URIBuilder(authorizeEndpoint);
                        builder.addParameter("client_id", oauthClient);
                        builder.addParameter("response_type", "token");
                        builder.addParameter("response_mode", "fragment");
                        builder.addParameter("scope", tokenScope);

                        return CompletableFuture.completedFuture(builder.build().toString());
                    } catch (ExecutionException
                            | InterruptedException
                            | URISyntaxException
                            | MissingEnvironmentVariableException e) {
                        return CompletableFuture.failedFuture(e);
                    }
                });
    }

    private CompletableFuture<String> computeLogoutRedirectEndpoint() {
        return oauthUrls.computeIfAbsent(
                LOGOUT_URL_KEY,
                key -> {
                    try {
                        CompletableFuture<OAuthMetadata> oauthMetadata =
                                this.computeOauthMetadata();
                        String baseUrl = oauthMetadata.get().getBaseUrl();

                        return CompletableFuture.completedFuture(
                                String.format("%s/logout", baseUrl));
                    } catch (ExecutionException | InterruptedException e) {
                        return CompletableFuture.failedFuture(e);
                    }
                });
    }

    private CompletableFuture<OAuthMetadata> computeOauthMetadata() {
        return oauthMetadata.computeIfAbsent(OAUTH_METADATA_KEY, key -> queryOAuthServer());
    }

    private CompletableFuture<OAuthMetadata> queryOAuthServer() {
        CompletableFuture<OAuthMetadata> oauthMetadata = new CompletableFuture<>();
        try {
            OpenShiftClient client = serviceAccountClient.get();
            OkHttpClient httpClient = client.adapt(OkHttpClient.class);
            HttpUrl url =
                    HttpUrl.get(client.getMasterUrl())
                            .newBuilder()
                            .addPathSegment(WELL_KNOWN_PATH)
                            .addPathSegment(OAUTH_SERVER_PATH)
                            .build();
            Request req =
                    new Request.Builder().url(url).addHeader("Accept", "application/json").build();
            httpClient
                    .newCall(req)
                    .enqueue(
                            new Callback() {

                                @Override
                                public void onFailure(Call call, IOException e) {
                                    oauthMetadata.completeExceptionally(e);
                                }

                                @Override
                                public void onResponse(Call call, Response response)
                                        throws IOException {
                                    try (ResponseBody body = response.body()) {
                                        ObjectMapper objectMapper = new ObjectMapper();
                                        OAuthMetadata entity =
                                                objectMapper.readValue(
                                                        response.body().string(),
                                                        OAuthMetadata.class);
                                        oauthMetadata.complete(entity);
                                    }
                                }
                            });
            return CompletableFuture.completedFuture(oauthMetadata.get());
        } catch (ExecutionException | InterruptedException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private String getServiceAccountName() throws MissingEnvironmentVariableException {
        Optional<String> clientId = Optional.ofNullable(env.getEnv(CRYOSTAT_OAUTH_CLIENT_ID));
        return String.format(
                "system:serviceaccount:%s:%s",
                namespace.get(),
                clientId.orElseThrow(
                        () -> new MissingEnvironmentVariableException(CRYOSTAT_OAUTH_CLIENT_ID)));
    }

    private String getTokenScope() throws MissingEnvironmentVariableException {
        Optional<String> tokenScope = Optional.ofNullable(env.getEnv(CRYOSTAT_OAUTH_ROLE));
        return String.format(
                "user:check-access role:%s:%s",
                tokenScope.orElseThrow(
                        () -> new MissingEnvironmentVariableException(CRYOSTAT_OAUTH_ROLE)),
                namespace.get());
    }

    private String getOauthAccessTokenName(String token) {
        String sha256Prefix = "sha256~";
        String rawToken = StringUtils.removeStart(token, sha256Prefix);
        byte[] checksum = DigestUtils.sha256(rawToken);
        String encodedTokenHash =
                new String(Base64.getUrlEncoder().encode(checksum), StandardCharsets.UTF_8).trim();

        return sha256Prefix + StringUtils.removeEnd(encodedTokenHash, "=");
    }

    private static String map(ResourceVerb verb) {
        switch (verb) {
            case CREATE:
                return "create";
            case READ:
                return "get";
            case UPDATE:
                return "patch";
            case DELETE:
                return "delete";
            default:
                throw new IllegalArgumentException(
                        String.format("Unknown resource verb \"%s\"", verb));
        }
    }

    @Name("io.cryostat.net.OpenShiftAuthManager.AuthRequest")
    @Label("AuthRequest")
    @Category("Cryostat")
    @SuppressFBWarnings(
            value = "URF_UNREAD_FIELD",
            justification = "Event fields are recorded with JFR instead of accessed directly")
    public static class AuthRequest extends Event {

        boolean requestSuccessful;

        public AuthRequest() {
            this.requestSuccessful = false;
        }

        public void setRequestSuccessful(boolean requestSuccessful) {
            this.requestSuccessful = requestSuccessful;
        }
    }

    // A pairing of a Kubernetes group name and resource name
    private static enum GroupResource {
        DEPLOYMENTS("apps", "deployments"),
        PODS("", "pods"),
        RECORDINGS("operator.cryostat.io", "recordings"),
        PERMISSION_NOT_REQUIRED("", "PERMISSION_NOT_REQUIRED"),
        ;

        private final String group;
        private final String resource;

        private GroupResource(String group, String resource) {
            this.group = group;
            this.resource = resource;
        }

        public String getGroup() {
            return group;
        }

        public String getResource() {
            return resource;
        }
    }

    // Holder for deserialized response from OAuth server. Ignores unneeded properties.
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class OAuthMetadata {
        private String baseUrl;
        private String authorizationEndpoint;

        @JsonProperty("issuer")
        public String getBaseUrl() {
            return baseUrl;
        }

        @JsonProperty("authorization_endpoint")
        public String getAuthorizationEndpoint() {
            return authorizationEndpoint;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public void setAuthorizationEndpoint(String authorizationEndpoint) {
            this.authorizationEndpoint = authorizationEndpoint;
        }
    }
}
