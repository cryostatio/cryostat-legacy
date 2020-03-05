package com.redhat.rhjmc.containerjfr.tui.ws;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.google.gson.Gson;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.tui.ClientReader;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;

import io.vertx.core.Handler;
import io.vertx.core.http.ServerWebSocket;

class WsClientReaderWriter implements ClientReader, ClientWriter, Handler<String> {

    private final Logger logger;
    private final Gson gson;
    private final BlockingQueue<String> inQ = new LinkedBlockingQueue<>();

    private final ServerWebSocket sws;
    private final Object threadLock = new Object();
    private Thread readingThread;

    WsClientReaderWriter(Logger logger, Gson gson, ServerWebSocket sws) {
        this.logger = logger;
        this.gson = gson;
        this.sws = sws;
    }

    @Override
    public void handle(String msg) {
        logger.info(String.format("(%s): CMD %s", this.sws.remoteAddress().toString(), msg));
        inQ.add(msg);
    }

    @Override
    public void close() {
        inQ.clear();
        synchronized (threadLock) {
            if (readingThread != null) {
                readingThread.interrupt();
            }
        }
    }

    @Override
    public void print(String s) {
        logger.info(s);
    }

    void flush(ResponseMessage<?> message) {
        if (!this.sws.isClosed()) {
            try {
                this.sws.writeTextMessage(gson.toJson(message));
            } catch (Exception e) {
                logger.warn(e);
            }
        }
    }

    @Override
    public String readLine() {
        try {
            synchronized (threadLock) {
                readingThread = Thread.currentThread();
            }
            return inQ.take();
        } catch (InterruptedException e) {
            return null;
        } finally {
            synchronized (threadLock) {
                readingThread = null;
            }
        }
    }
}
