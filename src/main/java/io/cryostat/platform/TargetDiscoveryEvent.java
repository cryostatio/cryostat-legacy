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
package io.cryostat.platform;

import java.util.Objects;

import io.cryostat.core.net.discovery.JvmDiscoveryClient.EventKind;

public class TargetDiscoveryEvent {
    private final EventKind kind;
    private final ServiceRef serviceRef;

    public TargetDiscoveryEvent(EventKind kind, ServiceRef serviceRef) {
        this.kind = kind;
        this.serviceRef = new ServiceRef(serviceRef);
    }

    public EventKind getEventKind() {
        return this.kind;
    }

    public ServiceRef getServiceRef() {
        return new ServiceRef(this.serviceRef);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, serviceRef);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        TargetDiscoveryEvent other = (TargetDiscoveryEvent) obj;
        return kind == other.kind && Objects.equals(serviceRef, other.serviceRef);
    }
}
