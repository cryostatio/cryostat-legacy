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
package io.cryostat.net.web.http.api.beta;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.agent.LocalProbeTemplateService;
import io.cryostat.core.agent.ProbeTemplate;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.net.web.http.api.v2.AbstractV2RequestHandler;
import io.cryostat.net.web.http.api.v2.IntermediateResponse;
import io.cryostat.net.web.http.api.v2.RequestParameters;

import com.google.gson.Gson;
import io.vertx.core.http.HttpMethod;

public class ProbeTemplateGetHandler extends AbstractV2RequestHandler<List<ProbeTemplate>> {

    static final String PATH = "probes";

    private final LocalProbeTemplateService probeTemplateService;
    private final FileSystem fs;
    private final Gson gson;

    @Inject
    ProbeTemplateGetHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            LocalProbeTemplateService probeTemplateService,
            FileSystem fs,
            Gson gson) {
        super(auth, credentialsManager, gson);
        this.probeTemplateService = probeTemplateService;
        this.fs = fs;
        this.gson = gson;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.V2;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.GET;
    }

    @Override
    public String path() {
        return basePath() + PATH;
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
    public boolean requiresAuthentication() {
        return true;
    }

    @Override
    public IntermediateResponse<List<ProbeTemplate>> handle(RequestParameters params)
            throws Exception {
        return new IntermediateResponse<List<ProbeTemplate>>()
                .body(probeTemplateService.getTemplates());
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(ResourceAction.READ_PROBE_TEMPLATE);
    }

    @Override
    public List<HttpMimeType> produces() {
        return List.of(HttpMimeType.JSON);
    }
}
