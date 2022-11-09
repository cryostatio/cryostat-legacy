/*
 * Copyright The Cryostat Authors
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software (each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
import io.cryostat.net.security.SecurityContext;

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
    public SecurityContext securityContext(RequestParameters params) {
        return SecurityContext.DEFAULT;
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
