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

import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;

import javax.script.ScriptException;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.Credentials;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.security.jwt.AssetJwtHelper;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.RequestHandler;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.proc.BadJWTException;
import dagger.Lazy;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;

public abstract class AbstractAssetJwtConsumingHandler implements RequestHandler {

    protected final AuthManager auth;
    protected final CredentialsManager credentialsManager;
    protected final AssetJwtHelper jwt;
    protected final Lazy<WebServer> webServer;
    protected final Logger logger;

    protected AbstractAssetJwtConsumingHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            AssetJwtHelper jwt,
            Lazy<WebServer> webServer,
            Logger logger) {
        this.auth = auth;
        this.credentialsManager = credentialsManager;
        this.jwt = jwt;
        this.webServer = webServer;
        this.logger = logger;
    }

    public abstract void handleWithValidJwt(RoutingContext ctx, JWT jwt) throws Exception;

    @Override
    public final void handle(RoutingContext ctx) {
        try {
            JWT jwt = validateJwt(ctx);
            handleWithValidJwt(ctx, jwt);
        } catch (ApiException | HttpException e) {
            throw e;
        } catch (Exception e) {
            if (AbstractAuthenticatedRequestHandler.isJmxAuthFailure(e)) {
                ctx.response()
                        .putHeader(
                                AbstractAuthenticatedRequestHandler.JMX_AUTHENTICATE_HEADER,
                                "Basic");
                throw new ApiException(427, "JMX Authentication Failure", e);
            }
            if (AbstractAuthenticatedRequestHandler.isUnknownTargetFailure(e)) {
                throw new ApiException(404, "Target Not Found", e);
            }
            if (AbstractAuthenticatedRequestHandler.isJmxSslFailure(e)) {
                throw new ApiException(502, "Target SSL Untrusted", e);
            }
            if (AbstractAuthenticatedRequestHandler.isServiceTypeFailure(e)) {
                throw new ApiException(504, "Non-JMX Port", e);
            }
            throw new ApiException(500, e);
        }
    }

    private JWT validateJwt(RoutingContext ctx)
            throws ParseException,
                    JOSEException,
                    SocketException,
                    UnknownHostException,
                    URISyntaxException,
                    MalformedURLException {
        String token = ctx.queryParams().get("token");
        JWT parsed;
        try {
            parsed = jwt.parseAssetDownloadJwt(token);
        } catch (BadJWTException e) {
            throw new ApiException(401, e);
        }

        URL hostUrl = webServer.get().getHostUrl();
        URI requestUri = new URI(ctx.request().absoluteURI());
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

    protected ConnectionDescriptor getConnectionDescriptorFromJwt(RoutingContext ctx, JWT jwt)
            throws ParseException {
        String targetId = ctx.pathParam("targetId");
        Credentials credentials = null;
        String jmxauth = jwt.getJWTClaimsSet().getStringClaim(AssetJwtHelper.JMXAUTH_CLAIM);
        if (jmxauth == null) {
            try {
                credentials = credentialsManager.getCredentialsByTargetId(targetId);
            } catch (ScriptException e) {
                logger.error(e);
            }
        } else {
            String c;
            try {
                Matcher m =
                        AbstractAuthenticatedRequestHandler.AUTH_HEADER_PATTERN.matcher(jmxauth);
                if (!m.find()) {
                    throw new ApiException(
                            427,
                            String.format("Invalid %s claim format", AssetJwtHelper.JMXAUTH_CLAIM));
                }
                String t = m.group("type");
                if (!"basic".equals(t.toLowerCase())) {
                    throw new ApiException(
                            427,
                            String.format(
                                    "Unacceptable %s credentials type",
                                    AssetJwtHelper.JMXAUTH_CLAIM));
                }
                c =
                        new String(
                                Base64.getUrlDecoder().decode(m.group("credentials")),
                                StandardCharsets.UTF_8);
            } catch (IllegalArgumentException iae) {
                throw new ApiException(
                        427,
                        String.format(
                                "%s claim credentials do not appear to be Base64-encoded",
                                AssetJwtHelper.JMXAUTH_CLAIM),
                        iae);
            }
            String[] parts = c.split(":");
            if (parts.length != 2) {
                throw new ApiException(
                        427,
                        String.format(
                                "Unrecognized %s claim credential format",
                                AssetJwtHelper.JMXAUTH_CLAIM));
            }
            credentials = new Credentials(parts[0], parts[1]);
        }
        return new ConnectionDescriptor(targetId, credentials);
    }
}
