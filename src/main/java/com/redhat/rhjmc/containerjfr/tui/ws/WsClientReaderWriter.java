package com.redhat.rhjmc.containerjfr.tui.ws;

import java.util.concurrent.*;

import com.google.gson.Gson;
import com.redhat.rhjmc.containerjfr.core.tui.ClientReader;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.core.log.Logger;

import io.vertx.core.Handler;
import io.vertx.core.http.ServerWebSocket;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

class WsClientReaderWriter implements ClientReader, ClientWriter, Handler<ServerWebSocket> {

    private final Semaphore semaphore = new Semaphore(0, true);
    private final Logger logger;
    private final Gson gson;
    private final BlockingQueue<String> inQ = new LinkedBlockingQueue<>();
    private final MessagingServer server;

    private ServerWebSocket sws;
    private volatile Thread readingThread;

    WsClientReaderWriter(MessagingServer server, Logger logger, Gson gson) {
        this.server = server;
        this.logger = logger;
        this.gson = gson;
        this.server.addConnection(this);
    }

    @Override
    public void handle(ServerWebSocket sws) {
        this.sws = sws;
        semaphore.release();
        logger.info(String.format("Connected remote client %s", this.sws.remoteAddress().toString()));

        sws.textMessageHandler(this::handleTextMessage);
        sws.closeHandler((unused) -> close());
    }

    void handleTextMessage(String msg) {
        logger.info(String.format("(%s): CMD %s", this.sws.remoteAddress().toString(), msg));
        inQ.add(msg);
    }

    @SuppressFBWarnings("RV_RETURN_VALUE_IGNORED") // tryAcquire return value is irrelevant
    @Override
    public void close() {
        logger.info(String.format("Disconnected remote client %s", this.sws.remoteAddress().toString()));

        semaphore.tryAcquire();
        if (!this.sws.isClosed()) {
            CompletableFuture<Void> future = new CompletableFuture<>();
            this.sws.close((res) -> {
                if (res.failed()) {
                    future.completeExceptionally(res.cause());
                } else {
                    future.complete(null);
                }
            });
            future.join();
        }
        this.sws = null;

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
                CompletableFuture<Void> future = new CompletableFuture<>();
                this.sws.writeTextMessage(gson.toJson(message), (res) -> {
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
