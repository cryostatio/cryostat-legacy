package com.redhat.rhjmc.containerjfr.net;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PfxOptions;
import org.openjdk.jmc.common.util.Pair;

import java.nio.file.Path;

class SslConfiguration {
    private final Environment env;
    private final FileSystem fs;
    private final Logger logger;

    private final Path keystorePath;
    private final String keystorePass;

    private final Path keyPath;
    private final Path certPath;

    private static final String KEYSTORE_PATH_ENV = "KEYSTORE_PATH";
    private static final String KEYSTORE_PASS_ENV = "KEYSTORE_PASS";
    private static final String KEY_PATH_ENV = "KEY_PATH";
    private static final String CERT_PATH_ENV = "CERT_PATH";

    SslConfiguration(Environment env, FileSystem fs, Logger logger) throws IllegalArgumentException {
        this.env = env;
        this.fs = fs;
        this.logger = logger;

        {
            Path path = obtainKeyStorePathIfSpecified();
            if (path != null) {
                keystorePath = path;
                keystorePass = env.getEnv(KEYSTORE_PASS_ENV, "");
                keyPath = null;
                certPath = null;
                return;
            }    
        }

        {
            Pair<Path, Path> pair = obtainKeyCertPathPairIfSpecified();
            if (pair != null) {
                keystorePath = null;
                keystorePass = null;
                keyPath = pair.left;
                certPath = pair.right;
                return;
            }
        }

        {
            Path path = discoverKeyStorePathInDefaultLocations();
            if (path != null) {
                keystorePath = path;
                keystorePass = env.getEnv(KEYSTORE_PASS_ENV, "");
                keyPath = null;
                certPath = null;
                return;
            }    
        }

        {
            Pair<Path, Path> pair = discoverKeyCertPathPairInDefaultLocations();
            if (pair != null) {
                keystorePath = null;
                keystorePass = null;
                keyPath = pair.left;
                certPath = pair.right;
                return;
            }
        }

        keystorePath = null;
        keystorePass = null;
        keyPath = null;
        certPath = null;
        logger.warn("No available SSL certificates. Fallback to plain HTTP.");
    }

    Path obtainKeyStorePathIfSpecified() throws IllegalArgumentException {
        if (!env.hasEnv(KEYSTORE_PATH_ENV)) {
            return null;
        }

        Path path = Path.of(env.getEnv(KEYSTORE_PATH_ENV)).normalize();
        if (!fs.exists(keystorePath)) {
            throw new IllegalArgumentException("KEYSTORE_PATH refers to a non-existent file");
        }

        return path;
    }

    Pair<Path, Path> obtainKeyCertPathPairIfSpecified() throws IllegalArgumentException {
        if (!env.hasEnv(KEY_PATH_ENV) && !env.hasEnv(CERT_PATH_ENV)) {
            return null;
        }

        if (env.hasEnv(KEY_PATH_ENV) ^ env.hasEnv(CERT_PATH_ENV)) {
            throw new IllegalArgumentException("both KEY_PATH and CERT_PATH must be specified");
        }

        Path key = Path.of(env.getEnv(KEY_PATH_ENV)).normalize();
        Path cert = Path.of(env.getEnv(CERT_PATH_ENV)).normalize();
        if (!fs.exists(key)) {
            throw new IllegalArgumentException("KEY_PATH refers to a non-existent file");
        }

        if (!fs.exists(cert)) {
            throw new IllegalArgumentException("CERT_PATH refers to a non-existent file");
        }

        return new Pair<>(key, cert);
    }

    Path discoverKeyStorePathInDefaultLocations() {
        Path home = Path.of(System.getProperty("user.home", "/"));

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
        Path home = Path.of(System.getProperty("user.home", "/"));
        Pair<Path, Path> ret = new Pair<>(null, null);

        // discover keyPath
        if (fs.exists(home.resolve("container-jfr-key.pem"))) {
            ret.left = home.resolve("container-jfr-key.pem");
        } else {
            logger.warn("unable to locate container-jfr-key.pem");
        }

        // discover certPath
        if (fs.exists(home.resolve("container-jfr-cert.pem"))) {
            ret.right = home.resolve("container-jfr-cert.pem");
        } else {
            logger.warn("unable to locate container-jfr-cert.pem");
        }

        if (keyPath == null || certPath == null) {
            return null;
        }

        return ret;
    }

    HttpServerOptions setToHttpServerOptions(HttpServerOptions options) {
        if (!enabled()) {
            return options.setSsl(false);
        }

        options.setSsl(true);

        if (keystorePath != null) {
            if (keystorePass.isEmpty()) {
                logger.warn("keystore password unset or empty");
            }

            if (keystorePath.endsWith(".jks")) {
                return options
                        .setKeyStoreOptions(new JksOptions().setPath(keystorePath.toString()).setPassword(keystorePass));
            } else if (keystorePath.endsWith(".pfx") || keystorePath.endsWith(".p12")) {
                return options
                        .setPfxKeyCertOptions(new PfxOptions().setPath(keystorePath.toString()).setPassword(keystorePass));
            }

            IllegalArgumentException e = new IllegalArgumentException("unrecognized keystore type");
            logger.error(e);
            throw e;
        }

        if (keyPath != null && certPath != null) {
            if (!keyPath.endsWith(".pem")) {
                IllegalArgumentException e = new IllegalArgumentException("unrecognized key file type");
                logger.error(e);
                throw e;
            }
            if (!certPath.endsWith(".pem")) {
                IllegalArgumentException e = new IllegalArgumentException("unrecognized certificate file type");
                logger.error(e);
                throw e;
            }

            return options
                    .setPemKeyCertOptions(new PemKeyCertOptions().setKeyPath(keyPath.toString()).setCertPath(certPath.toString()));
        }

        return options;
    }

    boolean enabled() {
        return keystorePath != null || (certPath != null && keyPath != null);
    }
}
