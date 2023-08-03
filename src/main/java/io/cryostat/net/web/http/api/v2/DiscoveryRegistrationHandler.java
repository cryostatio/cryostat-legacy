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
            if (!Objects.equals(plugin.getCallback(), callbackUri)) {
                throw new ApiException(400);
            }

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
