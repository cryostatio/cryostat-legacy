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
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Environment;
import io.cryostat.net.AbstractAuthManager;
import io.cryostat.net.AuthenticationErrorException;
import io.cryostat.net.AuthenticationScheme;
import io.cryostat.net.MissingEnvironmentVariableException;
import io.cryostat.net.PermissionDeniedException;
import io.cryostat.net.TokenNotFoundException;
import io.cryostat.net.UserInfo;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.ResourceType;
import io.cryostat.net.security.ResourceVerb;
import io.cryostat.util.resource.ClassPropertiesLoader;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Scheduler;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import dagger.Lazy;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.fabric8.kubernetes.api.model.StatusDetails;
import io.fabric8.kubernetes.api.model.authentication.TokenReview;
import io.fabric8.kubernetes.api.model.authentication.TokenReviewBuilder;
import io.fabric8.kubernetes.api.model.authentication.TokenReviewStatus;
import io.fabric8.kubernetes.api.model.authorization.v1.ResourceAttributes;
import io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectAccessReview;
import io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectAccessReviewBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.http.HttpClient;
import io.fabric8.kubernetes.client.http.HttpRequest;
import io.fabric8.openshift.client.OpenShiftClient;
import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;

public class OpenShiftAuthManager extends AbstractAuthManager {

    private static final String WELL_KNOWN_PATH = ".well-known";
    private static final String OAUTH_SERVER_PATH = "oauth-authorization-server";
    private static final String AUTHORIZATION_URL_KEY = "authorization_endpoint";
    private static final String LOGOUT_URL_KEY = "logout";
    private static final String OAUTH_METADATA_KEY = "oauth_metadata";
    private static final String CRYOSTAT_OAUTH_CLIENT_ID = "CRYOSTAT_OAUTH_CLIENT_ID";
    private static final String CRYOSTAT_BASE_OAUTH_ROLE = "CRYOSTAT_BASE_OAUTH_ROLE";
    private static final String CRYOSTAT_CUSTOM_OAUTH_ROLE = "CRYOSTAT_CUSTOM_OAUTH_ROLE";

    static final Pattern RESOURCE_PATTERN =
            Pattern.compile(
                    "^([\\w]+)([\\.\\w]+)?(?:/([\\w]+))?$",
                    Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    private final Environment env;
    private final Lazy<String> namespace;
    private final Lazy<OpenShiftClient> serviceAccountClient;
    private final ConcurrentHashMap<String, CompletableFuture<String>> oauthUrls;
    private final ConcurrentHashMap<String, CompletableFuture<OAuthMetadata>> oauthMetadata;
    private final Map<ResourceType, Set<GroupResource>> resourceMap;
    private final Gson gson;

    private final LoadingCache<String, OpenShiftClient> userClients;

    OpenShiftAuthManager(
            Environment env,
            Lazy<String> namespace,
            Lazy<OpenShiftClient> serviceAccountClient,
            Function<String, OpenShiftClient> clientProvider,
            ClassPropertiesLoader classPropertiesLoader,
            Gson gson,
            Executor cacheExecutor,
            Scheduler cacheScheduler,
            Logger logger) {
        super(logger);
        this.env = env;
        this.namespace = namespace;
        this.serviceAccountClient = serviceAccountClient;
        this.oauthUrls = new ConcurrentHashMap<>(2);
        this.oauthMetadata = new ConcurrentHashMap<>(1);
        this.gson = gson;

        Caffeine<String, OpenShiftClient> cacheBuilder =
                Caffeine.newBuilder()
                        .executor(cacheExecutor)
                        .scheduler(cacheScheduler)
                        .expireAfterAccess(Duration.ofMinutes(5)) // should this be configurable?
                        .removalListener((k, v, cause) -> v.close());
        this.userClients = cacheBuilder.build(clientProvider::apply);

        this.resourceMap = processResourceMapping(classPropertiesLoader, logger);
    }

    static Map<ResourceType, Set<GroupResource>> processResourceMapping(
            ClassPropertiesLoader loader, Logger logger) {
        Map<ResourceType, Set<GroupResource>> resourceMap = new HashMap<>();
        Map<String, String> props;
        try {
            props = loader.loadAsMap(OpenShiftAuthManager.class);
        } catch (IOException ioe) {
            logger.error(ioe);
            return Collections.unmodifiableMap(resourceMap);
        }
        props.entrySet()
                .forEach(
                        entry -> {
                            try {
                                ResourceType type = ResourceType.valueOf(entry.getKey());
                                Set<GroupResource> values =
                                        Arrays.asList(entry.getValue().split(",")).stream()
                                                .map(String::strip)
                                                .filter(StringUtils::isNotBlank)
                                                .map(GroupResource::fromString)
                                                .collect(Collectors.toSet());
                                resourceMap.put(type, values);
                            } catch (IllegalArgumentException iae) {
                                logger.error(iae);
                            }
                        });
        return Collections.unmodifiableMap(resourceMap);
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
                        new AuthenticationErrorException("Authentication Failed"));
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
                    || cause instanceof AuthenticationErrorException
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
                    resourceActions.stream()
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
        Set<GroupResource> resources =
                resourceMap.getOrDefault(resourceAction.getResource(), Set.of());
        if (resources.isEmpty()) {
            return Stream.of();
        }
        String verb = map(resourceAction.getVerb());
        return resources.stream()
                .map(
                        resource ->
                                new SelfSubjectAccessReviewBuilder()
                                        .withNewSpec()
                                        .withNewResourceAttributes()
                                        .withNamespace(namespace)
                                        .withGroup(resource.getGroup())
                                        .withResource(resource.getResource())
                                        .withSubresource(resource.getSubResource())
                                        .withVerb(verb)
                                        .endResourceAttributes()
                                        .endSpec()
                                        .build())
                .map(
                        accessReview -> {
                            CompletableFuture<Void> result = new CompletableFuture<>();
                            AuthRequest evt = new AuthRequest();
                            try {
                                evt.begin();
                                SelfSubjectAccessReview accessReviewResult =
                                        client.authorization()
                                                .v1()
                                                .selfSubjectAccessReview()
                                                .create(accessReview);
                                evt.setRequestSuccessful(true);
                                if (accessReviewResult.getStatus().getAllowed()) {
                                    result.complete(null);
                                } else {
                                    result.completeExceptionally(
                                            new PermissionDeniedException(
                                                    namespace,
                                                    new GroupResource(
                                                                    accessReview
                                                                            .getSpec()
                                                                            .getResourceAttributes())
                                                            .toString(),
                                                    verb,
                                                    accessReviewResult.getStatus().getReason()));
                                }
                            } catch (Exception e) {
                                result.completeExceptionally(e);
                            } finally {
                                if (evt.shouldCommit()) {
                                    evt.end();
                                    evt.commit();
                                }
                            }
                            return result;
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

    private void deleteToken(String token) throws TokenNotFoundException {
        List<StatusDetails> results =
                serviceAccountClient
                        .get()
                        .oAuthAccessTokens()
                        .withName(this.getOauthAccessTokenName(token))
                        .delete();

        List<String> causes =
                results.stream()
                        .flatMap(sd -> sd.getCauses().stream())
                        .map(
                                sc ->
                                        String.format(
                                                "[%s] %s: %s",
                                                sc.getField(), sc.getReason(), sc.getMessage()))
                        .toList();
        if (!causes.isEmpty()) {
            logger.warn(new TokenNotFoundException(causes));
        }
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
                        new AuthenticationErrorException(status.getError()));
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
                            | MissingEnvironmentVariableException
                            | IllegalArgumentException e) {
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
        try {
            OpenShiftClient client = serviceAccountClient.get();
            HttpClient httpClient = client.getHttpClient();
            HttpRequest httpRequest =
                    httpClient
                            .newHttpRequestBuilder()
                            .uri(
                                    new URIBuilder(client.getMasterUrl().toURI())
                                            .setPathSegments(WELL_KNOWN_PATH, OAUTH_SERVER_PATH)
                                            .build())
                            .header("Accept", "application/json")
                            .build();
            return httpClient
                    .sendAsync(httpRequest, String.class)
                    .thenCompose(
                            res -> {
                                try {
                                    return CompletableFuture.completedStage(
                                            gson.fromJson(res.body(), OAuthMetadata.class));
                                } catch (Exception e) {
                                    return CompletableFuture.failedStage(e);
                                }
                            });
        } catch (URISyntaxException e) {
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

    private String getTokenScope()
            throws MissingEnvironmentVariableException, IllegalArgumentException {
        Optional<String> baseOAuthRole = Optional.ofNullable(env.getEnv(CRYOSTAT_BASE_OAUTH_ROLE));
        Optional<String> customOAuthRole =
                Optional.ofNullable(env.getEnv(CRYOSTAT_CUSTOM_OAUTH_ROLE));

        String tokenScope =
                String.format(
                        "user:check-access role:%s:%s",
                        baseOAuthRole.orElseThrow(
                                () ->
                                        new MissingEnvironmentVariableException(
                                                CRYOSTAT_BASE_OAUTH_ROLE)),
                        namespace.get());

        if (customOAuthRole.isPresent()) {
            if (customOAuthRole.get().isBlank()) {
                throw new IllegalArgumentException(
                        CRYOSTAT_CUSTOM_OAUTH_ROLE + " must not be blank.");
            }
            tokenScope =
                    String.format(
                            "%s role:%s:%s", tokenScope, customOAuthRole.get(), namespace.get());
        }
        return tokenScope;
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
    public static class GroupResource {

        private final String group;
        private final String resource;
        private final String subResource;

        GroupResource(String group, String resource, String subResource) {
            this.group = nullable(group);
            this.resource = nullable(resource);
            this.subResource = nullable(subResource);
        }

        GroupResource(ResourceAttributes attrs) {
            this(attrs.getGroup(), attrs.getResource(), attrs.getSubresource());
        }

        private static String nullable(String s) {
            if (s == null) {
                return "";
            }
            return s;
        }

        public String getGroup() {
            return group;
        }

        public String getResource() {
            return resource;
        }

        public String getSubResource() {
            return subResource;
        }

        @Override
        public String toString() {
            String r = resource;
            if (StringUtils.isNotBlank(group)) {
                r += "." + group;
            }
            if (StringUtils.isNotBlank(subResource)) {
                r += "/" + subResource;
            }
            return r;
        }

        @Override
        public int hashCode() {
            return Objects.hash(group, resource, subResource);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            GroupResource other = (GroupResource) obj;
            return Objects.equals(group, other.group)
                    && Objects.equals(resource, other.resource)
                    && Objects.equals(subResource, other.subResource);
        }

        public static GroupResource fromString(String raw) {
            Matcher m = RESOURCE_PATTERN.matcher(raw);
            if (!m.matches()) {
                throw new IllegalArgumentException(raw);
            }
            String group = m.group(2);
            if (group != null) {
                // substring(1) to remove the first character, which will be the '.' delimeter due
                // to
                // how the regex is structured
                group = group.substring(1);
            }
            String resource = m.group(1);
            String subResource = m.group(3);
            return new GroupResource(group, resource, subResource);
        }
    }

    // Holder for deserialized response from OAuth server.
    static class OAuthMetadata {
        private @SerializedName("issuer") String baseUrl;
        private @SerializedName("authorization_endpoint") String authorizationEndpoint;

        public String getBaseUrl() {
            return baseUrl;
        }

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
