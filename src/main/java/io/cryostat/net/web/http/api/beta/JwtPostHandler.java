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
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;

import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;
import io.cryostat.net.NetworkConfiguration;
import io.cryostat.net.UserInfo;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.WebModule;
import io.cryostat.net.web.WebServer;
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
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import org.apache.http.client.utils.URIBuilder;

class JwtPostHandler extends AbstractV2RequestHandler<Map<String, String>> {

    static final String PATH = "jwt";

    private final JWTAuth jwtAuth;
    private final String signingAlgo;
    private final Lazy<WebServer> webServer;
    private final NetworkConfiguration netConf;
    private final Logger logger;

    @Inject
    JwtPostHandler(
            AuthManager auth,
            Gson gson,
            JWTAuth jwtAuth,
            @Named(WebModule.SIGNING_ALGO) String signingAlgo,
            Lazy<WebServer> webServer,
            NetworkConfiguration netConf,
            Logger logger) {
        super(auth, gson);
        this.jwtAuth = jwtAuth;
        this.signingAlgo = signingAlgo;
        this.webServer = webServer;
        this.netConf = netConf;
        this.logger = logger;
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
        String resource = requestParams.getFormAttributes().get("resource");
        if (resource == null) {
            throw new ApiException(400, "\"resource\" form attribute is required");
        }
        String resourcePrefix = webServer.get().getHostUrl().toString();
        if (!resource.startsWith(resourcePrefix)) {
            throw new ApiException(400, "\"resource\" URL is invalid");
        }

        UserInfo userInfo =
                auth.getUserInfo(() -> requestParams.getHeaders().get(HttpHeaders.AUTHORIZATION))
                        .get();
        JWTOptions options =
                new JWTOptions()
                        .setAlgorithm(signingAlgo)
                        .setIssuer(netConf.getWebServerHost())
                        .setAudience(List.of(netConf.getWebServerHost()))
                        .setSubject(userInfo.getUsername())
                        .setExpiresInMinutes(2);
        JsonObject claim = new JsonObject();
        claim.put("resource", resource);
        String jwt = jwtAuth.generateToken(claim, options);
        try {
            URI resourceUri = new URIBuilder(resource).setParameter("token", jwt).build();
            return new IntermediateResponse<Map<String, String>>()
                    .body(Map.of("token", jwt, "resourceUrl", resourceUri.toString()));
        } catch (URISyntaxException use) {
            throw new ApiException(400, use);
        }
    }
}
