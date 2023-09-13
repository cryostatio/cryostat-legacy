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
import javax.persistence.RollbackException;
import javax.script.ScriptException;

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
import io.cryostat.platform.ServiceRef;
import io.cryostat.rules.MatchExpression;
import io.cryostat.rules.MatchExpressionEvaluator;
import io.cryostat.rules.MatchExpressionManager;
import io.cryostat.rules.MatchExpressionManager.MatchedMatchExpression;
import io.cryostat.rules.MatchExpressionValidationException;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hibernate.exception.ConstraintViolationException;

public class MatchExpressionsPostHandler extends AbstractV2RequestHandler<MatchedMatchExpression> {

    static final String PATH = "matchExpressions";

    private final MatchExpressionManager expressionManager;
    private final MatchExpressionEvaluator expressionEvaluator;
    private final NotificationFactory notificationFactory;

    @Inject
    MatchExpressionsPostHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            MatchExpressionManager expressionManager,
            MatchExpressionEvaluator expressionEvaluator,
            NotificationFactory notificationFactory,
            Gson gson) {
        super(auth, credentialsManager, gson);
        this.expressionManager = expressionManager;
        this.expressionEvaluator = expressionEvaluator;
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
        return HttpMethod.POST;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(ResourceAction.CREATE_MATCH_EXPRESSION);
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
    public List<HttpMimeType> consumes() {
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
    public IntermediateResponse<MatchedMatchExpression> handle(RequestParameters params)
            throws ApiException {
        try {
            RequestData requestData = gson.fromJson(params.getBody(), RequestData.class);
            String matchExpression = requestData.getMatchExpression();
            List<ServiceRef> targets = requestData.getTargets();
            if (StringUtils.isBlank(matchExpression)) {
                throw new ApiException(400, "'matchExpression' is required.");
            }
            expressionEvaluator.validate(matchExpression);
            if (targets != null) {
                Set<ServiceRef> matched =
                        expressionManager.resolveMatchingTargets(
                                matchExpression, (t) -> targets.contains(t));

                return new IntermediateResponse<MatchedMatchExpression>()
                        .statusCode(200)
                        .body(new MatchedMatchExpression(matchExpression, matched));
            } else {
                int id = expressionManager.addMatchExpression(matchExpression);
                Optional<MatchExpression> opt = expressionManager.get(id);
                if (opt.isEmpty()) {
                    throw new ApiException(500, "Failed to add match expression");
                }
                MatchExpression expr = opt.get();
                notificationFactory
                        .createBuilder()
                        .metaCategory("MatchExpressionAdded")
                        .metaType(HttpMimeType.JSON)
                        .message(Map.of("id", id, "matchExpression", expr.getMatchExpression()))
                        .build()
                        .send();
                return new IntermediateResponse<MatchedMatchExpression>()
                        .statusCode(201)
                        .addHeader(HttpHeaders.LOCATION, String.format("%s/%d", path(), id))
                        .body(new MatchedMatchExpression(expr));
            }
        } catch (JsonParseException e) {
            throw new ApiException(400, "Unable to parse JSON", e);
        } catch (RollbackException e) {
            if (ExceptionUtils.indexOfType(e, ConstraintViolationException.class) >= 0) {
                throw new ApiException(409, "Duplicate matchExpression", e);
            }
            throw new ApiException(500, e);
        } catch (MatchExpressionValidationException e) {
            throw new ApiException(400, e);
        } catch (ScriptException e) {
            throw new ApiException(400, "Invalid matchExpression", e);
        }
    }

    static class RequestData {
        private String matchExpression;
        private List<ServiceRef> targets;

        String getMatchExpression() {
            return matchExpression;
        }

        List<ServiceRef> getTargets() {
            return targets;
        }
    }
}
