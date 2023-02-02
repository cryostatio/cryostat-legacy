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
package io.cryostat.platform.internal;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.cryostat.core.log.Logger;
import io.cryostat.core.net.JFRConnectionToolkit;
import io.cryostat.core.sys.Environment;
import io.cryostat.platform.AbstractPlatformClient;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.ServiceRef.AnnotationKey;
import io.cryostat.platform.discovery.BaseNodeType;
import io.cryostat.platform.discovery.EnvironmentNode;
import io.cryostat.platform.discovery.NodeType;
import io.cryostat.platform.discovery.TargetNode;
import io.cryostat.util.URIUtil;

import dagger.Lazy;

public class KubeEnvPlatformClient extends AbstractPlatformClient {

    public static final String REALM = "KubernetesEnv";
    private static final Pattern SERVICE_ENV_PATTERN =
            Pattern.compile("([\\S]+)_PORT_([\\d]+)_TCP_ADDR");
    private final String namespace;
    private final Lazy<JFRConnectionToolkit> connectionToolkit;
    private final Environment env;
    private final Logger logger;

    KubeEnvPlatformClient(
            String namespace,
            Lazy<JFRConnectionToolkit> connectionToolkit,
            Environment env,
            Logger logger) {
        this.namespace = namespace;
        this.connectionToolkit = connectionToolkit;
        this.env = env;
        this.logger = logger;
    }

    @Override
    public void start() throws IOException {}

    @Override
    public List<ServiceRef> listDiscoverableServices() {
        return env.getEnv().entrySet().stream()
                .map(this::envToServiceRef)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public EnvironmentNode getDiscoveryTree() {
        List<TargetNode> targets =
                listDiscoverableServices().stream()
                        .map(sr -> new TargetNode(KubernetesNodeType.SERVICE, sr))
                        .toList();
        return new EnvironmentNode(REALM, BaseNodeType.REALM, Collections.emptyMap(), targets);
    }

    private ServiceRef envToServiceRef(Map.Entry<String, String> entry) {
        Matcher matcher = SERVICE_ENV_PATTERN.matcher(entry.getKey());
        if (!matcher.matches()) {
            return null;
        }
        String alias = matcher.group(1).toLowerCase();
        int port = Integer.parseInt(matcher.group(2));
        try {
            ServiceRef sr =
                    new ServiceRef(
                            null,
                            URIUtil.convert(
                                    connectionToolkit
                                            .get()
                                            .createServiceURL(entry.getValue(), port)),
                            alias);
            sr.setCryostatAnnotations(
                    Map.of(
                            AnnotationKey.REALM, REALM,
                            AnnotationKey.NAMESPACE, namespace,
                            AnnotationKey.SERVICE_NAME, alias,
                            AnnotationKey.PORT, Integer.toString(port)));
            return sr;
        } catch (Exception e) {
            logger.warn(e);
            return null;
        }
    }

    public enum KubernetesNodeType implements NodeType {
        SERVICE("Service"),
        ;

        private final String kind;

        KubernetesNodeType(String kind) {
            this.kind = kind;
        }

        @Override
        public String getKind() {
            return kind;
        }

        @Override
        public String toString() {
            return getKind();
        }
    }
}
