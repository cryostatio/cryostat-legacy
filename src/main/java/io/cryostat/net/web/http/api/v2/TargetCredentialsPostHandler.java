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

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.persistence.RollbackException;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.Credentials;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.DeprecatedApi;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.rules.MatchExpressionValidationException;

import com.google.gson.Gson;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hibernate.exception.ConstraintViolationException;

@DeprecatedApi(
        deprecated = @Deprecated(forRemoval = false),
        alternateLocation = "/api/v2.2/credentials")
class TargetCredentialsPostHandler extends AbstractV2RequestHandler<Void> {

    static final String PATH = "targets/:targetId/credentials";

    private final CredentialsManager credentialsManager;
    private final NotificationFactory notificationFactory;
    private final Logger logger;

    @Inject
    TargetCredentialsPostHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            NotificationFactory notificationFactory,
            Gson gson,
            Logger logger) {
        super(auth, credentialsManager, gson);
        this.credentialsManager = credentialsManager;
        this.notificationFactory = notificationFactory;
        this.logger = logger;
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
        return EnumSet.of(ResourceAction.CREATE_CREDENTIALS);
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
    public IntermediateResponse<Void> handle(RequestParameters params) throws ApiException {
        String targetId =
                CredentialsManager.targetIdToMatchExpression(
                        params.getPathParams().get("targetId"));
        String username = params.getFormAttributes().get("username");
        String password = params.getFormAttributes().get("password");

        if (StringUtils.isAnyBlank(username, password)) {
            StringBuilder sb = new StringBuilder();
            if (StringUtils.isBlank(username)) {
                sb.append("\"username\" is required.");
            }
            if (StringUtils.isBlank(password)) {
                sb.append(" \"password\" is required.");
            }

            throw new ApiException(400, sb.toString().trim());
        }

        try {
            this.credentialsManager.addCredentials(targetId, new Credentials(username, password));

            notificationFactory
                    .createBuilder()
                    .metaCategory("TargetCredentialsStored")
                    .metaType(HttpMimeType.JSON)
                    .message(Map.of("target", targetId))
                    .build()
                    .send();

        } catch (RollbackException e) {
            if (ExceptionUtils.indexOfType(e, ConstraintViolationException.class) >= 0) {
                throw new ApiException(400, "Duplicate targetId", e);
            }
        } catch (MatchExpressionValidationException e) {
            throw new ApiException(400, e);
        }

        return new IntermediateResponse<Void>().body(null);
    }
}
