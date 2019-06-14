package com.redhat.rhjmc.containerjfr.tui.tcp;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Scanner;
import java.util.concurrent.Semaphore;

import com.redhat.rhjmc.containerjfr.tui.ClientReader;
import com.redhat.rhjmc.containerjfr.tui.ClientWriter;

class SocketClientReaderWriter implements ClientReader, ClientWriter {

    private final Thread listenerThread;
    private final ServerSocket ss;
    private final Semaphore semaphore = new Semaphore(0, true);
    private volatile Socket s;
    private volatile Scanner scanner;
    private volatile OutputStreamWriter writer;

    SocketClientReaderWriter(int port) throws IOException {
        ss = new ServerSocket(port);
        listenerThread = new Thread(() -> {
            System.out.println(String.format("Listening on port %d", port));
            while (true) {
                try {
                    Socket sock = ss.accept();
                    try {
                        close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    System.out.println(String.format("Connected: %s", sock.getRemoteSocketAddress().toString()));
                    try {
                        s = sock;
                        scanner = new Scanner(sock.getInputStream(), "utf-8");
                        writer = new OutputStreamWriter(sock.getOutputStream(), "utf-8");
                    } finally {
                        semaphore.release();
                    }
                } catch (SocketException e) {
                    semaphore.drainPermits();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        listenerThread.start();
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
        if (s != null) {
            s.close();
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
            e.printStackTrace();
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
            e.printStackTrace();
        }
    }

}