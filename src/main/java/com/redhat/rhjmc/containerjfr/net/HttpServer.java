package com.redhat.rhjmc.containerjfr.net;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PfxOptions;

import java.io.File;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.CompletableFuture;

public class HttpServer {

    private final NetworkConfiguration netConf;
    private final Logger logger;

    private final Vertx vertx;
    private final HandlerDelegate<HttpServerRequest> requestHandlerDelegate = new HandlerDelegate<>();
    private final HandlerDelegate<ServerWebSocket> websocketHandlerDelegate = new HandlerDelegate<>();
    private final boolean sslEnabled;

    private final io.vertx.core.http.HttpServer server;

    private static final String KEYSTORE_PATH_ENV = "KEYSTORE_PATH";
    private static final String KEYSTORE_PASS_ENV = "KEYSTORE_PASS";
    private static final String KEY_PATH_ENV = "KEY_PATH";
    private static final String CERT_PATH_ENV = "CERT_PATH";

    HttpServer(NetworkConfiguration netConf, Environment env, Logger logger) {
        this.netConf = netConf;
        this.logger = logger;
        this.vertx = Vertx.vertx();

        HttpServerOptions options = new HttpServerOptions()
                .setCompressionSupported(true)
                .setLogActivity(true);
        this.sslEnabled = setSslOptions(env, options);

        this.server = vertx.createHttpServer(options);
    }

    private boolean setSslOptions(Environment env, HttpServerOptions options) {
        String keystorePath = null;
        if (env.hasEnv(KEYSTORE_PATH_ENV)) {
            keystorePath = env.getEnv(KEYSTORE_PATH_ENV);
        } else if (new File(System.getProperty("user.home"), "container-jfr-keystore.jks").exists()) {
            keystorePath = System.getProperty("user.home") + File.separator + "container-jfr-keystore.jks";
        } else if (new File(System.getProperty("user.home"), "container-jfr-keystore.pfx").exists()) {
            keystorePath = System.getProperty("user.home") + File.separator + "container-jfr-keystore.pfx";
        } else if (new File(System.getProperty("user.home"), "container-jfr-keystore.p12").exists()) {
            keystorePath = System.getProperty("user.home") + File.separator + "container-jfr-keystore.p12";
        }

        String keystorePass = env.getEnv(KEYSTORE_PASS_ENV, "");
        if (keystorePath != null) {
            if (keystorePass.isEmpty()) {
                logger.warn("keystore password unset or empty");
            }

            if (keystorePath.endsWith(".jks")) {
                options.setSsl(true)
                        .setKeyStoreOptions(new JksOptions().setPath(keystorePath).setPassword(keystorePass));
                return true;
            } else if (keystorePath.endsWith(".pfx") || keystorePath.endsWith(".p12")) {
                options.setSsl(true)
                        .setPfxKeyCertOptions(new PfxOptions().setPath(keystorePath).setPassword(keystorePass));
                return true;
            }

            IllegalArgumentException e = new IllegalArgumentException("unrecognized keystore type");
            logger.error(e);
            throw e;
        }

        String keyPath = null;
        if (env.hasEnv(KEY_PATH_ENV)) {
            keyPath = env.getEnv(KEY_PATH_ENV);
        } else if (new File(System.getProperty("user.home"), "container-jfr-key.pem").exists()) {
            keyPath = System.getProperty("user.home") + File.separator + "container-jfr-key.pem";
        }

        String certPath = null;
        if (env.hasEnv(CERT_PATH_ENV)) {
            certPath = env.getEnv(CERT_PATH_ENV);
        } else if (new File(System.getProperty("user.home"), "container-jfr-cert.pem").exists()) {
            certPath = System.getProperty("user.home") + File.separator + "container-jfr-cert.pem";
        }

        if (keyPath != null && certPath != null) {
            options.setSsl(true)
                    .setPemKeyCertOptions(new PemKeyCertOptions().setKeyPath(keyPath).setCertPath(certPath));
            return true;
        } else if (keyPath != null ^ certPath != null) {
            IllegalArgumentException e = new IllegalArgumentException("both a key and a certificate are required");
            logger.error(e);
            throw e;
        }

        logger.warn("SSL parameters not set. Fallback to plain HTTP.");
        options.setSsl(false);
        return false;
    }

    public boolean getSslEnabled() {
        return sslEnabled;
    }

    public void start() throws SocketException, UnknownHostException {
        if (isAlive()) {
            return;
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        this.server
                .requestHandler(requestHandlerDelegate)
                .websocketHandler(websocketHandlerDelegate)
                .listen(netConf.getInternalWebServerPort(), netConf.getWebServerHost(), res -> {
                    if (res.failed()) {
                        future.completeExceptionally(res.cause());
                        return;
                    }
                    future.complete(null);
                });

        future.join(); // wait for async deployment to complete

        logger.info(String.format("%s service running on %s://%s:%d", 
                sslEnabled ? "HTTPS" : "HTTP", sslEnabled ? "https" : "http", 
                netConf.getWebServerHost(), netConf.getExternalWebServerPort()));
    }

    public void stop() {
        if (!isAlive()) {
            return;
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        this.server.close(res -> {
            if (res.failed()) {
                future.completeExceptionally(res.cause());
                return;
            }

            future.complete(null);
        });
        future.join(); // wait for vertx to be closed
    }

    public void requestHandler(Handler<HttpServerRequest> handler) {
        requestHandlerDelegate.handler(handler);
    }

    public void websocketHandler(Handler<ServerWebSocket> handler) {
        websocketHandlerDelegate.handler(handler);
    }

    public boolean isAlive() {
        return this.server.actualPort() != 0;
    } 

    public Vertx getVertx() {
        return vertx;
    }

    private static class HandlerDelegate<T> implements Handler<T> {

        private Handler<T> mHandler;

        @Override
        public final void handle(T event) {
            if (mHandler != null) {
                mHandler.handle(event);
            }
        }

        public HandlerDelegate<T> handler(Handler<T> handler) {
            mHandler = handler;

            return this;
        }
    }
}
