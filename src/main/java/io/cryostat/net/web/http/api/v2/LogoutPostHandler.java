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

import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;

import com.google.gson.Gson;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;

class LogoutPostHandler extends AbstractV2RequestHandler<Void> {

    @Inject
    protected LogoutPostHandler(
            AuthManager auth, CredentialsManager credentialsManager, Gson gson) {
        super(auth, credentialsManager, gson);
    }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.V2_1;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.POST;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return ResourceAction.NONE;
    }

    @Override
    public String path() {
        return basePath() + "logout";
    }

    @Override
    public List<HttpMimeType> produces() {
        return List.of(HttpMimeType.JSON);
    }

    @Override
    public boolean isAsync() {
        return true;
    }

    @Override
    public IntermediateResponse<Void> handle(RequestParameters requestParams) throws Exception {
        Optional<String> logoutRedirectUrl =
                auth.logout(() -> requestParams.getHeaders().get(HttpHeaders.AUTHORIZATION));
        return logoutRedirectUrl
                .map(
                        location -> {
                            return new IntermediateResponse<Void>()
                                    .addHeader("X-Location", location)
                                    .addHeader("access-control-expose-headers", "Location")
                                    .statusCode(302);
                        })
                .orElse(new IntermediateResponse<Void>().body(null));
    }
}
