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
import io.cryostat.core.net.Credentials;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.platform.ServiceRef;
import io.cryostat.recordings.RecordingTargetHelper;
import io.cryostat.rules.Rule;
import io.cryostat.rules.RuleRegistry;

import com.google.gson.Gson;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;

class RuleDeleteHandler extends AbstractV2RequestHandler<Void> {

    private static final String DELETE_RULE_CATEGORY = "RuleDeleted";
    static final String PATH = RuleGetHandler.PATH;
    static final String CLEAN_PARAM = "clean";

    private final Vertx vertx;
    private final RuleRegistry ruleRegistry;
    private final RecordingTargetHelper recordings;
    private final DiscoveryStorage storage;
    private final CredentialsManager credentialsManager;
    private final NotificationFactory notificationFactory;
    private final Logger logger;

    @Inject
    RuleDeleteHandler(
            Vertx vertx,
            AuthManager auth,
            RuleRegistry ruleRegistry,
            RecordingTargetHelper recordings,
            DiscoveryStorage storage,
            CredentialsManager credentialsManager,
            NotificationFactory notificationFactory,
            Gson gson,
            Logger logger) {
        super(auth, credentialsManager, gson);
        this.vertx = vertx;
        this.ruleRegistry = ruleRegistry;
        this.recordings = recordings;
        this.storage = storage;
        this.credentialsManager = credentialsManager;
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
        return HttpMethod.DELETE;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(ResourceAction.DELETE_RULE);
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
    public IntermediateResponse<Void> handle(RequestParameters params) throws ApiException {
        String name = params.getPathParams().get(Rule.Attribute.NAME.getSerialKey());
        if (!ruleRegistry.hasRuleByName(name)) {
            throw new ApiException(404);
        }
        Rule rule = ruleRegistry.getRule(name).get();
        try {
            ruleRegistry.deleteRule(rule);
        } catch (IOException e) {
            throw new ApiException(500, "IOException occurred while deleting rule", e);
        }
        notificationFactory
                .createBuilder()
                .metaCategory(DELETE_RULE_CATEGORY)
                .metaType(HttpMimeType.JSON)
                .message(rule)
                .build()
                .send();
        if (Boolean.valueOf(params.getQueryParams().get(CLEAN_PARAM))) {
            vertx.executeBlocking(
                    promise -> {
                        try {
                            cleanup(params, rule);
                            promise.complete();
                        } catch (Exception e) {
                            promise.fail(e);
                        }
                    });
        }
        return new IntermediateResponse<Void>().body(null);
    }

    private void cleanup(RequestParameters params, Rule rule) {
        storage.listDiscoverableServices().stream()
                .forEach(
                        (ServiceRef ref) -> {
                            vertx.executeBlocking(
                                    promise -> {
                                        try {
                                            if (ruleRegistry.applies(rule, ref)) {
                                                String targetId = ref.getServiceUri().toString();
                                                Credentials credentials =
                                                        credentialsManager.getCredentialsByTargetId(
                                                                targetId);
                                                ConnectionDescriptor cd =
                                                        new ConnectionDescriptor(
                                                                targetId, credentials);
                                                recordings.stopRecording(
                                                        cd, rule.getRecordingName(), true);
                                            }
                                            promise.complete();
                                        } catch (Exception e) {
                                            logger.error(e);
                                            promise.fail(e);
                                        }
                                    });
                        });
    }
}
