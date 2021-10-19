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

import java.net.SocketException;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.Base64;
import java.util.regex.Matcher;

import io.cryostat.core.log.Logger;
import io.cryostat.core.net.Credentials;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.web.JwtFactory;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.RequestHandler;
import io.cryostat.net.web.http.api.v2.ApiException;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.proc.BadJWTException;
import io.vertx.ext.web.RoutingContext;

abstract class AbstractJwtConsumingHandler implements RequestHandler {

    protected final JwtFactory jwt;
    protected final Logger logger;

    protected AbstractJwtConsumingHandler(JwtFactory jwt, Logger logger) {
        this.jwt = jwt;
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
                    URISyntaxException {
        String token = ctx.queryParams().get("token");
        JWT parsed;
        try {
            parsed = jwt.parseAssetDownloadJwt(token);
        } catch (BadJWTException e) {
            throw new ApiException(401);
        }

        String rawRequestUri = ctx.request().absoluteURI();
        // We know there is a '?' (query param separator) because we checked for the 'token' query
        // param earlier
        String requestUri = rawRequestUri.substring(0, rawRequestUri.indexOf('?'));
        if (!requestUri.endsWith(parsed.getJWTClaimsSet().getStringClaim("resource"))) {
            throw new ApiException(401);
        }

        return parsed;
    }

    protected ConnectionDescriptor getConnectionDescriptorFromJwt(RoutingContext ctx, JWT jwt)
            throws ParseException {
        String targetId = ctx.pathParam("targetId");
        // TODO inject the CredentialsManager here to check for stored credentials
        Credentials credentials = null;
        // FIXME extract "jmxauth" to a constant and reuse in JwtFactory/AuthTokenPostHandler
        String jmxauth = jwt.getJWTClaimsSet().getStringClaim("jmxauth");
        if (jmxauth != null) {
            String c;
            try {
                Matcher m =
                        AbstractAuthenticatedRequestHandler.AUTH_HEADER_PATTERN.matcher(jmxauth);
                if (!m.find()) {
                    throw new ApiException(427, "Invalid jmxauth claim format");
                }
                String t = m.group("type");
                if (!"basic".equals(t.toLowerCase())) {
                    throw new ApiException(427, "Unacceptable jmxauth credentials type");
                }
                c =
                        new String(
                                Base64.getUrlDecoder().decode(m.group("credentials")),
                                StandardCharsets.UTF_8);
            } catch (IllegalArgumentException iae) {
                throw new ApiException(
                        427, "jmxauth claim credentials do not appear to be Base64-encoded", iae);
            }
            String[] parts = c.split(":");
            if (parts.length != 2) {
                throw new ApiException(427, "Unrecognized jmxauth claim credential format");
            }
            credentials = new Credentials(parts[0], parts[1]);
        }
        return new ConnectionDescriptor(targetId, credentials);
    }
}
