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
import io.cryostat.recordings.RecordingMetadataManager.SecurityContext;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import dagger.Lazy;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.builder.HashCodeBuilder;

class ApiGetHandler extends AbstractV2RequestHandler<ApiGetHandler.ApiResponse> {

    private final Lazy<WebServer> webServer;
    private final Lazy<Set<RequestHandler<?>>> handlers;

    @Inject
    ApiGetHandler(
            Lazy<WebServer> webServer,
            Lazy<Set<RequestHandler<?>>> handlers,
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
	public SecurityContext securityContext(RequestParameters params) {
        return SecurityContext.DEFAULT;
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
