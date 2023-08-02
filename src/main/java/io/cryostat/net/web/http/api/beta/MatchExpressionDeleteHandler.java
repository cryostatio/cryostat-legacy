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
package io.cryostat.net.web.http.api.beta;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.net.web.http.api.v2.AbstractV2RequestHandler;
import io.cryostat.net.web.http.api.v2.ApiException;
import io.cryostat.net.web.http.api.v2.IntermediateResponse;
import io.cryostat.net.web.http.api.v2.RequestParameters;
import io.cryostat.rules.MatchExpression;
import io.cryostat.rules.MatchExpressionManager;

import com.google.gson.Gson;
import io.vertx.core.http.HttpMethod;

public class MatchExpressionDeleteHandler extends AbstractV2RequestHandler<Void> {

    private final MatchExpressionManager expressionManager;
    private final NotificationFactory notificationFactory;

    @Inject
    MatchExpressionDeleteHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            MatchExpressionManager matchExpressionManager,
            NotificationFactory notificationFactory,
            Gson gson) {
        super(auth, credentialsManager, gson);
        this.expressionManager = matchExpressionManager;
        this.notificationFactory = notificationFactory;
    }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.BETA;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.DELETE;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(ResourceAction.DELETE_MATCH_EXPRESSION);
    }

    @Override
    public String path() {
        return basePath() + MatchExpressionGetHandler.PATH;
    }

    @Override
    public List<HttpMimeType> produces() {
        return List.of(HttpMimeType.JSON);
    }

    @Override
    public IntermediateResponse<Void> handle(RequestParameters params) throws ApiException {
        int id = Integer.parseInt(params.getPathParams().get("id"));
        Optional<MatchExpression> matchExpression = expressionManager.get(id);
        if (matchExpression.isEmpty()) {
            return new IntermediateResponse<Void>().statusCode(404);
        }

        MatchExpression expr = matchExpression.get();
        if (expressionManager.delete(id)) {
            notificationFactory
                    .createBuilder()
                    .metaCategory("MatchExpressionDeleted")
                    .metaType(HttpMimeType.JSON)
                    .message(Map.of("id", id, "matchExpression", expr.getMatchExpression()))
                    .build()
                    .send();
            return new IntermediateResponse<Void>().statusCode(200);
        }
        throw new ApiException(500);
    }
}
