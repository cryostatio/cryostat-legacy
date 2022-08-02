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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.platform.ServiceRef;
import io.cryostat.rules.Rule;
import io.cryostat.rules.RuleRegistry;

import com.google.gson.Gson;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.vertx.core.http.HttpMethod;

class RuleDeleteHandler extends AbstractV2RequestHandler<List<RuleDeleteHandler.CleanupFailure>> {

    private static final String DELETE_RULE_CATEGORY = "RuleDeleted";
    static final String PATH = RuleGetHandler.PATH;
    static final String CLEAN_PARAM = "clean";

    private final RuleRegistry ruleRegistry;
    private final TargetConnectionManager targetConnectionManager;
    private final DiscoveryStorage storage;
    private final CredentialsManager credentialsManager;
    private final NotificationFactory notificationFactory;
    private final Logger logger;

    @Inject
    RuleDeleteHandler(
            AuthManager auth,
            RuleRegistry ruleRegistry,
            TargetConnectionManager targetConnectionManager,
            DiscoveryStorage storage,
            CredentialsManager credentialsManager,
            NotificationFactory notificationFactory,
            Gson gson,
            Logger logger) {
        super(auth, gson);
        this.ruleRegistry = ruleRegistry;
        this.targetConnectionManager = targetConnectionManager;
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
    public HttpMimeType mimeType() {
        return HttpMimeType.PLAINTEXT;
    }

    @Override
    public IntermediateResponse<List<RuleDeleteHandler.CleanupFailure>> handle(
            RequestParameters params) throws ApiException {
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
        List<CleanupFailure> failures = new ArrayList<>();
        if (Boolean.valueOf(params.getQueryParams().get(CLEAN_PARAM))) {
            for (ServiceRef ref : storage.listDiscoverableServices()) {
                if (!ruleRegistry.applies(rule, ref)) {
                    continue;
                }
                try {
                    targetConnectionManager.executeConnectedTask(
                            new ConnectionDescriptor(ref, credentialsManager.getCredentials(ref)),
                            conn -> {
                                conn.getService().getAvailableRecordings().stream()
                                        .filter(
                                                rec ->
                                                        rec.getName()
                                                                .equals(rule.getRecordingName()))
                                        .findFirst()
                                        .ifPresent(
                                                r -> {
                                                    try {
                                                        conn.getService().stop(r);
                                                    } catch (Exception e) {
                                                        logger.error(new ApiException(500, e));
                                                        CleanupFailure failure =
                                                                new CleanupFailure();
                                                        failure.ref = ref;
                                                        failure.message = e.getMessage();
                                                        failures.add(failure);
                                                    }
                                                });
                                return null;
                            });
                } catch (Exception e) {
                    logger.error(new ApiException(500, e));
                    CleanupFailure failure = new CleanupFailure();
                    failure.ref = ref;
                    failure.message = e.getMessage();
                    failures.add(failure);
                }
            }
        }
        if (failures.size() == 0) {
            return new IntermediateResponse<List<CleanupFailure>>().body(null);
        } else {
            return new IntermediateResponse<List<CleanupFailure>>().statusCode(500).body(failures);
        }
    }

    @SuppressFBWarnings("URF_UNREAD_FIELD")
    static class CleanupFailure {
        ServiceRef ref;
        String message;
    }
}
