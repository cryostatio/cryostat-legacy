package com.redhat.rhjmc.containerjfr.net;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.ServerWebSocket;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.CompletableFuture;

public class HttpServer {

    private final NetworkConfiguration netConf;
    private final SslConfiguration sslConf;
    private final Logger logger;

    private final Vertx vertx;
    private final HandlerDelegate<HttpServerRequest> requestHandlerDelegate = new HandlerDelegate<>();
    private final HandlerDelegate<ServerWebSocket> websocketHandlerDelegate = new HandlerDelegate<>();

    private final io.vertx.core.http.HttpServer server;

    HttpServer(NetworkConfiguration netConf, SslConfiguration sslConf, Logger logger) {
        this.netConf = netConf;
        this.sslConf = sslConf;
        this.logger = logger;
        this.vertx = Vertx.vertx();
        this.server = vertx.createHttpServer(sslConf.applyToHttpServerOptions(new HttpServerOptions()
                .setPort(netConf.getInternalWebServerPort())
                .setCompressionSupported(true)
                .setLogActivity(true)));

        if (!sslConf.enabled()) {
            this.logger.warn("No available SSL certificates. Fallback to plain HTTP.");
        }
    }

    public void start() throws SocketException, UnknownHostException {
        if (isAlive()) {
            return;
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        this.server
                .requestHandler(requestHandlerDelegate)
                .websocketHandler(websocketHandlerDelegate)
                .listen(res -> {
                    if (res.failed()) {
                        future.completeExceptionally(res.cause());
                        return;
                    }
                    future.complete(null);
                });

        future.join(); // wait for async deployment to complete

        logger.info(String.format("%s service running on %s://%s:%d",
                isSsl() ? "HTTPS" : "HTTP", isSsl() ? "https" : "http", 
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

    public boolean isSsl() {
        return sslConf.enabled();
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
