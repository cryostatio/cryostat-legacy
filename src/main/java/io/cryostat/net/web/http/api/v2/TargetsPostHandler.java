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

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.Credentials;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.ServiceRef.AnnotationKey;
import io.cryostat.platform.internal.CustomTargetPlatformClient;
import io.cryostat.recordings.JvmIdHelper;
import io.cryostat.recordings.JvmIdHelper.JvmIdGetException;
import io.cryostat.rules.MatchExpressionValidationException;
import io.cryostat.util.URIUtil;

import com.google.gson.Gson;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;

class TargetsPostHandler extends AbstractV2RequestHandler<ServiceRef> {

    static final String PATH = "targets";

    private final DiscoveryStorage storage;
    private final JvmIdHelper jvmIdHelper;
    private final CustomTargetPlatformClient customTargetPlatformClient;
    private final Logger logger;
    private final NotificationFactory notificationFactory;

    @Inject
    TargetsPostHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            Gson gson,
            DiscoveryStorage storage,
            JvmIdHelper jvmIdHelper,
            CustomTargetPlatformClient customTargetPlatformClient,
            NotificationFactory notificationFactory,
            Logger logger) {
        super(auth, credentialsManager, gson);
        this.storage = storage;
        this.jvmIdHelper = jvmIdHelper;
        this.customTargetPlatformClient = customTargetPlatformClient;
        this.logger = logger;
        this.notificationFactory = notificationFactory;
    }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.V2;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.POST;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(ResourceAction.CREATE_TARGET);
    }

    @Override
    public String path() {
        return basePath() + PATH;
    }

    @Override
    public List<HttpMimeType> produces() {
        return List.of(HttpMimeType.JSON);
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public boolean isOrdered() {
        return true;
    }

    @Override
    public List<HttpMimeType> consumes() {
        return List.of(HttpMimeType.MULTIPART_FORM, HttpMimeType.URLENCODED_FORM);
    }

    @Override
    public IntermediateResponse<ServiceRef> handle(RequestParameters params) throws ApiException {
        try {
            MultiMap attrs = params.getFormAttributes();
            String connectUrl = attrs.get("connectUrl");
            if (StringUtils.isBlank(connectUrl)) {
                throw new ApiException(400, "\"connectUrl\" form parameter must be provided");
            }
            String alias = attrs.get("alias");
            if (StringUtils.isBlank(alias)) {
                throw new ApiException(400, "\"alias\" form parameter must be provided");
            }
            URI uri = URIUtil.createAbsolute(connectUrl);
            for (ServiceRef serviceRef : storage.listDiscoverableServices()) {
                if (Objects.equals(uri, serviceRef.getServiceUri())) {
                    throw new ApiException(400, "Duplicate connectUrl");
                }
            }

            MultiMap queries = params.getQueryParams();
            boolean dryRun =
                    StringUtils.isNotBlank(queries.get("dryrun"))
                            && Boolean.valueOf(queries.get("dryrun"));
            boolean storeCredentials =
                    StringUtils.isNotBlank(queries.get("storeCredentials"))
                            && Boolean.valueOf(queries.get("storeCredentials"));

            String username = attrs.get("username");
            String password = attrs.get("password");
            Optional<Credentials> credentials =
                    StringUtils.isBlank(username) || StringUtils.isBlank(password)
                            ? Optional.empty()
                            : Optional.of(new Credentials(username, password));

            if (storeCredentials && credentials.isPresent()) {
                String matchExpression = CredentialsManager.targetIdToMatchExpression(connectUrl);
                int id = credentialsManager.addCredentials(matchExpression, credentials.get());
                notificationFactory
                        .createBuilder()
                        .metaCategory("CredentialsStored")
                        .metaType(HttpMimeType.JSON)
                        .message(
                                Map.of(
                                        "id",
                                        id,
                                        "matchExpression",
                                        matchExpression,
                                        "numMatchingTargets",
                                        1))
                        .build()
                        .send();
            }

            String jvmId = jvmIdHelper.getJvmId(uri.toString(), false, credentials);
            ServiceRef serviceRef = new ServiceRef(jvmId, uri, alias);

            Map<AnnotationKey, String> cryostatAnnotations = new HashMap<>();
            for (AnnotationKey ak : AnnotationKey.values()) {
                // TODO is there a good way to determine this prefix from the structure of the
                // ServiceRef's serialized form?
                String formKey = "annotations.cryostat." + ak.name();
                if (attrs.contains(formKey)) {
                    cryostatAnnotations.put(ak, attrs.get(formKey));
                }
            }
            cryostatAnnotations.put(AnnotationKey.REALM, CustomTargetPlatformClient.REALM);
            serviceRef.setCryostatAnnotations(cryostatAnnotations);

            if (!dryRun) {
                boolean v = customTargetPlatformClient.addTarget(serviceRef);
                if (!v) {
                    throw new ApiException(400, "Duplicate connectUrl");
                }
            } else {
                if (storage.contains(serviceRef)) {
                    return new IntermediateResponse<ServiceRef>().statusCode(202).body(serviceRef);
                }
            }

            return new IntermediateResponse<ServiceRef>().body(serviceRef);
        } catch (JvmIdGetException e) {
            if (AbstractAuthenticatedRequestHandler.isJmxAuthFailure(e)) {
                throw new ApiException(427, "JMX Authentication Failure", e);
            }
            if (AbstractAuthenticatedRequestHandler.isUnknownTargetFailure(e)) {
                throw new ApiException(404, "Target Not Found", e);
            }
            if (AbstractAuthenticatedRequestHandler.isJmxSslFailure(e)) {
                throw new ApiException(502, "Target SSL Untrusted", e);
            }
            if (AbstractAuthenticatedRequestHandler.isServiceTypeFailure(e)) {
                throw new ApiException(504, "Non-JMX Port", e);
            }
            throw new ApiException(500, "Internal Error", e);
        } catch (URISyntaxException use) {
            throw new ApiException(400, "Invalid connectUrl", use);
        } catch (IOException ioe) {
            throw new ApiException(500, "Internal Error", ioe);
        } catch (MatchExpressionValidationException e) {
            throw new ApiException(400, e);
        }
    }
}
