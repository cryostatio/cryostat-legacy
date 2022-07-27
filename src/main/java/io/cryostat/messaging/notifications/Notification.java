/*
 * Copyright The Cryostat Authors
 *
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
 */
package io.cryostat.messaging.notifications;

import java.time.Instant;

import io.cryostat.messaging.MessagingServer;
import io.cryostat.messaging.WsMessage;
import io.cryostat.net.web.http.HttpMimeType;

public class Notification<T> extends WsMessage {

    private final transient MessagingServer server;
    private final transient NotificationSource source;

    private final Notification.Meta meta;
    private final T message;

    Notification(Notification.Builder<T> builder) {
        this.server = builder.server;
        this.source = builder.source;
        this.meta = new Meta(builder.category, builder.type);
        this.message = builder.message;
    }

    public void send() {
        this.server.writeMessage(this);
        this.source.notifyListeners(this);
    }

    public T getMessage() {
        return this.message;
    }

    public String getCategory() {
        return this.meta.category;
    }

    public static class Builder<T> {
        private final MessagingServer server;
        private final NotificationSource source;
        private String category = "generic";
        private MetaType type = new MetaType(HttpMimeType.JSON);
        private T message;

        Builder(MessagingServer server, NotificationSource source) {
            this.server = server;
            this.source = source;
        }

        public Builder<T> meta(Meta meta) {
            metaCategory(meta.category);
            metaType(meta.type);
            return this;
        }

        public Builder<T> metaCategory(String category) {
            this.category = category;
            return this;
        }

        public Builder<T> metaType(MetaType type) {
            this.type = type;
            return this;
        }

        public Builder<T> metaType(HttpMimeType mime) {
            return metaType(new MetaType(mime));
        }

        public Builder<T> message(T t) {
            this.message = t;
            return this;
        }

        public Notification<T> build() {
            return new Notification<>(this);
        }
    }

    public static class Meta {
        private final String category;
        private final MetaType type;
        private final long serverTime = Instant.now().getEpochSecond();

        public Meta(String category, MetaType type) {
            this.category = category;
            this.type = type;
        }
    }

    public static class MetaType {
        private final String type;
        private final String subType;

        public MetaType(HttpMimeType mimeType) {
            this(mimeType.type(), mimeType.subType());
        }

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
