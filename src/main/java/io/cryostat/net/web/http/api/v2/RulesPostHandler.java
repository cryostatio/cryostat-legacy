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
import org.apache.commons.lang3.StringUtils;

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
        String contentType =
                StringUtils.defaultString(params.getHeaders().get(HttpHeaders.CONTENT_TYPE));

        if (contentType.contains(";")) {
            contentType = contentType.substring(0, contentType.indexOf(";"));
        }
        HttpMimeType mime = HttpMimeType.fromString(contentType);
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
