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

import java.time.Duration;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.inject.Named;
import javax.inject.Singleton;

import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Environment;
import io.cryostat.discovery.DiscoveryModule;
import io.cryostat.net.AuthManager;
import io.cryostat.net.AuthenticationScheme;
import io.cryostat.net.web.WebServer;

import com.nimbusds.jose.JWEDecrypter;
import com.nimbusds.jose.JWEEncrypter;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.DirectDecrypter;
import com.nimbusds.jose.crypto.DirectEncrypter;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;

@Module
public abstract class JwtModule {

    @Provides
    @Singleton
    static AssetJwtHelper provideAssetJwtFactory(
            Lazy<WebServer> webServer,
            JWSSigner signer,
            JWSVerifier verifier,
            JWEEncrypter encrypter,
            JWEDecrypter decrypter,
            AuthManager auth,
            Logger logger) {
        try {
            return new AssetJwtHelper(
                    webServer,
                    signer,
                    verifier,
                    encrypter,
                    decrypter,
                    !AuthenticationScheme.NONE.equals(auth.getScheme()),
                    logger);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Provides
    @Singleton
    static DiscoveryJwtHelper provideDiscoveryJwtFactory(
            Lazy<WebServer> webServer,
            JWSSigner signer,
            JWSVerifier verifier,
            JWEEncrypter encrypter,
            JWEDecrypter decrypter,
            AuthManager auth,
            @Named(DiscoveryModule.DISCOVERY_PING_DURATION) Duration discoveryPingPeriod,
            Logger logger) {
        try {
            return new DiscoveryJwtHelper(
                    webServer, signer, verifier, encrypter, decrypter, discoveryPingPeriod, logger);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Provides
    @Singleton
    static SecretKey provideSecretKey() {
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(256);
            return generator.generateKey();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Provides
    @Singleton
    static JWSSigner provideJwsSigner(SecretKey key) {
        try {
            return new MACSigner(key);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Provides
    @Singleton
    static JWSVerifier provideJwsVerifier(SecretKey key) {
        try {
            return new MACVerifier(key);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Provides
    @Singleton
    static JWEEncrypter provideJweEncrypter(Environment env, SecretKey key, Logger logger) {
        try {
            return new DirectEncrypter(key);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Provides
    @Singleton
    static JWEDecrypter provideJweDecrypter(Environment env, SecretKey key) {
        try {
            return new DirectDecrypter(key);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
