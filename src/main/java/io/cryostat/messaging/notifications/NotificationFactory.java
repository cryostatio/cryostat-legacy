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

import io.cryostat.recordings.JvmIdHelper;
import io.cryostat.recordings.JvmIdHelper.JvmIdGetException;

public class NotificationFactory {

    private final NotificationSource source;
    private final JvmIdHelper jvmIdHelper;

    NotificationFactory(NotificationSource source, JvmIdHelper jvmIdHelper) {
        this.source = source;
        this.jvmIdHelper = jvmIdHelper;
    }

    public <T> Notification.Builder<T> createBuilder() {
        return new Notification.Builder<T>(source);
    }

    public Notification.OwnedResourceBuilder createOwnedResourceBuilder(
            String notificationCategory) {
        return new Notification.OwnedResourceBuilder(source, notificationCategory);
    }

    public Notification.OwnedResourceBuilder createOwnedResourceBuilder(
            String targetId, String notificationCategory) throws JvmIdGetException {
        return new Notification.OwnedResourceBuilder(source, notificationCategory)
                .messageEntry("target", targetId)
                .messageEntry("jvmId", jvmIdHelper.getJvmId(targetId));
    }
}
