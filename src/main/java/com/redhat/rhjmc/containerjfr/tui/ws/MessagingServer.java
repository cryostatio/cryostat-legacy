package com.redhat.rhjmc.containerjfr.tui.ws;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.tui.ClientReader;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.net.AuthManager;
import com.redhat.rhjmc.containerjfr.net.HttpServer;

import com.google.gson.Gson;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

class MessagingServer {

    private final HttpServer server;
    private final AuthManager authManager;
    private final Logger logger;
    private final Gson gson;
    private final Semaphore semaphore = new Semaphore(0, true);
    private final List<WsClientReaderWriter> connections = new ArrayList<>();

    MessagingServer(HttpServer server, AuthManager authManager, Logger logger, Gson gson) {
        this.server = server;
        this.authManager = authManager;
        this.logger = logger;
        this.gson = gson;
    }

    void start() throws SocketException, UnknownHostException {
        server.start();

        server.websocketHandler(
                (sws) -> {
                    if (!"/command".equals(sws.path())) {
                        sws.reject(404);
                        return;
                    }
                    String remoteAddress = sws.remoteAddress().toString();
                    logger.info(String.format("Connected remote client %s", remoteAddress));

                    WsClientReaderWriter crw =
                            new WsClientReaderWriter(this.logger, this.gson, sws);
                    sws.closeHandler(
                            (unused) -> {
                                logger.info(
                                        String.format(
                                                "Disconnected remote client %s", remoteAddress));
                                removeConnection(crw);
                            });
                    sws.textMessageHandler(
                            msg -> {
                                try {
                                    String proto = sws.subProtocol();
                                    authManager
                                            .doAuthenticated(
                                                    () -> getAuthTokenFromSubprotocol(proto))
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
                });
    }

    void addConnection(WsClientReaderWriter crw) {
        connections.add(crw);
        semaphore.release();
    }

    @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED")
    // tryAcquire return value is irrelevant
    void removeConnection(WsClientReaderWriter crw) {
        if (connections.remove(crw)) {
            semaphore.tryAcquire();
            crw.close();
        }
    }

    private String getAuthTokenFromSubprotocol(String subprotocol) {
        if (subprotocol == null) {
            return null;
        }
        Pattern pattern =
                Pattern.compile(
                        "base64url\\.bearer\\.authorization\\.containerjfr\\.([\\S]+)",
                        Pattern.CASE_INSENSITIVE);
        Matcher m = pattern.matcher(subprotocol);
        if (!m.matches()) {
            return null;
        }
        return m.group(1);
    }

    private void closeConnections() {
        semaphore.drainPermits();
        connections.forEach(WsClientReaderWriter::close);
        connections.clear();
    }

    void flush(ResponseMessage<?> message) {
        final int permits = Math.max(1, connections.size());
        try {
            semaphore.acquireUninterruptibly(permits);
            connections.forEach(c -> c.flush(message));
        } finally {
            semaphore.release(permits);
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
                final int permits = Math.max(1, connections.size());
                try {
                    semaphore.acquire(permits);
                    while (true) {
                        for (WsClientReaderWriter crw : connections) {
                            if (crw.hasMessage()) {
                                return crw.readLine();
                            }
                        }
                        Thread.sleep(100);
                    }
                } catch (InterruptedException e) {
                    logger.warn(e);
                    return null;
                } finally {
                    semaphore.release(permits);
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
}
