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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class EnvironmentNode extends AbstractNode {

    private List<AbstractNode> children;

    public EnvironmentNode(EnvironmentNode other) {
        this(other.name, other.nodeType, other.labels, other.children);
    }

    public EnvironmentNode(String name, NodeType nodeType) {
        this(name, nodeType, Collections.emptyMap());
    }

    public EnvironmentNode(String name, NodeType nodeType, Map<String, String> labels) {
        this(name, nodeType, labels, Collections.emptyList());
    }

    public EnvironmentNode(
            String name,
            NodeType nodeType,
            Map<String, String> labels,
            Collection<? extends AbstractNode> children) {
        super(name, nodeType, labels);
        this.children = new ArrayList<>(children);
        Collections.sort(this.children);
        this.id = hashCode();
    }

    public List<AbstractNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    public void addChildNode(AbstractNode child) {
        this.children.add(child);
        Collections.sort(this.children);
        this.id = hashCode();
    }

    public void addChildren(Collection<? extends AbstractNode> children) {
        this.children.addAll(children);
        Collections.sort(this.children);
        this.id = hashCode();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().appendSuper(super.hashCode()).append(children).build();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        if (o == this) {
            return true;
        }
        if (!(o instanceof EnvironmentNode)) {
            return false;
        }
        EnvironmentNode other = (EnvironmentNode) o;
        return new EqualsBuilder()
                .appendSuper(super.equals(o))
                .append(children, other.children)
                .isEquals();
    }
}
