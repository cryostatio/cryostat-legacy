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
package io.cryostat.messaging.notifications;

import java.time.Instant;

import io.cryostat.net.web.http.HttpMimeType;

public class Notification<T> {

    private final transient NotificationSource source;

    private final Notification.Meta meta;
    private final T message;

    Notification(Notification.Builder<T> builder) {
        this.source = builder.source;
        this.meta = new Meta(builder.category, builder.type);
        this.message = builder.message;
    }

    public void send() {
        this.source.notifyListeners(this);
    }

    public T getMessage() {
        return this.message;
    }

    public String getCategory() {
        return this.meta.category;
    }

    public static class Builder<T> {
        private final NotificationSource source;
        private String category = "generic";
        private MetaType type = new MetaType(HttpMimeType.JSON);
        private T message;

        Builder(NotificationSource source) {
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
