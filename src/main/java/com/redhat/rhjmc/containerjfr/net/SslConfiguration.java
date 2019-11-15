package com.redhat.rhjmc.containerjfr.net;

import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PfxOptions;
import org.apache.commons.lang3.tuple.Pair;

import java.nio.file.Path;

class SslConfiguration {
    private final Environment env;
    private final FileSystem fs;

    private final SslConfigurationStrategy strategy;

    private static final String KEYSTORE_PATH_ENV = "KEYSTORE_PATH";
    private static final String KEYSTORE_PASS_ENV = "KEYSTORE_PASS";
    private static final String KEY_PATH_ENV = "KEY_PATH";
    private static final String CERT_PATH_ENV = "CERT_PATH";

    SslConfiguration(Environment env, FileSystem fs) throws SslConfigurationException {
        this.env = env;
        this.fs = fs;

        {
            Path path = obtainKeyStorePathIfSpecified();
            if (path != null) {
                strategy = keyStoreStrategy(path, env.getEnv(KEYSTORE_PASS_ENV, ""));
                return;
            }    
        }

        {
            Pair<Path, Path> pair = obtainKeyCertPathPairIfSpecified();
            if (pair != null) {
                strategy = keyCertStrategy(pair.getLeft(), pair.getRight());
                return;
            }
        }

        {
            Path path = discoverKeyStorePathInDefaultLocations();
            if (path != null) {
                strategy = keyStoreStrategy(path, env.getEnv(KEYSTORE_PASS_ENV, ""));
                return;
            }    
        }

        {
            Pair<Path, Path> pair = discoverKeyCertPathPairInDefaultLocations();
            if (pair != null) {
                strategy = keyCertStrategy(pair.getLeft(), pair.getRight());
                return;
            }
        }

        strategy = noSslStrategy();
    }

    // Test-only constructor
    SslConfiguration(Environment env, FileSystem fs, SslConfigurationStrategy strategy) {
        this.env = env;
        this.fs = fs;
        this.strategy = strategy;
    }
    
    Path obtainKeyStorePathIfSpecified() throws SslConfigurationException {
        if (!env.hasEnv(KEYSTORE_PATH_ENV)) {
            return null;
        }

        Path path = fs.pathOf(env.getEnv(KEYSTORE_PATH_ENV)).normalize();
        if (!fs.exists(path)) {
            throw new SslConfigurationException("KEYSTORE_PATH refers to a non-existent file");
        }

        return path;
    }

    Pair<Path, Path> obtainKeyCertPathPairIfSpecified() throws SslConfigurationException {
        if (!env.hasEnv(KEY_PATH_ENV) && !env.hasEnv(CERT_PATH_ENV)) {
            return null;
        }

        if (env.hasEnv(KEY_PATH_ENV) ^ env.hasEnv(CERT_PATH_ENV)) {
            throw new SslConfigurationException("both KEY_PATH and CERT_PATH must be specified");
        }

        Path key = fs.pathOf(env.getEnv(KEY_PATH_ENV)).normalize();
        Path cert = fs.pathOf(env.getEnv(CERT_PATH_ENV)).normalize();
        if (!fs.exists(key)) {
            throw new SslConfigurationException("KEY_PATH refers to a non-existent file");
        }

        if (!fs.exists(cert)) {
            throw new SslConfigurationException("CERT_PATH refers to a non-existent file");
        }

        return Pair.of(key, cert);
    }

    Path discoverKeyStorePathInDefaultLocations() {
        Path home = fs.pathOf(System.getProperty("user.home", "/"));

        if (fs.exists(home.resolve("container-jfr-keystore.jks"))) {
            return home.resolve("container-jfr-keystore.jks");
        } else if (fs.exists(home.resolve("container-jfr-keystore.pfx"))) {
            return home.resolve("container-jfr-keystore.pfx");
        } else if (fs.exists(home.resolve("container-jfr-keystore.p12"))) {
            return home.resolve("container-jfr-keystore.p12");
        }

        return null;
    }

    Pair<Path, Path> discoverKeyCertPathPairInDefaultLocations() {
        Path home = fs.pathOf(System.getProperty("user.home", "/"));
        Path key = null;
        Path cert = null;

        // discover keyPath
        if (fs.exists(home.resolve("container-jfr-key.pem"))) {
            key = home.resolve("container-jfr-key.pem");
        }

        // discover certPath
        if (fs.exists(home.resolve("container-jfr-cert.pem"))) {
            cert = home.resolve("container-jfr-cert.pem");
        }

        if (key == null || cert == null) {
            return null;
        }

        return Pair.of(key, cert);
    }

    HttpServerOptions applyToHttpServerOptions(HttpServerOptions options) {
        return strategy.applyToHttpServerOptions(options);
    }

    boolean enabled() {
        return strategy.enabled();
    }

    SslConfigurationStrategy keyStoreStrategy(Path path, String password) throws SslConfigurationException {
        return new KeyStoreStrategy(path, password);
    }

    SslConfigurationStrategy keyCertStrategy(Path keyPath, Path certPath) throws SslConfigurationException {
        return new KeyCertStrategy(keyPath, certPath);
    }
    
    SslConfigurationStrategy noSslStrategy() {
        return new NoSslStrategy();
    }

    interface SslConfigurationStrategy {
        HttpServerOptions applyToHttpServerOptions(HttpServerOptions options);
        
        default boolean enabled() {
            return !(this instanceof NoSslStrategy);
        }
    }
    
    static class NoSslStrategy implements SslConfigurationStrategy {

        @Override
        public HttpServerOptions applyToHttpServerOptions(HttpServerOptions options) {
            return options.setSsl(false);
        }
    }

    static class KeyStoreStrategy implements SslConfigurationStrategy {

        private final Path path;
        private final String password;

        KeyStoreStrategy(Path path, String pass) throws SslConfigurationException {
            this.path = path;
            this.password = pass;
            
            if (!path.toString().endsWith(".jks") && !path.toString().endsWith(".pfx") && !path.toString().endsWith(".p12")) {
                throw new SslConfigurationException("unrecognized keystore type");
            }

            if (pass == null) {
                throw new SslConfigurationException("keystore password must not be null");
            }
        }

        @Override
        public HttpServerOptions applyToHttpServerOptions(HttpServerOptions options){
            if (path.toString().endsWith(".jks")) {
                return options.setSsl(true)
                        .setKeyStoreOptions(new JksOptions().setPath(path.toString()).setPassword(password));
            } else if (path.toString().endsWith(".pfx") || path.toString().endsWith(".p12")) {
                return options.setSsl(true)
                        .setPfxKeyCertOptions(new PfxOptions().setPath(path.toString()).setPassword(password));
            }

            throw new IllegalStateException(); // extension checked in constructor. should never reach this step
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
                    .setPemKeyCertOptions(new PemKeyCertOptions().setKeyPath(keyPath.toString()).setCertPath(certPath.toString()));
        }
    }

    static class SslConfigurationException extends Exception {
        SslConfigurationException(String msg) {
            super(msg);
        }
    }
}
