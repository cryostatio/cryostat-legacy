package com.redhat.rhjmc.containerjfr.tui.ws;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.tui.ClientReader;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;

import io.vertx.core.Handler;
import io.vertx.core.http.ServerWebSocket;

class WsClientReaderWriter implements ClientReader, ClientWriter, Handler<String> {

    private final Semaphore semaphore = new Semaphore(0, true);
    private final Logger logger;
    private final Gson gson;
    private final BlockingQueue<String> inQ = new LinkedBlockingQueue<>();

    private final ServerWebSocket sws;
    private volatile Thread readingThread;

    WsClientReaderWriter(Logger logger, Gson gson, ServerWebSocket sws) {
        this.logger = logger;
        this.gson = gson;
        this.sws = sws;
        semaphore.release();
    }

    @Override
    public void handle(String msg) {
        logger.info(String.format("(%s): CMD %s", this.sws.remoteAddress().toString(), msg));
        inQ.add(msg);
    }

    @Override
    public void close() {
        if (semaphore.tryAcquire() && readingThread != null) {
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
                CompletableFuture<Void> future = new CompletableFuture<>();
                this.sws.writeTextMessage(
                        gson.toJson(message),
                        (res) -> {
                            if (res.failed()) {
                                future.completeExceptionally(res.cause());
                            } else {
                                future.complete(null);
                            }
                        });
                future.join();
            }
        } catch (InterruptedException e) {
            logger.warn(e);
        } catch (CompletionException e) {
            if (e.getCause() instanceof IllegalStateException) {
                logger.warn((IllegalStateException) e.getCause());
                return;
            }
            throw e;
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
