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
import java.util.Map;

import io.cryostat.platform.ServiceRef;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class TargetNode extends AbstractNode {

    private ServiceRef target;

    public TargetNode(TargetNode other) {
        this(other.nodeType, other.target, other.labels);
    }

    public TargetNode(NodeType nodeType, ServiceRef target) {
        this(nodeType, target, Collections.emptyMap());
    }

    public TargetNode(NodeType nodeType, ServiceRef target, Map<String, String> labels) {
        super(target.getServiceUri().toString(), nodeType, labels);
        this.target = new ServiceRef(target);
        this.id = hashCode();
    }

    public ServiceRef getTarget() {
        return new ServiceRef(target);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().appendSuper(super.hashCode()).append(target).build();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        if (o == this) {
            return true;
        }
        if (!(o instanceof TargetNode)) {
            return false;
        }
        TargetNode other = (TargetNode) o;
        return new EqualsBuilder()
                .appendSuper(super.equals(o))
                .append(target, other.target)
                .isEquals();
    }
}
