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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.jwt.AssetJwtHelper;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;

import com.google.gson.Gson;
import dagger.Lazy;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import org.apache.http.client.utils.URIBuilder;

class AuthTokenPostHandler extends AbstractV2RequestHandler<Map<String, String>> {

    static final String PATH = "auth/token";

    private final AssetJwtHelper jwt;
    private final Lazy<WebServer> webServer;

    @Inject
    AuthTokenPostHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            Gson gson,
            AssetJwtHelper jwt,
            Lazy<WebServer> webServer,
            Logger logger) {
        super(auth, credentialsManager, gson);
        this.jwt = jwt;
        this.webServer = webServer;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.V2_1;
    }

    @Override
    public String path() {
        return basePath() + PATH;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.POST;
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
    public List<HttpMimeType> produces() {
        return List.of(HttpMimeType.JSON);
    }

    @Override
    public List<HttpMimeType> consumes() {
        return List.of(HttpMimeType.MULTIPART_FORM, HttpMimeType.URLENCODED_FORM);
    }

    @Override
    public IntermediateResponse<Map<String, String>> handle(RequestParameters requestParams)
            throws Exception {
        String resource = requestParams.getFormAttributes().get(AssetJwtHelper.RESOURCE_CLAIM);
        if (resource == null) {
            throw new ApiException(
                    400,
                    String.format(
                            "\"%s\" form attribute is required", AssetJwtHelper.RESOURCE_CLAIM));
        }
        String resourcePrefix = webServer.get().getHostUrl().toString();
        URI resourceUri;
        try {
            resourceUri = new URI(resource);
        } catch (URISyntaxException use) {
            throw new ApiException(400, use);
        }
        if (resourceUri.isAbsolute() && !resource.startsWith(resourcePrefix)) {
            throw new ApiException(
                    400, String.format("\"%s\" URL is invalid", AssetJwtHelper.RESOURCE_CLAIM));
        }

        String authzHeader = requestParams.getHeaders().get(HttpHeaders.AUTHORIZATION);
        String jmxauth =
                requestParams
                        .getHeaders()
                        .get(AbstractAuthenticatedRequestHandler.JMX_AUTHORIZATION_HEADER);
        String token = jwt.createAssetDownloadJwt(authzHeader, resource, jmxauth);
        try {
            URI finalUri = new URIBuilder(resourceUri).setParameter("token", token).build();
            return new IntermediateResponse<Map<String, String>>()
                    .body(Map.of("resourceUrl", finalUri.toString()));
        } catch (URISyntaxException use) {
            throw new ApiException(400, use);
        }
    }
}
