package com.redhat.rhjmc.containerjfr.tui.ws;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.tui.ClientReader;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.net.AuthManager;
import com.redhat.rhjmc.containerjfr.net.HttpServer;

import com.google.gson.Gson;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

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

                    try {
                        authManager
                                .doAuthenticated(
                                        () ->
                                                URLEncodedUtils.parse(
                                                                sws.query(), StandardCharsets.UTF_8)
                                                        .stream()
                                                        .filter(p -> p.getName().equals("token"))
                                                        .findFirst()
                                                        .map(NameValuePair::getValue)
                                                        .orElse(null))
                                .onSuccess(
                                        () -> {
                                            logger.info(
                                                    String.format(
                                                            "Remote client %s passed token authentication",
                                                            remoteAddress));
                                            sws.accept();
                                            new WsClientReaderWriter(this, this.logger, this.gson)
                                                    .handle(sws);
                                        })
                                .onFailure(
                                        () -> {
                                            logger.info(
                                                    String.format(
                                                            "Remote client %s failed token authentication",
                                                            remoteAddress));
                                            sws.reject(401);
                                        })
                                .execute();
                    } catch (InterruptedException | ExecutionException | TimeoutException e) {
                        logger.error(e);
                        sws.reject(500);
                    }
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
        }
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
