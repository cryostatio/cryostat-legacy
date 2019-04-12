package es.andrewazor.containertest.tui;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.ReentrantLock;

class SocketClientReaderWriter implements ClientReader, ClientWriter {

    private final Thread listenerThread;
    private final ServerSocket ss;
    private final ReentrantLock writeLock = new ReentrantLock();
    private final ReentrantLock readLock = new ReentrantLock();
    private final CountDownLatch socketLatch = new CountDownLatch(1);
    private volatile Socket s;
    private volatile boolean running = true;
    private volatile Scanner scanner;
    private volatile OutputStreamWriter writer;

    SocketClientReaderWriter(int port) throws IOException {
        ss = new ServerSocket(port);
        listenerThread = new Thread(() -> {
            try {
                while (running) {
                    Socket sock = ss.accept();
                    System.out.println("Connected: " + sock.getRemoteSocketAddress().toString());
                    readLock.lock();
                    try {
                        writeLock.lock();
                        try {
                            s = sock;
                            scanner = new Scanner(sock.getInputStream(), "utf-8");
                            writer = new OutputStreamWriter(sock.getOutputStream(), "utf-8");
                        } finally {
                            writeLock.unlock();
                        }
                    } finally {
                        readLock.unlock();
                        socketLatch.countDown();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        listenerThread.start();
    }

    @Override
    public void close() throws IOException {
        running = false;
        scanner.close();
        writer.close();
        listenerThread.interrupt();
        ss.close();
        if (s != null) {
            s.close();
        }
    }

    @Override
    public String readLine() {
        try {
            socketLatch.await();
            readLock.lock();
            try {
                return scanner.nextLine();
            } finally {
                readLock.unlock();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public void print(String s) {
        try {
            socketLatch.await();
            writeLock.lock();
            try {
                writer.write(s);
                writer.flush();
            } finally {
                writeLock.unlock();
            }
        } catch (InterruptedException | IOException e) {
            e.printStackTrace();
        }
    }

}