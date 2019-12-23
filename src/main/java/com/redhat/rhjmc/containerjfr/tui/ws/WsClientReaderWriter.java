package com.redhat.rhjmc.containerjfr.tui.ws;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.tui.ClientReader;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;

import com.google.gson.Gson;
import io.vertx.core.Handler;
import io.vertx.core.http.ServerWebSocket;

class WsClientReaderWriter implements ClientReader, ClientWriter, Handler<String> {

    private final Logger logger;
    private final Gson gson;
    private final BlockingQueue<String> inQ = new LinkedBlockingQueue<>();
    private boolean running = true;

    private final ServerWebSocket sws;
    private volatile Thread readingThread;

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
        if (running && readingThread != null) {
            inQ.clear();
            readingThread.interrupt();
        }
        running = false;
    }

    @Override
    public void print(String s) {
        logger.info(s);
    }

    void flush(ResponseMessage<?> message) {
        if (!this.sws.isClosed()) {
            this.sws.writeTextMessage(gson.toJson(message));
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
