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

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.MBeanMetrics;
import io.cryostat.net.AuthManager;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;

import com.google.gson.Gson;
import io.vertx.core.http.HttpMethod;

public class MBeanMetricsGetHandler extends AbstractV2RequestHandler<MBeanMetrics> {

    private final TargetConnectionManager tcm;
    private final Logger logger;

    @Inject
    MBeanMetricsGetHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            Gson gson,
            TargetConnectionManager tcm,
            Logger logger) {
        super(auth, credentialsManager, gson);
        this.tcm = tcm;
        this.logger = logger;
    }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.V2_3;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.GET;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(ResourceAction.READ_TARGET, ResourceAction.READ_CREDENTIALS);
    }

    @Override
    public String path() {
        return basePath() + "targets/:targetId/mbeanMetrics";
    }

    @Override
    public List<HttpMimeType> produces() {
        return List.of(HttpMimeType.JSON);
    }

    @Override
    public IntermediateResponse<MBeanMetrics> handle(RequestParameters params) throws Exception {
        return tcm.executeConnectedTask(
                getConnectionDescriptorFromParams(params),
                conn -> {
                    MBeanMetrics metrics = conn.getMBeanMetrics();
                    return new IntermediateResponse<MBeanMetrics>().body(metrics);
                });
    }
}
