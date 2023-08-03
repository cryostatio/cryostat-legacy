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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.agent.AgentJMXHelper;
import io.cryostat.core.agent.Event;
import io.cryostat.core.agent.ProbeTemplate;
import io.cryostat.net.AuthManager;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;

import com.google.gson.Gson;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.StringUtils;

class TargetProbesGetHandler extends AbstractV2RequestHandler<List<Event>> {

    static final String PATH = "targets/:targetId/probes";

    private final TargetConnectionManager connectionManager;

    @Inject
    TargetProbesGetHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            TargetConnectionManager connectionManager,
            Gson gson) {
        super(auth, credentialsManager, gson);
        this.connectionManager = connectionManager;
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
        return HttpMethod.GET;
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
    public IntermediateResponse<List<Event>> handle(RequestParameters requestParams)
            throws Exception {
        Map<String, String> pathParams = requestParams.getPathParams();
        String targetId = pathParams.get("targetId");
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isBlank(targetId)) {
            sb.append("targetId is required.");
            throw new ApiException(400, sb.toString().trim());
        }
        return connectionManager.executeConnectedTask(
                getConnectionDescriptorFromParams(requestParams),
                connection -> {
                    List<Event> response = new ArrayList<Event>();
                    AgentJMXHelper helper = new AgentJMXHelper(connection.getHandle());
                    try {
                        String probes = helper.retrieveEventProbes();
                        if (probes != null && !probes.isBlank()) {
                            ProbeTemplate template = new ProbeTemplate();
                            template.deserialize(
                                    new ByteArrayInputStream(
                                            probes.getBytes(StandardCharsets.UTF_8)));
                            for (Event e : template.getEvents()) {
                                response.add(e);
                            }
                        }
                    } catch (Exception e) {
                        throw new ApiException(501, e.getMessage());
                    }
                    return new IntermediateResponse<List<Event>>().body(response);
                });
    }

    @Override
    public List<HttpMimeType> produces() {
        return List.of(HttpMimeType.JSON);
    }

    @Override
    public boolean isAsync() {
        return false;
    }
}
