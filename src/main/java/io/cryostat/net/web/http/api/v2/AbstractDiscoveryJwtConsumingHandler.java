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
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import io.cryostat.core.log.Logger;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.discovery.PluginInfo;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.jwt.AssetJwtHelper;
import io.cryostat.net.security.jwt.DiscoveryJwtHelper;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.RequestHandler;
import io.cryostat.net.web.http.api.ApiMeta;
import io.cryostat.net.web.http.api.ApiResponse;
import io.cryostat.net.web.http.api.ApiResultData;
import io.cryostat.util.StringUtil;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.proc.BadJWTException;
import dagger.Lazy;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;

abstract class AbstractDiscoveryJwtConsumingHandler<T> implements RequestHandler {

    static final String X_FORWARDED_FOR = "X-Forwarded-For";

    protected final DiscoveryStorage storage;
    protected final AuthManager auth;
    protected final DiscoveryJwtHelper jwt;
    protected final Lazy<WebServer> webServer;
    protected final Function<String, UUID> uuidFromString;
    protected final Logger logger;

    protected AbstractDiscoveryJwtConsumingHandler(
            DiscoveryStorage storage,
            AuthManager auth,
            DiscoveryJwtHelper jwt,
            Lazy<WebServer> webServer,
            Function<String, UUID> uuidFromString,
            Logger logger) {
        this.storage = storage;
        this.auth = auth;
        this.jwt = jwt;
        this.webServer = webServer;
        this.uuidFromString = uuidFromString;
        this.logger = logger;
    }

    abstract void handleWithValidJwt(RoutingContext ctx, JWT jwt) throws Exception;

    protected boolean checkTokenTimeClaims() {
        return true;
    }

    @Override
    public final void handle(RoutingContext ctx) {
        try {
            JWT jwt = validateJwt(ctx);
            handleWithValidJwt(ctx, jwt);
        } catch (Exception e) {
            if (e instanceof ApiException) {
                throw (ApiException) e;
            }
            throw new ApiException(500, e);
        }
    }

    protected void writeResponse(RoutingContext ctx, IntermediateResponse<T> intermediateResponse) {
        ApiMeta meta = new ApiMeta(HttpMimeType.JSON, "OK");
        ApiResultData<T> data = new ApiResultData<>(intermediateResponse.getBody());
        ApiResponse<ApiResultData<T>> body = new ApiResponse<>(meta, data);
        ctx.json(body);
    }

    private JWT validateJwt(RoutingContext ctx)
            throws ParseException,
                    JOSEException,
                    SocketException,
                    UnknownHostException,
                    URISyntaxException,
                    MalformedURLException {
        String token = ctx.queryParams().get("token");
        if (StringUtils.isBlank(token)) {
            throw new ApiException(401);
        }
        UUID id = uuidFromString.apply(StringUtil.requireNonBlank(ctx.pathParam("id"), "id"));
        Optional<PluginInfo> plugin = storage.getById(id);
        if (!plugin.isPresent()) {
            throw new ApiException(404);
        }
        InetAddress addr = null;
        HttpServerRequest req = ctx.request();
        if (req.remoteAddress() != null) {
            addr = tryResolveAddress(addr, req.remoteAddress().host());
        }
        MultiMap headers = req.headers();
        addr = tryResolveAddress(addr, headers.get(RequestParameters.X_FORWARDED_FOR));

        URL hostUrl = webServer.get().getHostUrl();

        JWT parsed;
        try {
            parsed =
                    jwt.parseDiscoveryPluginJwt(
                            token,
                            plugin.get().getRealm(),
                            getResourceUri(hostUrl, id.toString()),
                            addr,
                            checkTokenTimeClaims());
        } catch (BadJWTException e) {
            throw new ApiException(401, e);
        }

        URI requestUri = new URI(req.absoluteURI());
        URI fullRequestUri =
                new URI(hostUrl.getProtocol(), hostUrl.getAuthority(), null, null, null)
                        .resolve(requestUri.getRawPath());
        URI relativeRequestUri = new URI(requestUri.getRawPath());
        URI resourceClaim;
        try {
            resourceClaim =
                    new URI(parsed.getJWTClaimsSet().getStringClaim(AssetJwtHelper.RESOURCE_CLAIM));
        } catch (URISyntaxException use) {
            throw new ApiException(401, use);
        }
        boolean matchesAbsoluteRequestUri =
                resourceClaim.isAbsolute() && Objects.equals(fullRequestUri, resourceClaim);
        boolean matchesRelativeRequestUri = Objects.equals(relativeRequestUri, resourceClaim);
        if (!matchesAbsoluteRequestUri && !matchesRelativeRequestUri) {
            throw new ApiException(401, "Token resource claim does not match requested resource");
        }

        try {
            String subject = parsed.getJWTClaimsSet().getSubject();
            if (!auth.validateHttpHeader(() -> subject, resourceActions()).get()) {
                throw new ApiException(401, "Token subject has insufficient permissions");
            }
        } catch (ExecutionException | InterruptedException e) {
            throw new ApiException(401, "Token subject permissions could not be determined");
        }

        return parsed;
    }

    static URI getResourceUri(URL baseUrl, String pluginId) throws URISyntaxException {
        return baseUrl.toURI().resolve("/api/v2.2/discovery/" + pluginId);
    }

    static InetAddress tryResolveAddress(InetAddress addr, String host) {
        if (StringUtils.isBlank(host)) {
            return addr;
        }
        try {
            return InetAddress.getByName(host);
        } catch (UnknownHostException e) {
            Logger.INSTANCE.error(e);
        }
        return addr;
    }
}
