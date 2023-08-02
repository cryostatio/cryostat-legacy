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
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.configuration.CredentialsManager.MatchedCredentials;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.platform.ServiceRef;

import com.google.gson.Gson;
import io.vertx.core.http.HttpMethod;

class CredentialGetHandler extends AbstractV2RequestHandler<MatchedCredentials> {

    @Inject
    CredentialGetHandler(AuthManager auth, CredentialsManager credentialsManager, Gson gson) {
        super(auth, credentialsManager, gson);
    }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.V2_2;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.GET;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(ResourceAction.READ_CREDENTIALS);
    }

    @Override
    public String path() {
        return basePath() + "credentials/:id";
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
    public IntermediateResponse<MatchedCredentials> handle(RequestParameters params)
            throws ApiException {
        int id = Integer.parseInt(params.getPathParams().get("id"));
        Optional<String> matchExpression = credentialsManager.get(id);
        if (matchExpression.isEmpty()) {
            return new IntermediateResponse<MatchedCredentials>().statusCode(404);
        }
        Set<ServiceRef> targets = credentialsManager.resolveMatchingTargets(id);
        MatchedCredentials match = new MatchedCredentials(matchExpression.get(), targets);
        return new IntermediateResponse<MatchedCredentials>().body(match);
    }
}
