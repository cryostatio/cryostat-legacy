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
package io.cryostat.net.web.http.api.beta;

import java.lang.reflect.Type;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;
import javax.persistence.RollbackException;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.SecurityContext;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.net.web.http.api.v2.AbstractV2RequestHandler;
import io.cryostat.net.web.http.api.v2.ApiException;
import io.cryostat.net.web.http.api.v2.IntermediateResponse;
import io.cryostat.net.web.http.api.v2.RequestParameters;
import io.cryostat.platform.ServiceRef;
import io.cryostat.rules.MatchExpression;
import io.cryostat.rules.MatchExpressionManager;
import io.cryostat.rules.MatchExpressionManager.MatchedMatchExpression;
import io.cryostat.rules.MatchExpressionValidationException;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hibernate.exception.ConstraintViolationException;

public class MatchExpressionsPostHandler extends AbstractV2RequestHandler<MatchedMatchExpression> {

    static final String PATH = "matchExpressions";

    private final MatchExpressionManager expressionManager;
    private final NotificationFactory notificationFactory;

    @Inject
    MatchExpressionsPostHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            MatchExpressionManager expressionManager,
            NotificationFactory notificationFactory,
            Gson gson) {
        super(auth, credentialsManager, gson);
        this.expressionManager = expressionManager;
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
        return List.of(HttpMimeType.MULTIPART_FORM, HttpMimeType.URLENCODED_FORM);
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
    public SecurityContext securityContext(RequestParameters ctx) {
        return SecurityContext.DEFAULT;
    }

    @Override
    public IntermediateResponse<MatchedMatchExpression> handle(RequestParameters params)
            throws ApiException {
        String matchExpression = params.getFormAttributes().get("matchExpression");
        String targets = params.getFormAttributes().get("targets");
        if (StringUtils.isBlank(matchExpression)) {
            throw new ApiException(400, "'matchExpression' is required.");
        }
        try {
            if (StringUtils.isNotBlank(targets)) {
                Set<ServiceRef> matched;
                List<ServiceRef> parsedTargets = parseTargets(targets);
                matched =
                        expressionManager.resolveMatchingTargets(
                                matchExpression, (t) -> parsedTargets.contains(t));

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
            throw new ApiException(400, "JSON formatting error", e);
        } catch (RollbackException e) {
            if (ExceptionUtils.indexOfType(e, ConstraintViolationException.class) >= 0) {
                throw new ApiException(400, "Duplicate matchExpression", e);
            }
            throw new ApiException(500, e);
        } catch (MatchExpressionValidationException e) {
            throw new ApiException(400, e);
        }
    }

    public List<ServiceRef> parseTargets(String targets) {
        Objects.requireNonNull(targets, "Targets must not be null");
        Type mapType = new TypeToken<List<ServiceRef>>() {}.getType();
        List<ServiceRef> parsedTargets = gson.fromJson(targets, mapType);
        return parsedTargets;
    }
}
