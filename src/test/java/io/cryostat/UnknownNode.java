package io.cryostat;

import io.cryostat.platform.discovery.AbstractNode;

public class UnknownNode extends AbstractNode {
    protected UnknownNode(AbstractNode other) {
        super(other);
    }

    @Override
    public String getName() {
        return "unknown";
    }
}
