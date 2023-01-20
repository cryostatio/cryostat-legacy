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
import io.cryostat.core.net.Credentials;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.SecurityContext;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.platform.ServiceRef;
import io.cryostat.recordings.RecordingTargetHelper;
import io.cryostat.rules.Rule;
import io.cryostat.rules.RuleRegistry;

import com.google.gson.Gson;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;

class RulePatchHandler extends AbstractV2RequestHandler<Void> {

    private static final String UPDATE_RULE_CATEGORY = "RuleUpdated";
    static final String PATH = RuleGetHandler.PATH;
    static final String CLEAN_PARAM = "clean";

    private final Vertx vertx;
    private final RuleRegistry ruleRegistry;
    private final DiscoveryStorage storage;
    private final RecordingTargetHelper recordings;
    private final NotificationFactory notificationFactory;
    private final Logger logger;

    @Inject
    RulePatchHandler(
            Vertx vertx,
            AuthManager auth,
            DiscoveryStorage storage,
            RecordingTargetHelper recordings,
            CredentialsManager credentialsManager,
            RuleRegistry ruleRegistry,
            NotificationFactory notificationFactory,
            Gson gson,
            Logger logger) {
        super(auth, credentialsManager, gson);
        this.vertx = vertx;
        this.recordings = recordings;
        this.ruleRegistry = ruleRegistry;
        this.storage = storage;
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
        return HttpMethod.PATCH;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(ResourceAction.UPDATE_RULE);
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
    public SecurityContext securityContext(RequestParameters params) {
        // FIXME cleanup and remove this, all handlers should use the list form below
        return null;
    }

    @Override
    public List<SecurityContext> securityContexts(RequestParameters params) {
        String name = params.getPathParams().get(Rule.Attribute.NAME.getSerialKey());
        Rule rule = ruleRegistry.getRule(name).get();
        return (List<SecurityContext>)
                auth.getSecurityContexts().stream()
                        .filter(sc -> rule.getContexts().contains(sc.getName()))
                        .toList();
    }

    @Override
    public IntermediateResponse<Void> handle(RequestParameters params) throws ApiException {
        String name = params.getPathParams().get(Rule.Attribute.NAME.getSerialKey());
        if (!ruleRegistry.hasRuleByName(name)) {
            throw new ApiException(404);
        }
        Rule rule = ruleRegistry.getRule(name).get();

        JsonObject obj = new JsonObject(params.getBody());
        Boolean enabled = obj.getBoolean(Rule.Attribute.ENABLED.getSerialKey());

        try {
            ruleRegistry.enableRule(rule, enabled);
        } catch (IOException e) {
            throw new ApiException(
                    500,
                    "IOException occurred while updating rule definition: " + e.getMessage(),
                    e);
        }

        notificationFactory
                .createBuilder()
                .metaCategory(UPDATE_RULE_CATEGORY)
                .metaType(HttpMimeType.JSON)
                .message(rule)
                .build()
                .send();

        if (!enabled && Boolean.valueOf(params.getQueryParams().get(CLEAN_PARAM))) {
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

        return new IntermediateResponse<Void>().statusCode(204);
    }

    private void cleanup(RequestParameters params, Rule rule) {
        storage.listUniqueReachableServices().stream()
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
                                                        cd, rule.getRecordingName());
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
