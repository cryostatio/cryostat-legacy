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
package io.cryostat.net.web;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Named;
import javax.inject.Singleton;

import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Environment;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.net.AuthManager;
import io.cryostat.net.HttpServer;
import io.cryostat.net.NetworkConfiguration;
import io.cryostat.net.SslConfiguration;
import io.cryostat.net.web.http.HttpModule;
import io.cryostat.net.web.http.RequestHandler;

import com.google.gson.Gson;
import dagger.Module;
import dagger.Provides;
import io.vertx.core.Vertx;
import io.vertx.ext.auth.JWTOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.auth.jwt.impl.JWTAuthProviderImpl;
import io.vertx.ext.jwt.JWT;
import org.apache.commons.lang3.StringUtils;

@Module(includes = {HttpModule.class})
public abstract class WebModule {
    public static final String WEBSERVER_TEMP_DIR_PATH = "WEBSERVER_TEMP_DIR_PATH";
    public static final String JWT_SIGNING_ALGOS = "JWT_SIGNING_ALGOS";
    public static final String SUPPORTED_SIGNING_ALGOS = "SUPPORTED_SIGNING_ALGOS";
    public static final String SIGNING_ALGO = "SIGNING_ALGO";

    @Provides
    @Singleton
    static WebServer provideWebServer(
            HttpServer httpServer,
            NetworkConfiguration netConf,
            Set<RequestHandler> requestHandlers,
            RequestHandlerLookup requestHandlerLookup,
            Gson gson,
            AuthManager authManager,
            Logger logger) {
        return new WebServer(
                httpServer,
                netConf,
                requestHandlers,
                requestHandlerLookup,
                gson,
                authManager,
                logger);
    }

    @Provides
    @Singleton
    static RequestHandlerLookup provideRequeustHandlerLookup() {
        return new RequestHandlerLookup();
    }

    @Provides
    @Singleton
    @Named(WEBSERVER_TEMP_DIR_PATH)
    static Path provideWebServerTempDirPath(FileSystem fs) {
        try {
            return Files.createTempDirectory("cryostat").toAbsolutePath();
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    @Provides
    @Singleton
    static JWTAuth provideJWTAuth(
            Vertx vertx,
            NetworkConfiguration netConf,
            SslConfiguration sslConfig,
            @Named(SIGNING_ALGO) String signingAlgo) {
        try {
            JWTAuthOptions authOptions = new JWTAuthOptions();
            JWTOptions options =
                    new JWTOptions()
                            .setAlgorithm(signingAlgo)
                            .setIssuer(netConf.getWebServerHost())
                            .setAudience(List.of(netConf.getWebServerHost()));
            authOptions.setJWTOptions(options);
            return JWTAuth.create(vertx, sslConfig.applyToJWTAuthOptions(authOptions));
        } catch (UnknownHostException | SocketException e) {
            throw new RuntimeException(e);
        }
    }

    @Provides
    @Singleton
    static JWT provideJWT(JWTAuth jwtAuth) {
        try {
            Field f = JWTAuthProviderImpl.class.getDeclaredField("jwt");
            f.setAccessible(true);
            return (JWT) f.get(jwtAuth);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Provides
    @Singleton
    @Named(JWT_SIGNING_ALGOS)
    static List<String> provideJWTSigningAlgos() {
        return List.of(
                "HS256", "HS384", "HS512", "RS256", "RS384", "RS512", "ES256", "ES384", "ES512",
                "none");
    }

    @Provides
    @Singleton
    @Named(SUPPORTED_SIGNING_ALGOS)
    static List<String> provideSupportedSigningAlgos(Environment env) {
        // FIXME extract this env var name to a constant and document it
        String raw = env.getEnv("CRYOSTAT_SUPPORTED_SIGNING_ALGOS", "none");
        String[] split = raw.split(",");
        List<String> result =
                new ArrayList<>(
                        Arrays.asList(split).stream()
                                .map(String::trim)
                                .filter(StringUtils::isNotBlank)
                                .collect(Collectors.toList()));
        if (!result.contains("none")) {
            result.add("none");
        }
        return result;
    }

    @Provides
    @Singleton
    @Named(SIGNING_ALGO)
    static String provideSigningAlgo(
            SslConfiguration sslConf,
            @Named(JWT_SIGNING_ALGOS) List<String> signingAlgos,
            @Named(SUPPORTED_SIGNING_ALGOS) List<String> supportedSigningAlgos,
            Logger logger) {
        logger.info("Known signing algorithms: {}", signingAlgos);
        if (!sslConf.enabled()) {
            supportedSigningAlgos = List.of("none");
        }
        logger.info("Supported signing algorithms: {}", supportedSigningAlgos);
        List<String> intersection =
                signingAlgos.stream()
                        .distinct()
                        .filter(supportedSigningAlgos::contains)
                        .collect(Collectors.toList());
        logger.info("Intersection of algorithms: {}", intersection);
        String algo = intersection.get(0);
        logger.info("Using JWT signing algorithm {}", algo);
        return algo;
    }
}
