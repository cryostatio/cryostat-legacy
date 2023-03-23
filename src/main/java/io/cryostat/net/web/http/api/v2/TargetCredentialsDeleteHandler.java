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

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.SecurityContext;
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
    public SecurityContext securityContext(RequestParameters params) {
        return SecurityContext.DEFAULT;
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
