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

import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
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

public class AssetJwtHelper {

    public static final String RESOURCE_CLAIM = "resource";
    public static final String JMXAUTH_CLAIM = "jmxauth";

    private final Lazy<WebServer> webServer;
    private final JWSSigner signer;
    private final JWSVerifier verifier;
    private final JWEEncrypter encrypter;
    private final JWEDecrypter decrypter;
    private final boolean subjectRequired;

    AssetJwtHelper(
            Lazy<WebServer> webServer,
            JWSSigner signer,
            JWSVerifier verifier,
            JWEEncrypter encrypter,
            JWEDecrypter decrypter,
            boolean subjectRequired,
            Logger logger) {
        this.webServer = webServer;
        this.signer = signer;
        this.verifier = verifier;
        this.encrypter = encrypter;
        this.decrypter = decrypter;
        this.subjectRequired = subjectRequired;
    }

    public String createAssetDownloadJwt(String subject, String resource, String jmxauth)
            throws JOSEException,
                    SocketException,
                    UnknownHostException,
                    URISyntaxException,
                    MalformedURLException {
        String issuer = webServer.get().getHostUrl().toString();
        Date now = Date.from(Instant.now());
        Date expiry = Date.from(now.toInstant().plusSeconds(120));
        JWTClaimsSet claims =
                new JWTClaimsSet.Builder()
                        .issuer(issuer)
                        .audience(issuer)
                        .issueTime(now)
                        .notBeforeTime(now)
                        .expirationTime(expiry)
                        .subject(subject)
                        .claim(RESOURCE_CLAIM, resource)
                        .claim(JMXAUTH_CLAIM, jmxauth)
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

    public JWT parseAssetDownloadJwt(String rawToken)
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

        // TODO extract this claims verifier
        // TODO add a SecurityContext
        String cryostatUri = webServer.get().getHostUrl().toString();
        JWTClaimsSet exactMatchClaims =
                new JWTClaimsSet.Builder().issuer(cryostatUri).audience(cryostatUri).build();
        Set<String> requiredClaimNames =
                new HashSet<>(Set.of("exp", "nbf", "iat", "iss", "aud", RESOURCE_CLAIM));
        if (subjectRequired) {
            requiredClaimNames.add("sub");
        }
        new DefaultJWTClaimsVerifier<>(cryostatUri, exactMatchClaims, requiredClaimNames)
                .verify(jwt.getJWTClaimsSet(), null);

        return jwt;
    }
}
