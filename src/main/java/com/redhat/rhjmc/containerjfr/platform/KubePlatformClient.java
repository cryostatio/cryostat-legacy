package com.redhat.rhjmc.containerjfr.platform;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.redhat.rhjmc.containerjfr.core.util.log.Logger;

import org.apache.commons.lang3.exception.ExceptionUtils;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1Service;

class KubePlatformClient implements PlatformClient {

    // TODO implement search method that is service port agnostic
    private static final int EXPECTED_SERVICE_PORT = 9091;
    private final Logger logger;
    private final CoreV1Api api;

    KubePlatformClient(Logger logger, CoreV1Api api) {
        this.logger = logger;
        this.api = api;
    }

    @Override
    public List<ServiceRef> listDiscoverableServices() {
        try {
            return this.api
                .listServiceForAllNamespaces(null, null, null, null, null, null, null, null, null)
                .getItems()
                .stream()
                .map(V1Service::getSpec)
                .filter(s -> s.getPorts().stream().anyMatch(p -> p.getPort() == EXPECTED_SERVICE_PORT))
                .map(s -> new ServiceRef(
                                s.getExternalName(),
                                s.getClusterIP(),
                                EXPECTED_SERVICE_PORT
                            ))
                .collect(Collectors.toList());
        } catch (ApiException e) {
            logger.warn(ExceptionUtils.getStackTrace(e));
            return Collections.emptyList();
        }
    }
}
