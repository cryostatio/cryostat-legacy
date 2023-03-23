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
package io.cryostat.net.openshift;

import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.cryostat.net.security.InvalidConnectionURLException;
import io.cryostat.net.security.SecurityContext;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.discovery.AbstractNode;
import io.cryostat.platform.discovery.BaseNodeType;
import io.cryostat.platform.discovery.EnvironmentNode;
import io.cryostat.platform.discovery.TargetNode;

class OpenShiftSecurityContext extends SecurityContext {

    OpenShiftSecurityContext(String namespace) {
        super(namespace);
    }

    OpenShiftSecurityContext(AbstractNode node) {
        super(namespaceForNode(node));
    }

    OpenShiftSecurityContext(ServiceRef serviceRef) {
        super(namespaceForServiceRef(serviceRef));
    }

    String getNamespace() {
        return name;
    }

    private static String namespaceForNode(AbstractNode node) {
        return namespaceForServiceRef(findServiceRef(node));
    }

    private static String namespaceForServiceRef(ServiceRef serviceRef) {
        // TODO extract regexes to constants
        URI connectionUri = serviceRef.getServiceUri();
        String host;
        if (connectionUri.toString().startsWith("service:jmx")) {
            Pattern hostPortCapture =
                    Pattern.compile(
                            "service:jmx:rmi:///jndi/rmi://([a-z-A-Z0-9-_\\.]+)(?::([0-9]+))?/jmxrmi");
            Matcher m1 = hostPortCapture.matcher(connectionUri.toString());
            if (!m1.find()) {
                throw new InvalidConnectionURLException(connectionUri);
            }
            host = m1.group(1);
            // int port = Integer.valueOf(m1.group(2));
        } else if (connectionUri.toString().startsWith("http:")) {
            host = connectionUri.getHost();
        } else {
            throw new InvalidConnectionURLException(connectionUri);
        }

        String ipDashedPattern = "((?:(?:25[0-5]|(?:2[0-4]|1\\d|[1-9]|)\\d)-?){4})";
        String hostPattern = "([a-zA-Z0-9-_]+)";
        Pattern subdomainCapture =
                Pattern.compile(
                        String.format("%s\\.%s\\.(?:pod|svc)", ipDashedPattern, hostPattern));

        Matcher m2 = subdomainCapture.matcher(host);
        if (!m2.find()) {
            throw new InvalidConnectionURLException(connectionUri);
        }

        // String ipDashed = m2.group(1);
        String namespace = m2.group(2);

        return namespace;
    }

    // find the first ServiceRef descendant from this node, depth-first search. If this is a
    // namespace then all of the children belong to it. If this is a TargetNode then it has a
    // ServiceRef with an annotation indicating the namespace it belongs to. If it's a node in
    // between then go down to the TargetNode to find the annotation.
    // If we encounter the UNIVERSE or a REALM node then return null to signify no permissions
    // required - these are above the Namespace, so there are no explicit required permissions at
    // that level.
    // If we encounter the Namespace node itself along the way then we end up continuing down to the
    // first TargetNode. FIXME refactor to just return early when encountering a Namespace
    // EnrivonmentNode and take its name directly for the context.
    private static ServiceRef findServiceRef(AbstractNode node) {
        if (node instanceof TargetNode) {
            return ((TargetNode) node).getTarget();
        } else if (node instanceof EnvironmentNode) {
            EnvironmentNode en = (EnvironmentNode) node;
            if (Set.of(BaseNodeType.UNIVERSE, BaseNodeType.REALM).contains(en.getNodeType())) {
                return null;
            }
            List<AbstractNode> children = en.getChildren();
            // this can recurse until stack overflow if the input node is not part of a tree but a
            // cyclical graph. that's an invalid state anyway, so just let it happen here
            //
            // this assumes that every leaf in the tree is a TargetNode, and therefore there is a
            // direct path from any higher node down to a TargetNode by traversing down the first
            // child along the path. That's currently the case, but will it always be?
            return findServiceRef(children.get(0));
        } else {
            throw new IllegalStateException("Unknown node type: " + node.getClass().getName());
        }
    }
}
