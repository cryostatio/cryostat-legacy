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

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import javax.inject.Inject;
import javax.inject.Named;

import io.cryostat.MainModule;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.discovery.PluginInfo;
import io.cryostat.discovery.RegistrationException;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.jwt.DiscoveryJwtHelper;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.util.StringUtil;

import com.google.gson.Gson;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.proc.BadJWTException;
import dagger.Lazy;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;

class DiscoveryRegistrationHandler extends AbstractV2RequestHandler<Map<String, String>> {

    static final String PATH = "discovery";
    private final DiscoveryStorage storage;
    private final Lazy<WebServer> webServer;
    private final DiscoveryJwtHelper jwtFactory;
    private final Function<String, UUID> uuidFromString;
    private final Logger logger;

    @Inject
    DiscoveryRegistrationHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            DiscoveryStorage storage,
            Lazy<WebServer> webServer,
            DiscoveryJwtHelper jwt,
            @Named(MainModule.UUID_FROM_STRING) Function<String, UUID> uuidFromString,
            Gson gson,
            Logger logger) {
        super(auth, credentialsManager, gson);
        this.storage = storage;
        this.webServer = webServer;
        this.jwtFactory = jwt;
        this.uuidFromString = uuidFromString;
        this.logger = logger;
    }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.V2_2;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.POST;
    }

    @Override
    public String path() {
        return basePath() + PATH;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(ResourceAction.CREATE_TARGET);
    }

    @Override
    public List<HttpMimeType> produces() {
        return List.of(HttpMimeType.JSON);
    }

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public IntermediateResponse<Map<String, String>> handle(RequestParameters params)
            throws Exception {
        String pluginId, realm, priorToken;
        URI callbackUri;
        try {
            JsonObject body = new JsonObject(params.getBody());
            pluginId = body.getString("id");
            realm = StringUtil.requireNonBlank(body.getString("realm"), "realm");
            callbackUri =
                    new URI(StringUtil.requireNonBlank(body.getString("callback"), "callback"));
            priorToken = body.getString("token");
        } catch (URISyntaxException | IllegalArgumentException | DecodeException e) {
            throw new ApiException(400, e);
        }

        InetAddress address = params.getAddress();
        String authzHeader =
                Optional.ofNullable(params.getHeaders().get(HttpHeaders.AUTHORIZATION))
                        .orElse("None");
        URL hostUrl = webServer.get().getHostUrl();
        if (StringUtils.isBlank(pluginId) || StringUtils.isBlank(priorToken)) {
            try {
                pluginId = storage.register(realm, callbackUri).toString();
            } catch (RegistrationException e) {
                throw new ApiException(400, e);
            }
        } else {
            PluginInfo plugin =
                    storage.getById(uuidFromString.apply(pluginId))
                            .orElseThrow(() -> new ApiException(404));
            if (!Objects.equals(plugin.getRealm(), realm)) {
                throw new ApiException(400);
            }
            // userinfo is allowed to change, otherwise the callback should be the same
            URI prevCallback = new URIBuilder(plugin.getCallback()).setUserInfo(null).build();
            URI newCallback = new URIBuilder(callbackUri).setUserInfo(null).build();
            if (!Objects.equals(prevCallback, newCallback)) {
                throw new ApiException(400);
            }

            storage.refresh(plugin.getId(), callbackUri);

            try {
                jwtFactory.parseDiscoveryPluginJwt(
                        priorToken,
                        realm,
                        AbstractDiscoveryJwtConsumingHandler.getResourceUri(hostUrl, pluginId),
                        address,
                        false);
            } catch (JOSEException e) {
                throw new ApiException(400, e);
            } catch (BadJWTException e) {
                throw new ApiException(401, e);
            }
        }

        String token =
                jwtFactory.createDiscoveryPluginJwt(
                        authzHeader,
                        realm,
                        address,
                        AbstractDiscoveryJwtConsumingHandler.getResourceUri(hostUrl, pluginId));
        return new IntermediateResponse<Map<String, String>>()
                .statusCode(201)
                .addHeader(HttpHeaders.LOCATION, String.format("%s/%s", path(), pluginId))
                .body(Map.of("id", pluginId, "token", token));
    }
}
