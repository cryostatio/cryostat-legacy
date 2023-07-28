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
            expressionEvaluator.evaluates(matchExpression);
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
                throw new ApiException(400, "Duplicate matchExpression", e);
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
