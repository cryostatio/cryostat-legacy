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
package io.cryostat.net.security.jwt;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.cryostat.core.log.Logger;
import io.cryostat.net.web.WebServer;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEDecrypter;
import com.nimbusds.jose.JWEEncrypter;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.Payload;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import dagger.Lazy;

public class DiscoveryJwtHelper {

    public static final String REALM_CLAIM = "realm";

    private final Lazy<WebServer> webServer;
    private final JWSSigner signer;
    private final JWSVerifier verifier;
    private final JWEEncrypter encrypter;
    private final JWEDecrypter decrypter;

    DiscoveryJwtHelper(
            Lazy<WebServer> webServer,
            JWSSigner signer,
            JWSVerifier verifier,
            JWEEncrypter encrypter,
            JWEDecrypter decrypter,
            Logger logger) {
        this.webServer = webServer;
        this.signer = signer;
        this.verifier = verifier;
        this.encrypter = encrypter;
        this.decrypter = decrypter;
    }

    public String createDiscoveryPluginJwt(
            String authzHeader, String realm, InetAddress requestAddr, URI resource)
            throws MalformedURLException, SocketException, UnknownHostException, URISyntaxException,
                    JOSEException {
        URL hostUrl = webServer.get().getHostUrl();
        String issuer = hostUrl.toString();
        Date now = Date.from(Instant.now());
        // FIXME extract this constant somewhere. We ping discovery plugins every 5 minutes, so
        // the tokens we supply them need to be valid at least that long. On ping we supply a new
        // token that the plugin should use to replace their previous one, which will soon expire.
        Date expiry = Date.from(now.toInstant().plus(5, ChronoUnit.MINUTES));
        JWTClaimsSet claims =
                new JWTClaimsSet.Builder()
                        .issuer(issuer)
                        .audience(List.of(issuer, requestAddr.toString()))
                        .issueTime(now)
                        .notBeforeTime(now)
                        .expirationTime(expiry)
                        .subject(authzHeader)
                        .claim(AssetJwtHelper.RESOURCE_CLAIM, resource.toASCIIString())
                        .claim(REALM_CLAIM, realm)
                        .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).build(), claims);
        jwt.sign(signer);

        JWEHeader header =
                new JWEHeader.Builder(JWEAlgorithm.DIR, EncryptionMethod.A256GCM)
                        .contentType("JWT")
                        .build();
        JWEObject jwe = new JWEObject(header, new Payload(jwt));
        jwe.encrypt(encrypter);

        return jwe.serialize();
    }

    public JWT parseDiscoveryPluginJwt(String rawToken, String realm, InetAddress requestAddr)
            throws ParseException, JOSEException, BadJWTException, SocketException,
                    UnknownHostException, URISyntaxException, MalformedURLException {
        JWEObject jwe = JWEObject.parse(rawToken);
        jwe.decrypt(decrypter);

        SignedJWT jwt = jwe.getPayload().toSignedJWT();
        jwt.verify(verifier);

        String cryostatUri = webServer.get().getHostUrl().toString();
        JWTClaimsSet exactMatchClaims =
                new JWTClaimsSet.Builder()
                        .issuer(cryostatUri)
                        .audience(List.of(cryostatUri, requestAddr.toString()))
                        .claim(REALM_CLAIM, realm)
                        .build();
        Set<String> requiredClaimNames =
                new HashSet<>(Set.of("exp", "nbf", "iat", "iss", "aud", "sub", REALM_CLAIM));
        new DefaultJWTClaimsVerifier<>(cryostatUri, exactMatchClaims, requiredClaimNames)
                .verify(jwt.getJWTClaimsSet(), null);

        return jwt;
    }
}
