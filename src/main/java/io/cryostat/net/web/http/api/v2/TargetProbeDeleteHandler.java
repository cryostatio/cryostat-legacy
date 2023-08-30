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
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.agent.AgentJMXHelper;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Environment;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;

import com.google.gson.Gson;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;

class TargetProbeDeleteHandler extends AbstractV2RequestHandler<Void> {

    static final String PATH = "targets/:targetId/probes";

    private final Logger logger;
    private final NotificationFactory notificationFactory;
    private final FileSystem fs;
    private final TargetConnectionManager connectionManager;
    private final Environment env;
    private static final String NOTIFICATION_CATEGORY = "ProbesRemoved";

    @Inject
    TargetProbeDeleteHandler(
            Logger logger,
            NotificationFactory notificationFactory,
            FileSystem fs,
            AuthManager auth,
            CredentialsManager credentialsManager,
            TargetConnectionManager connectionManager,
            Environment env,
            Gson gson) {
        super(auth, credentialsManager, gson);
        this.logger = logger;
        this.notificationFactory = notificationFactory;
        this.connectionManager = connectionManager;
        this.env = env;
        this.fs = fs;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.V2;
    }

    @Override
    public String path() {
        return basePath() + PATH;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.DELETE;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return ResourceAction.NONE;
    }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }

    @Override
    public IntermediateResponse<Void> handle(RequestParameters requestParams) throws Exception {
        Map<String, String> pathParams = requestParams.getPathParams();
        String targetId = pathParams.get("targetId");
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isBlank(targetId)) {
            sb.append("targetId is required.");
            throw new ApiException(400, sb.toString().trim());
        }
        try {
            return connectionManager.executeConnectedTask(
                    getConnectionDescriptorFromParams(requestParams),
                    connection -> {
                        AgentJMXHelper helper = new AgentJMXHelper(connection.getHandle());
                        // The convention for removing probes in the agent controller mbean is to
                        // call defineEventProbes with a null argument.
                        helper.defineEventProbes(null);
                        notificationFactory
                                .createBuilder()
                                .metaCategory(NOTIFICATION_CATEGORY)
                                .metaType(HttpMimeType.JSON)
                                .message(Map.of("target", targetId))
                                .build()
                                .send();
                        return new IntermediateResponse<Void>().body(null);
                    });
        } catch (Exception e) {
            throw e;
        }
    }

    @Override
    public List<HttpMimeType> produces() {
        return List.of(HttpMimeType.PLAINTEXT);
    }

    @Override
    public boolean isAsync() {
        return false;
    }
}
