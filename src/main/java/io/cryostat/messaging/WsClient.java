/*
 * Copyright The Cryostat Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.cryostat.messaging;

import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Clock;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.net.SocketAddress;
import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

class WsClient implements AutoCloseable {

    private final ServerWebSocket sws;
    private final long connectionTime;
    private volatile boolean isAccepted;
    private final Logger logger;

    WsClient(Logger logger, ServerWebSocket sws, Clock clock) {
        this.logger = logger;
        this.sws = sws;
        this.connectionTime = clock.getMonotonicTime();
    }

    void setAccepted() {
        this.isAccepted = true;
    }

    boolean isAccepted() {
        return isAccepted;
    }

    long getConnectionTime() {
        return connectionTime;
    }

    void writeMessage(String message) {
        if (isAccepted() && !this.sws.isClosed()) {
            WsMessageEmitted evt =
                    new WsMessageEmitted(
                            sws.remoteAddress().host(),
                            sws.remoteAddress().port(),
                            sws.uri(),
                            message.length());
            evt.begin();

            try {
                this.sws.writeTextMessage(message);

            } catch (Exception e) {
                logger.warn(e);
                evt.setExceptionThrown(true);

            } finally {
                evt.end();
                if (evt.shouldCommit()) {
                    evt.commit();
                }
            }
        }
    }

    SocketAddress getRemoteAddress() {
        return sws.remoteAddress();
    }

    void ping() {
        sws.writePing(Buffer.buffer("ping"));
    }

    @Override
    public void close() {
        if (!sws.isClosed()) {
            sws.textMessageHandler(null);
            sws.close();
        }
    }

    @Name("io.cryostat.messaging.WsClient.WsMessageEmitted")
    @Label("WebSocket Message Emitted")
    @Category("Cryostat")
    @SuppressFBWarnings(
            value = "URF_UNREAD_FIELD",
            justification = "Event fields are recorded with JFR instead of accessed directly")
    public static class WsMessageEmitted extends Event {
        String host;
        int port;
        String path;
        int msgLen;
        boolean exceptionThrown;

        public WsMessageEmitted(String host, int port, String path, int msgLen) {
            this.host = host;
            this.port = port;
            this.path = path;
            this.msgLen = msgLen;
            this.exceptionThrown = false;
        }

        public void setExceptionThrown(boolean exceptionThrown) {
            this.exceptionThrown = exceptionThrown;
        }
    }
}
