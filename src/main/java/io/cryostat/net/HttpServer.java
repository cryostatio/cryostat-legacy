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
package io.cryostat.net;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;

import io.cryostat.core.log.Logger;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.ServerWebSocket;

public class HttpServer extends AbstractVerticle {

    private final NetworkConfiguration netConf;
    private final SslConfiguration sslConf;
    private final Set<Runnable> shutdownListeners;
    private final Logger logger;

    private final Vertx vertx;
    private final HandlerDelegate<HttpServerRequest> requestHandlerDelegate =
            new HandlerDelegate<>();
    private final HandlerDelegate<ServerWebSocket> websocketHandlerDelegate =
            new HandlerDelegate<>();

    private final io.vertx.core.http.HttpServer server;
    private volatile boolean isAlive;

    HttpServer(Vertx vertx, NetworkConfiguration netConf, SslConfiguration sslConf, Logger logger) {
        this.vertx = vertx;
        this.netConf = netConf;
        this.sslConf = sslConf;
        this.shutdownListeners = new HashSet<>();
        this.logger = logger;
        this.server =
                vertx.createHttpServer(
                        sslConf.applyToHttpServerOptions(
                                new HttpServerOptions()
                                        .setPort(netConf.getInternalWebServerPort())
                                        .addWebSocketSubProtocol("*")
                                        .setCompressionSupported(true)
                                        .setLogActivity(true)
                                        .setTcpFastOpen(true)
                                        .setTcpNoDelay(true)
                                        .setTcpQuickAck(true)));

        if (!sslConf.enabled()) {
            this.logger.warn("No available SSL certificates. Fallback to plain HTTP.");
        }
    }

    public void addShutdownListener(Runnable runnable) {
        this.shutdownListeners.add(runnable);
    }

    public void removeShutdownListener(Runnable runnable) {
        this.shutdownListeners.remove(runnable);
    }

    @Override
    public void start(Promise<Void> future) {
        if (isAlive()) {
            future.fail(new IllegalStateException("Already started"));
            return;
        }

        this.server
                .requestHandler(requestHandlerDelegate)
                .webSocketHandler(websocketHandlerDelegate)
                .listen(
                        res -> {
                            if (res.failed()) {
                                future.fail(res.cause());
                                return;
                            }

                            try {
                                logger.info(
                                        "{} service running on {}://{}:{}",
                                        isSsl() ? "HTTPS" : "HTTP",
                                        isSsl() ? "https" : "http",
                                        netConf.getWebServerHost(),
                                        netConf.getExternalWebServerPort());
                                this.isAlive = true;
                                future.complete();
                            } catch (SocketException | UnknownHostException e) {
                                future.fail(e);
                            }
                        });
    }

    @Override
    public void stop() {
        if (!isAlive()) {
            return;
        }
        this.shutdownListeners.forEach(Runnable::run);
    }

    public boolean isSsl() {
        return netConf.isSslProxied() || sslConf.enabled();
    }

    public void requestHandler(Handler<HttpServerRequest> handler) {
        requestHandlerDelegate.handler(handler);
    }

    public void websocketHandler(Handler<ServerWebSocket> handler) {
        websocketHandlerDelegate.handler(handler);
    }

    public boolean isAlive() {
        return isAlive;
    }

    @SuppressFBWarnings(value = "EI_EXPOSE_REP", justification = "Field is never mutated")
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
