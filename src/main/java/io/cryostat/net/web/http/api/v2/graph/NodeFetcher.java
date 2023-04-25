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
package io.cryostat.net.web.http.api.v2.graph;

import java.util.EnumSet;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;

import javax.inject.Inject;

import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.platform.discovery.AbstractNode;
import io.cryostat.platform.discovery.EnvironmentNode;

import graphql.schema.DataFetchingEnvironment;

class NodeFetcher extends AbstractPermissionedDataFetcher<AbstractNode> {

    private final RootNodeFetcher rootNodeFetcher;

    @Inject
    NodeFetcher(AuthManager auth, RootNodeFetcher rootNodeFetcher) {
        super(auth);
        this.rootNodeFetcher = rootNodeFetcher;
    }

    @Override
    Set<String> applicableContexts() {
        return Set.of("EnvironmentNode");
    }

    @Override
    String name() {
        return "find";
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(ResourceAction.READ_TARGET);
    }

    @Override
    boolean blocking() {
        return false;
    }

    @Override
    public AbstractNode getAuthenticated(DataFetchingEnvironment environment) throws Exception {
        EnvironmentNode root = rootNodeFetcher.get(environment);
        String name = environment.getArgument("name");
        String nodeType = environment.getArgument("nodeType");
        AbstractNode node = findNode(name, nodeType, root);
        if (node == null) {
            throw new NoSuchElementException(String.format("%s named %s", nodeType, name));
        }
        return node;
    }

    static AbstractNode findNode(String name, String nodeType, AbstractNode root) {
        if (Objects.equals(name, root.getName())
                && root.getNodeType().getKind().equalsIgnoreCase(nodeType)) {
            return root;
        }
        if (root instanceof EnvironmentNode) {
            for (AbstractNode child : ((EnvironmentNode) root).getChildren()) {
                AbstractNode found = findNode(name, nodeType, child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
