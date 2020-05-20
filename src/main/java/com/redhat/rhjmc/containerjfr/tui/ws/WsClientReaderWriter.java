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
