/*-
 * #%L
 * Container JFR
 * %%
 * Copyright (C) 2020 Red Hat, Inc.
 * %%
 * The Universal Permissive License (UPL), Version 1.0
 * 
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 * 
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software (each a "Larger Work" to which the Software
 * is contributed by such licensors),
 * 
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 * 
 * This license is subject to the following condition:
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * #L%
 */
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
        listenerThread =
                new Thread(
                        () -> {
                            System.out.println(String.format("Listening on port %d", port));
                            while (true) {
                                try {
                                    Socket sock = serverSocket.accept();
                                    try {
                                        close();
                                    } catch (IOException e) {
                                        logger.warn(e);
                                    }
                                    System.out.println(
                                            String.format(
                                                    "Connected: %s",
                                                    sock.getRemoteSocketAddress().toString()));
                                    try {
                                        socket = sock;
                                        scanner =
                                                new Scanner(
                                                        sock.getInputStream(),
                                                        StandardCharsets.UTF_8);
                                        writer =
                                                new OutputStreamWriter(
                                                        sock.getOutputStream(),
                                                        StandardCharsets.UTF_8);
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
    SocketClientReaderWriter(
            Logger logger,
            Semaphore semaphore,
            Socket socket,
            Scanner scanner,
            OutputStreamWriter writer) {
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
