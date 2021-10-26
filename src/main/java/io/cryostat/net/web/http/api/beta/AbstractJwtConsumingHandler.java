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

import io.cryostat.core.log.Logger;
import io.cryostat.core.net.Credentials;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.security.jwt.JwtFactory;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.RequestHandler;
import io.cryostat.net.web.http.api.v2.ApiException;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.proc.BadJWTException;
import dagger.Lazy;
import io.vertx.ext.web.RoutingContext;

abstract class AbstractJwtConsumingHandler implements RequestHandler {

    protected final AuthManager auth;
    protected final JwtFactory jwt;
    protected final Lazy<WebServer> webServer;
    protected final Logger logger;

    protected AbstractJwtConsumingHandler(
            AuthManager auth, JwtFactory jwt, Lazy<WebServer> webServer, Logger logger) {
        this.auth = auth;
        this.jwt = jwt;
        this.webServer = webServer;
        this.logger = logger;
    }

    abstract void handleWithValidJwt(RoutingContext ctx, JWT jwt) throws Exception;

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

    private JWT validateJwt(RoutingContext ctx)
            throws ParseException, JOSEException, SocketException, UnknownHostException,
                    URISyntaxException, MalformedURLException {
        String token = ctx.queryParams().get("token");
        JWT parsed;
        try {
            parsed = jwt.parseAssetDownloadJwt(token);
        } catch (BadJWTException e) {
            throw new ApiException(401);
        }

        URL hostUrl = webServer.get().getHostUrl();
        URI requestUri = new URI(ctx.request().absoluteURI());
        URI fullRequestUri =
                new URI(hostUrl.getProtocol(), hostUrl.getAuthority(), null, null, null)
                        .resolve(requestUri.getRawPath());
        URI resourceClaim =
                new URI(parsed.getJWTClaimsSet().getStringClaim(JwtFactory.RESOURCE_CLAIM));
        if (!Objects.equals(fullRequestUri, resourceClaim)) {
            throw new ApiException(401);
        }

        try {
            String subject = parsed.getJWTClaimsSet().getSubject();
            if (!auth.validateHttpHeader(() -> subject, resourceActions()).get()) {
                throw new ApiException(401);
            }
        } catch (ExecutionException | InterruptedException e) {
            throw new ApiException(401);
        }

        return parsed;
    }

    protected ConnectionDescriptor getConnectionDescriptorFromJwt(RoutingContext ctx, JWT jwt)
            throws ParseException {
        String targetId = ctx.pathParam("targetId");
        // TODO inject the CredentialsManager here to check for stored credentials
        Credentials credentials = null;
        String jmxauth = jwt.getJWTClaimsSet().getStringClaim(JwtFactory.JMXAUTH_CLAIM);
        if (jmxauth != null) {
            String c;
            try {
                Matcher m =
                        AbstractAuthenticatedRequestHandler.AUTH_HEADER_PATTERN.matcher(jmxauth);
                if (!m.find()) {
                    throw new ApiException(
                            427,
                            String.format("Invalid %s claim format", JwtFactory.JMXAUTH_CLAIM));
                }
                String t = m.group("type");
                if (!"basic".equals(t.toLowerCase())) {
                    throw new ApiException(
                            427,
                            String.format(
                                    "Unacceptable %s credentials type", JwtFactory.JMXAUTH_CLAIM));
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
                                JwtFactory.JMXAUTH_CLAIM),
                        iae);
            }
            String[] parts = c.split(":");
            if (parts.length != 2) {
                throw new ApiException(
                        427,
                        String.format(
                                "Unrecognized %s claim credential format",
                                JwtFactory.JMXAUTH_CLAIM));
            }
            credentials = new Credentials(parts[0], parts[1]);
        }
        return new ConnectionDescriptor(targetId, credentials);
    }
}
