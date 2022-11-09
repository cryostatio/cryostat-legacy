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
import io.cryostat.net.security.SecurityContext;
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

abstract class AbstractDiscoveryJwtConsumingHandler<T> implements RequestHandler<RoutingContext> {

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

    @Override
    public final SecurityContext securityContext(RoutingContext ctx) {
        return SecurityContext.DEFAULT;
    }

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
            throws ParseException, JOSEException, SocketException, UnknownHostException,
                    URISyntaxException, MalformedURLException {
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
            if (!auth.validateHttpHeader(() -> subject, securityContext(ctx), resourceActions())
                    .get()) {
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
