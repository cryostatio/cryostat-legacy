/*-
 * #%L
 * Container JFR
 * %%
 * Copyright (C) 2020 Red Hat, Inc.
 * %%
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
 * #L%
 */
package com.redhat.rhjmc.containerjfr.platform.openshift;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.platform.PlatformClient;
import com.redhat.rhjmc.containerjfr.platform.ServiceRef;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.fabric8.kubernetes.api.model.EndpointPort;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.openshift.client.OpenShiftClient;

class OpenShiftPlatformClient implements PlatformClient {

    private final OpenShiftClient osClient;
    private final FileSystem fs;
    private final Logger logger;

    OpenShiftPlatformClient(OpenShiftClient osClient, FileSystem fs, Logger logger) {
        this.osClient = osClient;
        this.fs = fs;
        this.logger = logger;
    }

    @Override
    public List<ServiceRef> listDiscoverableServices() {
        try {
            List<ServiceRef> refs = new ArrayList<>();
            osClient.endpoints().inNamespace(getNamespace()).list().getItems().stream()
                    .flatMap(endpoints -> endpoints.getSubsets().stream())
                    .forEach(
                            subset ->
                                    subset.getPorts().stream()
                                            .filter(this::isCompatiblePort)
                                            .forEach(
                                                    port ->
                                                            refs.addAll(
                                                                    createServiceRefs(
                                                                            logger, subset,
                                                                            port))));
            return refs;
        } catch (Exception e) {
            logger.error(e);
            return Collections.emptyList();
        }
    }

    private boolean isCompatiblePort(EndpointPort port) {
        return "jfr-jmx".equals(port.getName()) || 9091 == port.getPort();
    }

    private List<ServiceRef> createServiceRefs(
            Logger logger, EndpointSubset subset, EndpointPort port) {
        return subset.getAddresses().stream()
                .map(
                        addr -> {
                            try {
                                return new ServiceRef(
                                        addr.getIp(),
                                        port.getPort(),
                                        addr.getTargetRef().getName());
                            } catch (Exception e) {
                                logger.warn(e);
                                return null;
                            }
                        })
                .collect(Collectors.toList());
    }

    @SuppressFBWarnings(
            value = "DMI_HARDCODED_ABSOLUTE_FILENAME",
            justification = "Kubernetes namespace file path is well-known and absolute")
    private String getNamespace() throws IOException {
        return fs.readFile(Paths.get(Config.KUBERNETES_NAMESPACE_PATH))
                .lines()
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .get();
    }
}
