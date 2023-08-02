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
import java.util.concurrent.ExecutionException;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.net.AuthManager;
import io.cryostat.net.UnknownUserException;
import io.cryostat.net.UserInfo;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;

import com.google.gson.Gson;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;

class AuthPostHandler extends AbstractV2RequestHandler<UserInfo> {

    @Inject
    protected AuthPostHandler(AuthManager auth, CredentialsManager credentialsManager, Gson gson) {
        super(auth, credentialsManager, gson);
    }

    @Override
    public boolean requiresAuthentication() {
        return false;
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
        return basePath() + "auth";
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
    public IntermediateResponse<UserInfo> handle(RequestParameters requestParams) throws Exception {

        Optional<String> redirectUrl =
                auth.getLoginRedirectUrl(
                        () -> requestParams.getHeaders().get(HttpHeaders.AUTHORIZATION),
                        resourceActions());

        return redirectUrl
                .map(
                        location -> {
                            return new IntermediateResponse<UserInfo>()
                                    .addHeader("X-Location", location)
                                    .addHeader("access-control-expose-headers", "Location")
                                    .statusCode(302);
                        })
                .orElseGet(
                        () -> {
                            try {
                                return new IntermediateResponse<UserInfo>()
                                        .addHeader(
                                                WebServer.AUTH_SCHEME_HEADER,
                                                auth.getScheme().toString())
                                        .body(
                                                auth.getUserInfo(
                                                                () ->
                                                                        requestParams
                                                                                .getHeaders()
                                                                                .get(
                                                                                        HttpHeaders
                                                                                                .AUTHORIZATION))
                                                        .get());
                            } catch (ExecutionException | InterruptedException ee) {
                                Throwable cause = ee.getCause();
                                if (cause instanceof UnknownUserException) {
                                    throw new ApiException(401, "HTTP Authorization Failure", ee);
                                }
                                throw new ApiException(500, ee);
                            }
                        });
    }
}
