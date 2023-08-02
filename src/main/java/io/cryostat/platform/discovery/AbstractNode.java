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
package io.cryostat.platform.discovery;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.cryostat.platform.internal.CustomTargetPlatformClient;
import io.cryostat.platform.internal.DefaultPlatformClient;
import io.cryostat.platform.internal.KubeApiPlatformClient;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public abstract class AbstractNode implements Comparable<AbstractNode> {

    // this currently just holds a value from hashCode(). In the future it should be a database ID
    protected int id;

    protected String name;

    protected NodeType nodeType;

    protected Map<String, String> labels;

    protected AbstractNode(AbstractNode other) {
        this(other.name, other.nodeType, other.labels);
    }

    protected AbstractNode(String name, NodeType nodeType, Map<String, String> labels) {
        this.name = name;
        this.nodeType = nodeType;
        this.labels = new HashMap<>(labels);
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public NodeType getNodeType() {
        return nodeType;
    }

    public Map<String, String> getLabels() {
        return Collections.unmodifiableMap(labels);
    }

    @Override
    public int compareTo(AbstractNode other) {
        RealmOrder ro1 = getRealmOrder();
        RealmOrder ro2 = other.getRealmOrder();
        if (ro1 != null && ro2 != null) {
            return ro1.compareTo(ro2);
        } else {
            return name.compareTo(other.name);
        }
    }

    public RealmOrder getRealmOrder() {
        if (nodeType.getKind().equals(BaseNodeType.REALM.getKind())) {
            if (name.equalsIgnoreCase(DefaultPlatformClient.REALM)) {
                return RealmOrder.JDP;
            } else if (name.equalsIgnoreCase(KubeApiPlatformClient.REALM)) {
                return RealmOrder.KUBE;
            } else if (name.equalsIgnoreCase(CustomTargetPlatformClient.REALM)) {
                return RealmOrder.CUSTOM;
            } else {
                return RealmOrder.AGENT;
            }
        } else {
            return null;
        }
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(name).append(nodeType).append(labels).build();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        if (o == this) {
            return true;
        }
        if (!(o instanceof AbstractNode)) {
            return false;
        }
        AbstractNode other = (AbstractNode) o;
        return new EqualsBuilder()
                .append(name, other.name)
                .append(nodeType, other.nodeType)
                .append(labels, other.labels)
                .isEquals();
    }

    public enum RealmOrder {
        JDP,
        KUBE,
        AGENT,
        CUSTOM,
    }
}
