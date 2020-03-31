package com.redhat.rhjmc.containerjfr.platform.openshift;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.net.NetworkResolver;
import com.redhat.rhjmc.containerjfr.platform.PlatformClient;
import com.redhat.rhjmc.containerjfr.platform.ServiceRef;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.openshift.client.OpenShiftClient;

class OpenShiftPlatformClient implements PlatformClient {

    private final Logger logger;
    private final OpenShiftClient osClient;
    private final FileSystem fs;
    private final NetworkResolver resolver;

    OpenShiftPlatformClient(
            Logger logger, OpenShiftClient osClient, FileSystem fs, NetworkResolver resolver) {
        this.logger = logger;
        this.osClient = osClient;
        this.fs = fs;
        this.resolver = resolver;
    }

    @Override
    public List<ServiceRef> listDiscoverableServices() {
        try {
            return osClient.services().inNamespace(getNamespace()).list().getItems().stream()
                    .map(Service::getSpec)
                    .peek(spec -> logger.trace("Service spec: " + spec.toString()))
                    .filter(s -> s.getPorts() != null)
                    .flatMap(
                            s ->
                                    s.getPorts().stream()
                                            .map(
                                                    p ->
                                                            new ServiceRef(
                                                                    s.getClusterIP(), p.getPort())))
                    .parallel()
                    .map(this::resolveServiceRefHostname)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error(e);
            return Collections.emptyList();
        }
    }

    private ServiceRef resolveServiceRefHostname(ServiceRef in) {
        try {
            String hostname = resolver.resolveCanonicalHostName(in.getConnectUrl());
            logger.debug(String.format("Resolved %s to %s", in.getConnectUrl(), hostname));
            return new ServiceRef(hostname, in.getPort());
        } catch (Exception e) {
            logger.debug(e);
            return null;
        }
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
