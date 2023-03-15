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
package io.cryostat.platform.discovery;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import io.cryostat.platform.internal.CustomTargetPlatformClient;
import io.cryostat.platform.internal.DefaultPlatformClient;
import io.cryostat.platform.internal.KubeApiPlatformClient;
import io.cryostat.platform.internal.KubeEnvPlatformClient;

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
            } else if (name.equalsIgnoreCase(KubeApiPlatformClient.REALM)
                    || name.equalsIgnoreCase(KubeEnvPlatformClient.REALM)) {
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
