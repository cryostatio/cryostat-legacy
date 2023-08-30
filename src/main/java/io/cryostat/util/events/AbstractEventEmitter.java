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
package io.cryostat.util.events;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public abstract class AbstractEventEmitter<T extends EventType, V> {
    protected final Set<EventListener<T, V>> listeners =
            new CopyOnWriteArraySet<EventListener<T, V>>();

    public void addListener(EventListener<T, V> listener) {
        this.listeners.add(listener);
    }

    protected void emit(T eventType, V payload) {
        this.listeners.forEach(
                listener -> {
                    listener.onEvent(new Event<>(eventType, payload));
                });
    }
}
