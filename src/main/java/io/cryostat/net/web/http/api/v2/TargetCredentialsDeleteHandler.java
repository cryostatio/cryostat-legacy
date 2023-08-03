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

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.DeprecatedApi;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.rules.MatchExpressionValidationException;

import com.google.gson.Gson;
import io.vertx.core.http.HttpMethod;

@DeprecatedApi(
        deprecated = @Deprecated(forRemoval = false),
        alternateLocation = "/api/v2.2/credentials/:id")
class TargetCredentialsDeleteHandler extends AbstractV2RequestHandler<Void> {

    static final String PATH = TargetCredentialsPostHandler.PATH;

    private final CredentialsManager credentialsManager;
    private final NotificationFactory notificationFactory;

    @Inject
    TargetCredentialsDeleteHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            NotificationFactory notificationFactory,
            Gson gson) {
        super(auth, credentialsManager, gson);
        this.credentialsManager = credentialsManager;
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
        return HttpMethod.DELETE;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(ResourceAction.DELETE_CREDENTIALS);
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
        try {
            int removed = this.credentialsManager.removeCredentials(targetId);
            if (removed < 0) {
                return new IntermediateResponse<Void>().statusCode(404);
            }

            notificationFactory
                    .createBuilder()
                    .metaCategory("TargetCredentialsDeleted")
                    .metaType(HttpMimeType.JSON)
                    .message(Map.of("target", targetId))
                    .build()
                    .send();

            return new IntermediateResponse<Void>().statusCode(200);
        } catch (MatchExpressionValidationException e) {
            throw new ApiException(500, e);
        }
    }
}
