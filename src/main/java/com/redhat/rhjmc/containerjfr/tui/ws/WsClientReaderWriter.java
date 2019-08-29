package com.redhat.rhjmc.containerjfr.tui.ws;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.redhat.rhjmc.containerjfr.core.tui.ClientReader;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.core.log.Logger;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

class WsClientReaderWriter extends WebSocketAdapter implements ClientReader, ClientWriter {

    private Session session = null;
    private final Semaphore semaphore = new Semaphore(0, true);
    private final Logger logger;
    private final Gson gson;
    private final BlockingQueue<String> inQ = new LinkedBlockingQueue<>();
    private final MessagingServer server;
    private volatile Thread readingThread;

    WsClientReaderWriter(MessagingServer server, Logger logger, Gson gson) {
        this.server = server;
        this.logger = logger;
        this.gson = gson;
        this.server.addConnection(this);
    }

    @Override
    public void onWebSocketConnect(Session session) {
        super.onWebSocketConnect(session);
        logger.info(String.format("Connected remote client %s", session.getRemoteAddress().toString()));
        this.session = session;
        semaphore.release();
    }

    @Override
    public void onWebSocketText(String text) {
        super.onWebSocketText(text);
        logger.info(String.format("(%s): %s", session.getRemoteAddress().toString(), text));
        inQ.add(text);
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
        close();
    }

    @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED") // tryAcquire return value is irrelevant
    @Override
    public void close() {
        if (session != null) {
            logger.info(String.format("Disconnected remote client %s", session.getRemoteAddress().toString()));
        }
        semaphore.tryAcquire();
        this.session = null;
        if (isConnected()) {
            getSession().close();
        }
        super.onWebSocketClose(0, null);
        server.removeConnection(this);
        if (readingThread != null) {
            readingThread.interrupt();
        }
    }

    @Override
    public void print(String s) {
        logger.info(s);
    }

    void flush(ResponseMessage<?> message) {
        boolean acquired = false;
        try {
            acquired = semaphore.tryAcquire(3, TimeUnit.SECONDS);
            if (acquired) {
                getRemote().sendString(gson.toJson(message));
                getRemote().flush();
            }
        } catch (IOException | InterruptedException e) {
            logger.warn(ExceptionUtils.getStackTrace(e));
        } finally {
            if (acquired) {
                semaphore.release();
            }
        }
    }

    @Override
    public String readLine() {
        try {
            readingThread = Thread.currentThread();
            return inQ.take();
        } catch (InterruptedException e) {
            return null;
        } finally {
            readingThread = null;
        }
    }

    boolean hasMessage() {
        return !inQ.isEmpty();
    }
}
