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
package io.cryostat.net.web.http.api.beta;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.jwt.AssetJwtHelper;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.net.web.http.api.v2.AbstractV2RequestHandler;
import io.cryostat.net.web.http.api.v2.ApiException;
import io.cryostat.net.web.http.api.v2.IntermediateResponse;
import io.cryostat.net.web.http.api.v2.RequestParameters;

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
            Gson gson,
            AssetJwtHelper jwt,
            Lazy<WebServer> webServer,
            Logger logger) {
        super(auth, gson);
        this.jwt = jwt;
        this.webServer = webServer;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.BETA;
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
    public HttpMimeType mimeType() {
        return HttpMimeType.JSON;
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
