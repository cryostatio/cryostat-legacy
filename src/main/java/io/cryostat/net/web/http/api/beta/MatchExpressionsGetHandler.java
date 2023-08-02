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
import java.util.Set;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
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

class MatchExpressionsGetHandler extends AbstractV2RequestHandler<List<MatchExpression>> {

    private final MatchExpressionManager expressionManager;

    @Inject
    MatchExpressionsGetHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            MatchExpressionManager expressionManager,
            Gson gson) {
        super(auth, credentialsManager, gson);
        this.expressionManager = expressionManager;
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
        return HttpMethod.GET;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(ResourceAction.READ_MATCH_EXPRESSION);
    }

    @Override
    public String path() {
        return basePath() + MatchExpressionsPostHandler.PATH;
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
    public IntermediateResponse<List<MatchExpression>> handle(RequestParameters params)
            throws ApiException {
        List<MatchExpression> expressions = expressionManager.getAll();
        return new IntermediateResponse<List<MatchExpression>>().body(expressions);
    }
}
