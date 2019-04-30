package es.andrewazor.containertest.tui.ws;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;

import es.andrewazor.containertest.tui.ClientReader;
import es.andrewazor.containertest.tui.ClientWriter;

public class WsClientReaderWriter extends WebSocketAdapter implements ClientReader, ClientWriter {

    private final Semaphore semaphore = new Semaphore(0, true);
    private final MessagingServer server;
    private final BlockingQueue<String> inQ = new LinkedBlockingQueue<>();
    private volatile Thread readingThread;

    public WsClientReaderWriter(MessagingServer server) {
        this.server = server;
        this.server.setConnection(this);
    }

    @Override
    public void onWebSocketConnect(Session session) {
        super.onWebSocketConnect(session);
        semaphore.release();
    }

    @Override
    public void onWebSocketText(String text) {
        super.onWebSocketText(text);
        inQ.add(text);
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
        close();
        super.onWebSocketClose(statusCode, reason);
    }

    @Override
    public void close() {
        semaphore.drainPermits();
        if (isConnected()) {
            getSession().close();
        }
        if (readingThread != null) {
            readingThread.interrupt();
        }
    }

    @Override
    public void print(String s) {
        try {
            semaphore.acquireUninterruptibly();
            try {
                getRemote().sendString(s);
                getRemote().flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } finally {
            semaphore.release();
        }
    }

    @Override
    public String readLine() {
        try {
            readingThread = Thread.currentThread();
            return inQ.take();
        } catch (InterruptedException e) {
            readingThread = null;
            return null;
        }
    }
}