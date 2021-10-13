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

import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;

import io.cryostat.net.AuthManager;
import io.cryostat.net.NetworkConfiguration;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.WebModule;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.net.web.http.api.v2.AbstractV2RequestHandler;
import io.cryostat.net.web.http.api.v2.IntermediateResponse;
import io.cryostat.net.web.http.api.v2.RequestParameters;

import com.google.gson.Gson;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.jwt.JWTAuth;

class JwtGetHandler extends AbstractV2RequestHandler<String> {

    private final NetworkConfiguration netConf;
    private final JWTAuth jwtAuth;
    private final String signingAlgo;

    @Inject
    JwtGetHandler(
            JWTAuth jwtAuth,
            @Named(WebModule.SIGNING_ALGO) String signingAlgo,
            NetworkConfiguration netConf,
            AuthManager auth,
            Gson gson) {
        super(auth, gson);
        this.jwtAuth = jwtAuth;
        this.netConf = netConf;
        this.signingAlgo = signingAlgo;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.BETA;
    }

    @Override
    public String path() {
        return basePath() + "jwt";
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
        return false;
    }

    @Override
    public HttpMimeType mimeType() {
        return HttpMimeType.JSON;
    }

    @Override
    public IntermediateResponse<String> handle(RequestParameters requestParams) throws Exception {
        JsonObject claim = new JsonObject();
        JWTOptions options =
                new JWTOptions()
                        .setAlgorithm(signingAlgo)
                        .setIssuer(netConf.getWebServerHost())
                        .setAudience(List.of(netConf.getWebServerHost()))
                        .setPermissions(List.of())
                        .setSubject("JohnDoe")
                        .setExpiresInMinutes(2);
        String jwt = jwtAuth.generateToken(claim, options);
        return new IntermediateResponse<String>().body(jwt);
    }
}
