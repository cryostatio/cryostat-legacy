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
package io.cryostat.net.security.jwt;

import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
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
import com.nimbusds.jose.proc.SecurityContext;
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
    private final Duration discoveryPingPeriod;

    DiscoveryJwtHelper(
            Lazy<WebServer> webServer,
            JWSSigner signer,
            JWSVerifier verifier,
            JWEEncrypter encrypter,
            JWEDecrypter decrypter,
            Duration discoveryPingPeriod,
            Logger logger) {
        this.webServer = webServer;
        this.signer = signer;
        this.verifier = verifier;
        this.encrypter = encrypter;
        this.decrypter = decrypter;
        this.discoveryPingPeriod = discoveryPingPeriod;
    }

    public String createDiscoveryPluginJwt(
            String authzHeader, String realm, InetAddress requestAddr, URI resource)
            throws MalformedURLException,
                    SocketException,
                    UnknownHostException,
                    URISyntaxException,
                    JOSEException {
        URL hostUrl = webServer.get().getHostUrl();
        String issuer = hostUrl.toString();
        Date now = Date.from(Instant.now());
        Date expiry = Date.from(now.toInstant().plus(discoveryPingPeriod.multipliedBy(2)));
        JWTClaimsSet claims =
                new JWTClaimsSet.Builder()
                        .issuer(issuer)
                        .audience(List.of(issuer, requestAddr.getHostAddress()))
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

    public JWT parseDiscoveryPluginJwt(
            String rawToken, String realm, URI resource, InetAddress requestAddr)
            throws ParseException,
                    JOSEException,
                    BadJWTException,
                    SocketException,
                    UnknownHostException,
                    URISyntaxException,
                    MalformedURLException {
        return parseDiscoveryPluginJwt(rawToken, realm, resource, requestAddr, true);
    }

    public JWT parseDiscoveryPluginJwt(
            String rawToken,
            String realm,
            URI resource,
            InetAddress requestAddr,
            boolean checkTimeClaims)
            throws ParseException,
                    JOSEException,
                    BadJWTException,
                    SocketException,
                    UnknownHostException,
                    URISyntaxException,
                    MalformedURLException {
        JWEObject jwe = JWEObject.parse(rawToken);
        jwe.decrypt(decrypter);

        SignedJWT jwt = jwe.getPayload().toSignedJWT();
        jwt.verify(verifier);

        String cryostatUri = webServer.get().getHostUrl().toString();
        JWTClaimsSet exactMatchClaims =
                new JWTClaimsSet.Builder()
                        .issuer(cryostatUri)
                        .audience(List.of(cryostatUri, requestAddr.getHostAddress()))
                        .claim(AssetJwtHelper.RESOURCE_CLAIM, resource.toASCIIString())
                        .claim(REALM_CLAIM, realm)
                        .build();
        Set<String> requiredClaimNames =
                new HashSet<>(Set.of("iat", "iss", "aud", "sub", REALM_CLAIM));
        if (checkTimeClaims) {
            requiredClaimNames.add("exp");
            requiredClaimNames.add("nbf");
        }
        DefaultJWTClaimsVerifier<SecurityContext> verifier =
                new DefaultJWTClaimsVerifier<>(cryostatUri, exactMatchClaims, requiredClaimNames);
        if (checkTimeClaims) {
            verifier.setMaxClockSkew(5);
        }
        verifier.verify(jwt.getJWTClaimsSet(), null);

        return jwt;
    }
}
