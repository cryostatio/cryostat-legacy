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
import java.net.URL;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.RequestHandler;
import io.cryostat.net.web.http.api.ApiVersion;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import dagger.Lazy;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.builder.HashCodeBuilder;

class ApiGetHandler extends AbstractV2RequestHandler<ApiGetHandler.ApiResponse> {

    private final Lazy<WebServer> webServer;
    private final Lazy<Set<RequestHandler>> handlers;

    @Inject
    ApiGetHandler(
            Lazy<WebServer> webServer,
            Lazy<Set<RequestHandler>> handlers,
            AuthManager auth,
            CredentialsManager credentialsManager,
            Gson gson) {
        super(auth, credentialsManager, gson);
        this.webServer = webServer;
        this.handlers = handlers;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.GET;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.GENERIC;
    }

    @Override
    public String path() {
        return basePath() + "api";
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return ResourceAction.NONE;
    }

    @Override
    public List<HttpMimeType> produces() {
        return List.of(HttpMimeType.JSON);
    }

    @Override
    public boolean requiresAuthentication() {
        return false;
    }

    @Override
    public IntermediateResponse<ApiResponse> handle(RequestParameters requestParams)
            throws Exception {
        List<SerializedHandler> serializedHandlers =
                handlers.get().stream()
                        .filter(RequestHandler::isAvailable)
                        .filter(handler -> !ApiVersion.GENERIC.equals(handler.apiVersion()))
                        .sorted((h1, h2) -> h1.path().compareTo(h2.path()))
                        .map(SerializedHandler::new)
                        .distinct()
                        .collect(Collectors.toList());

        URL resourceFilePath = new URL(webServer.get().getHostUrl(), "HTTP_API.md");

        return new IntermediateResponse<ApiResponse>()
                .body(new ApiResponse(resourceFilePath, serializedHandlers));
    }

    static class ApiResponse {
        @SerializedName("overview")
        final URL resourceFilePath;

        @SerializedName("endpoints")
        final List<SerializedHandler> handlers;

        ApiResponse(URL resourceFilePath, List<SerializedHandler> handlers) {
            this.resourceFilePath = resourceFilePath;
            this.handlers = handlers;
        }
    }

    static class SerializedHandler {
        @SerializedName("version")
        final ApiVersion apiVersion;

        @SerializedName("verb")
        final String httpMethod;

        final String path;

        SerializedHandler(RequestHandler handler) {
            this.apiVersion = handler.apiVersion();
            this.httpMethod = handler.httpMethod().name();
            this.path = URI.create(handler.path()).normalize().toString();
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof SerializedHandler)) {
                return false;
            }
            SerializedHandler osh = (SerializedHandler) o;
            return Objects.equals(apiVersion, osh.apiVersion)
                    && Objects.equals(httpMethod, osh.httpMethod)
                    && Objects.equals(path, osh.path);
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder().append(apiVersion).append(httpMethod).append(path).build();
        }
    }
}
