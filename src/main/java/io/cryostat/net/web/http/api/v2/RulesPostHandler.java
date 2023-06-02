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

import java.io.IOException;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.rules.MatchExpressionValidationException;
import io.cryostat.rules.Rule;
import io.cryostat.rules.RuleException;
import io.cryostat.rules.RuleRegistry;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;

class RulesPostHandler extends AbstractV2RequestHandler<String> {

    private static final String CREATE_RULE_CATEGORY = "RuleCreated";
    static final String PATH = "rules";

    private final RuleRegistry ruleRegistry;
    private final NotificationFactory notificationFactory;
    private final Logger logger;

    @Inject
    RulesPostHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            RuleRegistry ruleRegistry,
            NotificationFactory notificationFactory,
            Gson gson,
            Logger logger) {
        super(auth, credentialsManager, gson);
        this.ruleRegistry = ruleRegistry;
        this.notificationFactory = notificationFactory;
        this.logger = logger;
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
        return HttpMethod.POST;
    }

    @Override
    public String path() {
        return basePath() + PATH;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(
                ResourceAction.CREATE_RULE,
                ResourceAction.READ_TARGET,
                ResourceAction.CREATE_RECORDING,
                ResourceAction.UPDATE_RECORDING,
                ResourceAction.READ_TEMPLATE);
    }

    @Override
    public List<HttpMimeType> produces() {
        return List.of(HttpMimeType.JSON);
    }

    @Override
    public List<HttpMimeType> consumes() {
        return List.of(
                HttpMimeType.MULTIPART_FORM, HttpMimeType.URLENCODED_FORM, HttpMimeType.JSON);
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
    public IntermediateResponse<String> handle(RequestParameters params) throws ApiException {
        Rule rule;
        String contentType = params.getHeaders().get(HttpHeaders.CONTENT_TYPE);
        
        if (contentType == null){
            contentType = "";
        }
        if (contentType.contains(";")) {
            contentType = contentType.substring(0, contentType.indexOf(";"));    
        }
        HttpMimeType mime =
                HttpMimeType.fromString(contentType);
        switch (mime) {
            case MULTIPART_FORM:
            case URLENCODED_FORM:
                try {
                    Rule.Builder builder = Rule.Builder.from(params.getFormAttributes());
                    rule = builder.build();
                } catch (MatchExpressionValidationException | IllegalArgumentException iae) {
                    throw new ApiException(400, iae);
                }
                break;
            case JSON:
                try {
                    rule = gson.fromJson(params.getBody(), Rule.class);

                    if (rule == null) {
                        throw new IllegalArgumentException("POST body was null");
                    }
                } catch (IllegalArgumentException | JsonSyntaxException e) {
                    throw new ApiException(400, e);
                }
                break;
            default:
                throw new ApiException(415, mime.mime());
        }

        try {
            rule = this.ruleRegistry.addRule(rule);
        } catch (RuleException e) {
            throw new ApiException(409, e);
        } catch (IOException e) {
            throw new ApiException(
                    500,
                    "IOException occurred while writing rule definition: " + e.getMessage(),
                    e);
        }
        notificationFactory
                .createBuilder()
                .metaCategory(CREATE_RULE_CATEGORY)
                .metaType(HttpMimeType.JSON)
                .message(rule)
                .build()
                .send();

        return new IntermediateResponse<String>()
                .statusCode(201)
                .addHeader(HttpHeaders.LOCATION, String.format("%s/%s", path(), rule.getName()))
                .body(rule.getName());
    }
}
