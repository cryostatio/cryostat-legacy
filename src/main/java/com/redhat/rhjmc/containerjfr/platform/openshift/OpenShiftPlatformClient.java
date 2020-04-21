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

    private final Logger logger;
    private final OpenShiftClient osClient;
    private final FileSystem fs;

    OpenShiftPlatformClient(Logger logger, OpenShiftClient osClient, FileSystem fs) {
        this.logger = logger;
        this.osClient = osClient;
        this.fs = fs;
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
                                                                            subset, port))));
            return refs;
        } catch (Exception e) {
            logger.error(e);
            return Collections.emptyList();
        }
    }

    private boolean isCompatiblePort(EndpointPort port) {
        return "jfr-jmx".equals(port.getName()) || 9091 == port.getPort();
    }

    private List<ServiceRef> createServiceRefs(EndpointSubset subset, EndpointPort port) {
        return subset.getAddresses().stream()
                .map(
                        addr ->
                                new ServiceRef(
                                        addr.getIp(),
                                        addr.getTargetRef().getName(),
                                        port.getPort()))
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
