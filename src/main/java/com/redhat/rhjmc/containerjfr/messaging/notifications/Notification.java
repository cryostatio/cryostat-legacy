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
package com.redhat.rhjmc.containerjfr.messaging.notifications;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.messaging.MessagingServer;
import com.redhat.rhjmc.containerjfr.messaging.WsMessage;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings("URF_UNREAD_FIELD")
public class Notification<T> extends WsMessage implements AutoCloseable {

    private final transient MessagingServer server;
    private final transient Logger logger;
    private final transient AtomicBoolean sent = new AtomicBoolean(false);

    private final Notification.Meta meta;
    private T message;

    Notification(MessagingServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
        this.meta = new Notification.Meta();
    }

    public void setMetaType(MetaType type) {
        Objects.requireNonNull(type);
        this.meta.type =
                String.format(
                        "%s/%s",
                        type.getType().trim().toLowerCase(),
                        type.getSubType().trim().toLowerCase());
    }

    public void setMessage(T message) {
        this.message = message;
    }

    public void send() {
        if (!sent.getAndSet(true)) {
            logger.trace("Notification committed");
            this.server.writeMessage(this);
        }
    }

    @Override
    public void close() {
        send();
    }

    static class Meta {
        String type = "string";
        private final long serverTime = Instant.now().getEpochSecond();
    }

    public static class MetaType {
        private final String type;
        private final String subType;

        public MetaType(String type, String subType) {
            this.type = type;
            this.subType = subType;
        }

        public String getType() {
            return type;
        }

        public String getSubType() {
            return subType;
        }
    }
}
