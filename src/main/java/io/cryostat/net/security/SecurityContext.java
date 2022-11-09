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
package io.cryostat.net.security;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.ServiceRef.AnnotationKey;
import io.cryostat.platform.discovery.EnvironmentNode;
import io.cryostat.platform.discovery.TargetNode;
import io.cryostat.platform.internal.KubeApiPlatformClient.KubernetesNodeType;

public class SecurityContext {
    private static final String KEY_NS = "NS";
    private static final String KEY_SRC = "SRC";
    private static final String KEY_JVMID = "JVMID";

    public static final SecurityContext DEFAULT =
        new SecurityContext(Map.of("__SC__", "default")) {
            @Override
            public String toString() {
                return "__DEFAULT__";
            }
        };

    private final Map<String, String> ctx;

    private SecurityContext(Map<String, String> ctx) {
        this.ctx = Collections.unmodifiableMap(new HashMap<>(ctx));
    }

    public SecurityContext(SecurityContext o) {
        this.ctx = new HashMap<>(o.ctx);
    }

    public SecurityContext(ServiceRef serviceRef) {
        this.ctx = new HashMap<>();
        if (serviceRef.getCryostatAnnotations().containsKey(AnnotationKey.NAMESPACE)) {
            ctx.put(KEY_NS, serviceRef.getCryostatAnnotations().get(AnnotationKey.NAMESPACE));
        }
        ctx.put(KEY_SRC, serviceRef.getServiceUri().toString());
        ctx.put(KEY_JVMID, serviceRef.getJvmId());
    }

    public SecurityContext(TargetNode node) {
        this(node.getTarget());
    }

    public SecurityContext(EnvironmentNode node) {
        // FIXME this should be more platform-agnostic
        if (KubernetesNodeType.NAMESPACE.getKind().equals(node.getNodeType().getKind())) {
            this.ctx = new HashMap<>();
            ctx.put(KEY_NS, node.getName());
        } else {
            this.ctx = new HashMap<>(DEFAULT.ctx);
        }
    }

    public String getNamespace() {
        return ctx.get(KEY_NS);
    }

    public URI getSource() {
        return URI.create(ctx.get(KEY_SRC));
    }

    public String getJvmId() {
        return ctx.get(KEY_JVMID);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ctx);
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
        SecurityContext other = (SecurityContext) obj;
        return Objects.equals(ctx, other.ctx);
    }

    @Override
    public String toString() {
        return ctx.toString();
    }
}
