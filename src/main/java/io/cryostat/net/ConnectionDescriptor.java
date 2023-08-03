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
package io.cryostat.net;

import java.util.Optional;

import io.cryostat.core.net.Credentials;
import io.cryostat.platform.ServiceRef;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class ConnectionDescriptor {

    private final String targetId;
    private final Optional<Credentials> credentials;

    public ConnectionDescriptor(ServiceRef serviceRef) {
        this(serviceRef.getServiceUri().toString());
    }

    public ConnectionDescriptor(String targetId) {
        this(targetId, null);
    }

    public ConnectionDescriptor(ServiceRef serviceRef, Credentials credentials) {
        this(serviceRef.getServiceUri().toString(), credentials);
    }

    public ConnectionDescriptor(String targetId, Credentials credentials) {
        this.targetId = targetId;
        this.credentials = Optional.ofNullable(credentials);
    }

    public String getTargetId() {
        return targetId;
    }

    public Optional<Credentials> getCredentials() {
        return credentials;
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }
        if (other == this) {
            return true;
        }
        if (!(other instanceof ConnectionDescriptor)) {
            return false;
        }
        ConnectionDescriptor cd = (ConnectionDescriptor) other;
        return new EqualsBuilder()
                .append(targetId, cd.targetId)
                .append(credentials, cd.credentials)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(targetId).append(credentials).hashCode();
    }
}
