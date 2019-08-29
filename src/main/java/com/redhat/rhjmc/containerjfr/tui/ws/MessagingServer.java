package com.redhat.rhjmc.containerjfr.tui.ws;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

import com.google.gson.Gson;
import com.redhat.rhjmc.containerjfr.core.tui.ClientReader;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.core.log.Logger;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

class MessagingServer {

    private final Logger logger;
    private final Server server;
    private final Semaphore semaphore = new Semaphore(0, true);
    private final List<WsClientReaderWriter> connections = new ArrayList<>();

    MessagingServer(Logger logger, int listenPort, Gson gson) {
        this.logger = logger;
        this.server = new Server();
        ServerConnector connector = new ServerConnector(server);
        connector.setPort(listenPort);
        server.addConnector(connector);

        ServletContextHandler contextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        contextHandler.setContextPath("/");
        server.setHandler(contextHandler);
        contextHandler.addServlet(new ServletHolder(new MessagingServlet(this, logger, gson)), "/command");
    }

    // testing-only constructor
    MessagingServer(Logger logger, Server server) {
        this.logger = logger;
        this.server = server;
    }

    void start() throws Exception {
        server.start();
        server.dump(System.err);
    }

    void addConnection(WsClientReaderWriter crw) {
        connections.add(crw);
        semaphore.release();
    }

    @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED") // tryAcquire return value is irrelevant
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
                    logger.warn(ExceptionUtils.getStackTrace(e));
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
                logger.warn(ExceptionUtils.getStackTrace(e));
            }
        };
    }

}
