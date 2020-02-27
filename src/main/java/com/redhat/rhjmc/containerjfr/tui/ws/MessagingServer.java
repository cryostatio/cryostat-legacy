package com.redhat.rhjmc.containerjfr.tui.ws;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.gson.Gson;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.core.tui.ClientReader;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.net.AuthManager;
import com.redhat.rhjmc.containerjfr.net.HttpServer;

class MessagingServer {

    static final String MAX_CONNECTIONS_ENV_VAR = "CONTAINER_JFR_MAX_WS_CONNECTIONS";
    static final int MIN_CONNECTIONS = 1;
    static final int MAX_CONNECTIONS = 64;
    static final int DEFAULT_MAX_CONNECTIONS = 2;

    private final int maxConnections;
    private final BlockingQueue<String> inQ = new LinkedBlockingQueue<>();
    private final Map<WsClientReaderWriter, ScheduledFuture<?>> connections = new HashMap<>();
    private final ScheduledExecutorService listenerPool;
    private final HttpServer server;
    private final AuthManager authManager;
    private final Logger logger;
    private final Gson gson;

    MessagingServer(
            HttpServer server, Environment env, AuthManager authManager, Logger logger, Gson gson) {
        this.server = server;
        this.authManager = authManager;
        this.logger = logger;
        this.gson = gson;
        this.maxConnections = determineMaximumWsConnections(env);
        this.listenerPool = Executors.newScheduledThreadPool(maxConnections);
    }

    void start() throws SocketException, UnknownHostException {
        server.start();
        logger.info(String.format("Max concurrent WebSocket connections: %d", maxConnections));

        server.websocketHandler(
                (sws) -> {
                    if (!"/command".equals(sws.path())) {
                        sws.reject(404);
                        return;
                    }
                    String remoteAddress = sws.remoteAddress().toString();
                    synchronized (connections) {
                        if (connections.size() >= maxConnections) {
                            logger.info(
                                    String.format(
                                            "Dropping remote client %s due to too many concurrent connections",
                                            remoteAddress));
                            sws.reject();
                            return;
                        }
                        logger.info(String.format("Connected remote client %s", remoteAddress));

                        WsClientReaderWriter crw =
                                new WsClientReaderWriter(this.logger, this.gson, sws);
                        sws.closeHandler(
                                (unused) -> {
                                    logger.info(
                                            String.format(
                                                    "Disconnected remote client %s",
                                                    remoteAddress));
                                    removeConnection(crw);
                                });
                        sws.textMessageHandler(
                                msg -> {
                                    try {
                                        String proto = sws.subProtocol();
                                        authManager
                                                .doAuthenticated(
                                                        () -> proto,
                                                        authManager::validateWebSocketSubProtocol)
                                                .onSuccess(() -> crw.handle(msg))
                                                // 1002: WebSocket "Protocol Error" close reason
                                                .onFailure(
                                                        () ->
                                                                sws.close(
                                                                        (short) 1002,
                                                                        String.format(
                                                                                "Invalid subprotocol \"%s\"",
                                                                                proto)))
                                                .execute();
                                    } catch (InterruptedException
                                            | ExecutionException
                                            | TimeoutException e) {
                                        logger.info(e);
                                        // 1011: WebSocket "Internal Error" close reason
                                        sws.close(
                                                (short) 1011,
                                                String.format(
                                                        "Internal error: \"%s\"", e.getMessage()));
                                    }
                                });
                        addConnection(crw);
                        sws.accept();
                    }
                });
    }

    void addConnection(WsClientReaderWriter crw) {
        synchronized (connections) {
            ScheduledFuture<?> task =
                    listenerPool.scheduleWithFixedDelay(
                            () -> {
                                String msg = crw.readLine();
                                if (msg != null) {
                                    inQ.add(msg);
                                }
                            },
                            0,
                            10,
                            TimeUnit.MILLISECONDS);
            connections.put(crw, task);
        }
    }

    void removeConnection(WsClientReaderWriter crw) {
        synchronized (connections) {
            ScheduledFuture<?> task = connections.remove(crw);
            if (task != null) {
                task.cancel(false);
                crw.close();
            }
        }
    }

    private void closeConnections() {
        synchronized (connections) {
            listenerPool.shutdown();
            connections.forEach((crw, task) -> crw.close());
            connections.clear();
        }
    }

    void flush(ResponseMessage<?> message) {
        synchronized (connections) {
            connections.forEach((c, t) -> c.flush(message));
        }
    }

    ClientReader getClientReader() {
        return new ClientReader() {
            @Override
            public void close() {
                closeConnections();
            }

            @Override
            public String readLine() {
                try {
                    return inQ.take();
                } catch (InterruptedException e) {
                    logger.warn(e);
                    return null;
                }
            }
        };
    }

    ClientWriter getClientWriter() {
        return new ClientWriter() {
            @Override
            public void print(String s) {
                logger.info(s);
            }

            @Override
            public void println(Exception e) {
                logger.warn(e);
            }
        };
    }

    private int determineMaximumWsConnections(Environment env) {
        try {
            int maxConn =
                    Integer.parseInt(
                            env.getEnv(
                                    MAX_CONNECTIONS_ENV_VAR,
                                    String.valueOf(DEFAULT_MAX_CONNECTIONS)));
            if (maxConn > MAX_CONNECTIONS) {
                logger.info(
                        String.format(
                                "Requested maximum WebSocket connections %d is too large.",
                                maxConn));
                return MAX_CONNECTIONS;
            }
            if (maxConn < MIN_CONNECTIONS) {
                logger.info(
                        String.format(
                                "Requested maximum WebSocket connections %d is too small.",
                                maxConn));
                return MIN_CONNECTIONS;
            }
            return maxConn;
        } catch (NumberFormatException nfe) {
            logger.warn(nfe);
            return DEFAULT_MAX_CONNECTIONS;
        }
    }
}
