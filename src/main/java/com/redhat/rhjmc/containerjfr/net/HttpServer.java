package com.redhat.rhjmc.containerjfr.net;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.ServerWebSocket;

import java.util.concurrent.CompletableFuture;

public class HttpServer {

    private final NetworkConfiguration netConf;
    private final Logger logger;

    private final HandlerDelegate<HttpServerRequest> requestHandlerDelegate = new HandlerDelegate<>();
    private final HandlerDelegate<ServerWebSocket> websocketHandlerDelegate = new HandlerDelegate<>();

    private Vertx vertx;

    HttpServer(NetworkConfiguration netConf, Logger logger) {
        this.netConf = netConf;
        this.logger = logger;
    }

    public void start() {
        if (vertx != null) {
            return;
        }

        vertx = Vertx.vertx();

        CompletableFuture<Void> future = new CompletableFuture<>();
        vertx
                .createHttpServer(new HttpServerOptions()
                        .setCompressionSupported(true)
                        .setLogActivity(true)
                )
                .requestHandler(requestHandlerDelegate)
                .websocketHandler(websocketHandlerDelegate)
                .listen(netConf.getInternalWebServerPort(), res -> {
                    if (res.failed()) {
                        future.completeExceptionally(res.cause());
                        return;
                    }
                    future.complete(null);
                });

        future.join(); // wait for async deployment to complete
    }

    public void stop() {
        if (vertx == null) {
            return;
        }

        CompletableFuture<Void> future = new CompletableFuture<>();
        vertx.close(res -> {
            if (res.failed()) {
                future.completeExceptionally(res.cause());
                return;
            }

            future.complete(null);
        });
        future.join(); // wait for vertx to be closed

        vertx = null;
        requestHandlerDelegate.handler(null);
        websocketHandlerDelegate.handler(null);
    }

    public void requestHandler(Handler<HttpServerRequest> handler) {
        requestHandlerDelegate.handler(handler);
    }

    public void websocketHandler(Handler<ServerWebSocket> handler) {
        websocketHandlerDelegate.handler(handler);
    }

    public Vertx getVertx() {
        return vertx;
    }

    private class HandlerDelegate<T> implements Handler<T> {

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
