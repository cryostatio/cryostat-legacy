package com.redhat.rhjmc.containerjfr.tui.tcp;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.Semaphore;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.tui.ClientReader;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;

class SocketClientReaderWriter implements ClientReader, ClientWriter {

    private final Logger logger;
    private final Thread listenerThread;
    private final ServerSocket serverSocket;
    private final Semaphore semaphore;
    private volatile Socket socket;
    private volatile Scanner scanner;
    private volatile OutputStreamWriter writer;

    SocketClientReaderWriter(Logger logger, int port) throws IOException {
        this.logger = logger;
        serverSocket = new ServerSocket(port);
        semaphore = new Semaphore(0, true);
        listenerThread = new Thread(() -> {
            System.out.println(String.format("Listening on port %d", port));
            while (true) {
                try {
                    Socket sock = serverSocket.accept();
                    try {
                        close();
                    } catch (IOException e) {
                        logger.warn(e);
                    }
                    System.out.println(String.format("Connected: %s", sock.getRemoteSocketAddress().toString()));
                    try {
                        socket = sock;
                        scanner = new Scanner(sock.getInputStream(), StandardCharsets.UTF_8);
                        writer = new OutputStreamWriter(sock.getOutputStream(), StandardCharsets.UTF_8);
                    } finally {
                        semaphore.release();
                    }
                } catch (SocketException e) {
                    semaphore.drainPermits();
                } catch (IOException e) {
                    logger.warn(e);
                }
            }
        });
        listenerThread.start();
    }

    // Testing-only constructor
    SocketClientReaderWriter(Logger logger, Semaphore semaphore, Socket socket, Scanner scanner, OutputStreamWriter writer) {
        this.logger = logger;
        this.semaphore = semaphore;
        this.scanner = scanner;
        this.writer = writer;
        this.listenerThread = null;
        this.serverSocket = null;
        this.socket = socket;
    }

    @Override
    public void close() throws IOException {
        semaphore.drainPermits();
        if (scanner != null) {
            scanner.close();
        }
        if (writer != null) {
            writer.close();
        }
        if (socket != null) {
            socket.close();
        }
    }

    @Override
    public String readLine() {
        try {
            semaphore.acquire();
            try {
                return scanner.nextLine();
            } finally {
                semaphore.release();
            }
        } catch (InterruptedException e) {
            logger.warn(e);
            return null;
        }
    }

    @Override
    public void print(String s) {
        try {
            semaphore.acquire();
            try {
                writer.write(s);
                writer.flush();
            } finally {
                semaphore.release();
            }
        } catch (InterruptedException | IOException e) {
            logger.warn(e);
        }
    }

}
