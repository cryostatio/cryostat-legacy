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
package io.cryostat.net;

import java.nio.file.Path;

import io.cryostat.configuration.Variables;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Environment;
import io.cryostat.core.sys.FileSystem;

import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PfxOptions;
import org.apache.commons.lang3.tuple.Pair;

public class SslConfiguration {
    private final Environment env;
    private final FileSystem fs;
    private final Logger logger;

    private final SslConfigurationStrategy strategy;

    SslConfiguration(Environment env, FileSystem fs, Logger logger)
            throws SslConfigurationException {
        this.env = env;
        this.fs = fs;
        this.logger = logger;

        if (env.hasEnv(Variables.DISABLE_SSL)) {
            String disableSslValue = env.getEnv(Variables.DISABLE_SSL);
            if ("true".equals(disableSslValue) || "TRUE".equals(disableSslValue )) {
                strategy = new NoSslStrategy();
                logger.info("Selected NoSSL strategy");
                return;
            }
        }

        {
            Path path = obtainKeyStorePathIfSpecified();
            if (path != null) {
                strategy = new KeyStoreStrategy(path, env.getEnv(Variables.KEYSTORE_PASS_ENV, ""));
                logger.info("Selected SSL KeyStore strategy with keystore {}", path.toString());
                return;
            }
        }

        {
            Pair<Path, Path> pair = obtainKeyCertPathPairIfSpecified();
            if (pair != null) {
                Path key = pair.getLeft();
                Path cert = pair.getRight();
                strategy = new KeyCertStrategy(key, cert);
                logger.info(
                        "Selected SSL KeyCert strategy with key {} and cert {}",
                        key.toString(),
                        cert.toString());
                return;
            }
        }

        {
            Path path = discoverKeyStorePathInDefaultLocations();
            if (path != null) {
                strategy = new KeyStoreStrategy(path, env.getEnv(Variables.KEYSTORE_PASS_ENV, ""));
                logger.info("Selected SSL KeyStore strategy in default location");
                return;
            }
        }

        {
            Pair<Path, Path> pair = discoverKeyCertPathPairInDefaultLocations();
            if (pair != null) {
                strategy = new KeyCertStrategy(pair.getLeft(), pair.getRight());
                logger.info("Selected SSL KeyCert strategy in default location");
                return;
            }
        }

        strategy = new NoSslStrategy();
        logger.info("Selected NoSSL strategy");
    }

    // Test-only constructor
    SslConfiguration(
            Environment env, FileSystem fs, Logger logger, SslConfigurationStrategy strategy) {
        this.env = env;
        this.fs = fs;
        this.logger = logger;
        this.strategy = strategy;
    }

    Path obtainKeyStorePathIfSpecified() throws SslConfigurationException {
        if (!env.hasEnv(Variables.KEYSTORE_PATH_ENV)) {
            return null;
        }

        Path path = fs.pathOf(env.getEnv(Variables.KEYSTORE_PATH_ENV)).normalize();
        if (!fs.exists(path)) {
            throw new SslConfigurationException(
                    String.format(
                            "KEYSTORE_PATH refers to a non-existent file: \"%s\"",
                            path.toString()));
        }

        return path;
    }

    Pair<Path, Path> obtainKeyCertPathPairIfSpecified() throws SslConfigurationException {
        if (!env.hasEnv(Variables.KEY_PATH_ENV) && !env.hasEnv(Variables.CERT_PATH_ENV)) {
            return null;
        }

        if (env.hasEnv(Variables.KEY_PATH_ENV) ^ env.hasEnv(Variables.CERT_PATH_ENV)) {
            throw new SslConfigurationException("both KEY_PATH and CERT_PATH must be specified");
        }

        Path key = fs.pathOf(env.getEnv(Variables.KEY_PATH_ENV)).normalize();
        Path cert = fs.pathOf(env.getEnv(Variables.CERT_PATH_ENV)).normalize();
        if (!fs.exists(key)) {
            throw new SslConfigurationException(
                    String.format(
                            "KEY_PATH refers to a non-existent file: \"%s\"", key.toString()));
        }

        if (!fs.exists(cert)) {
            throw new SslConfigurationException(
                    String.format(
                            "CERT_PATH refers to a non-existent file: \"%s\"", cert.toString()));
        }

        return Pair.of(key, cert);
    }

    Path discoverKeyStorePathInDefaultLocations() {
        Path home = fs.pathOf(System.getProperty("user.home", "/"));

        if (fs.exists(home.resolve("cryostat-keystore.jks"))) {
            return home.resolve("cryostat-keystore.jks");
        } else if (fs.exists(home.resolve("cryostat-keystore.pfx"))) {
            return home.resolve("cryostat-keystore.pfx");
        } else if (fs.exists(home.resolve("cryostat-keystore.p12"))) {
            return home.resolve("cryostat-keystore.p12");
        }

        return null;
    }

    Pair<Path, Path> discoverKeyCertPathPairInDefaultLocations() {
        Path home = fs.pathOf(System.getProperty("user.home", "/"));
        Path key = null;
        Path cert = null;

        // discover keyPath
        if (fs.exists(home.resolve("cryostat-key.pem"))) {
            key = home.resolve("cryostat-key.pem");
        }

        // discover certPath
        if (fs.exists(home.resolve("cryostat-cert.pem"))) {
            cert = home.resolve("cryostat-cert.pem");
        }

        if (key == null || cert == null) {
            return null;
        }

        return Pair.of(key, cert);
    }

    public HttpServerOptions applyToHttpServerOptions(HttpServerOptions options) {
        return strategy.applyToHttpServerOptions(options);
    }

    public boolean enabled() {
        return strategy.enabled();
    }

    interface SslConfigurationStrategy {
        HttpServerOptions applyToHttpServerOptions(HttpServerOptions options);

        default boolean enabled() {
            return true;
        }
    }

    static class NoSslStrategy implements SslConfigurationStrategy {

        @Override
        public HttpServerOptions applyToHttpServerOptions(HttpServerOptions options) {
            return options.setSsl(false);
        }

        @Override
        public boolean enabled() {
            return false;
        }
    }

    static class KeyStoreStrategy implements SslConfigurationStrategy {

        private final Path path;
        private final String password;

        KeyStoreStrategy(Path path, String pass) throws SslConfigurationException {
            this.path = path;
            this.password = pass;

            if (!path.toString().endsWith(".jks")
                    && !path.toString().endsWith(".pfx")
                    && !path.toString().endsWith(".p12")) {
                throw new SslConfigurationException("unrecognized keystore type");
            }

            if (pass == null) {
                throw new SslConfigurationException("keystore password must not be null");
            }
        }

        @Override
        public HttpServerOptions applyToHttpServerOptions(HttpServerOptions options) {
            if (path.toString().endsWith(".jks")) {
                return options.setSsl(true)
                        .setKeyStoreOptions(
                                new JksOptions().setPath(path.toString()).setPassword(password));
            } else if (path.toString().endsWith(".pfx") || path.toString().endsWith(".p12")) {
                return options.setSsl(true)
                        .setPfxKeyCertOptions(
                                new PfxOptions().setPath(path.toString()).setPassword(password));
            }

            throw new IllegalStateException(); // extension checked in constructor. should never
            // reach this step
        }
    }

    static class KeyCertStrategy implements SslConfigurationStrategy {

        private final Path keyPath;
        private final Path certPath;

        KeyCertStrategy(Path keyPath, Path certPath) throws SslConfigurationException {
            this.keyPath = keyPath;
            this.certPath = certPath;

            if (!keyPath.toString().endsWith(".pem")) {
                throw new SslConfigurationException("unrecognized key file type");
            }
            if (!certPath.toString().endsWith(".pem")) {
                throw new SslConfigurationException("unrecognized certificate file type");
            }
        }

        @Override
        public HttpServerOptions applyToHttpServerOptions(HttpServerOptions options) {
            return options.setSsl(true)
                    .setPemKeyCertOptions(
                            new PemKeyCertOptions()
                                    .setKeyPath(keyPath.toString())
                                    .setCertPath(certPath.toString()));
        }
    }

    static class SslConfigurationException extends Exception {
        SslConfigurationException(String msg) {
            super(msg);
        }
    }
}
