package com.redhat.rhjmc.containerjfr.platform.internal;

import java.net.UnknownHostException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.net.NetworkResolver;
import com.redhat.rhjmc.containerjfr.platform.PlatformClient;
import com.redhat.rhjmc.containerjfr.platform.ServiceRef;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1Service;

class KubeApiPlatformClient implements PlatformClient {

    private final Logger logger;
    private final CoreV1Api api;
    private final String namespace;
    private final NetworkResolver resolver;

    KubeApiPlatformClient(Logger logger, CoreV1Api api, String namespace, NetworkResolver resolver) {
        this.logger = logger;
        this.api = api;
        this.namespace = namespace;
        this.resolver = resolver;
    }

    @Override
    public List<ServiceRef> listDiscoverableServices() {
        try {
            return api
                .listNamespacedService(namespace, null, null, null, null, null, null, null, null, null)
                .getItems()
                .stream()
                .map(V1Service::getSpec)
                .flatMap(s -> s.getPorts().stream().map(p -> new ServiceRef(s.getClusterIP(), "", p.getPort())))
                .parallel()
                .map(this::resolveServiceRefHostname)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        } catch (ApiException e) {
            logger.warn(e.getMessage());
            logger.warn(e.getResponseBody());
            return Collections.emptyList();
        }
    }

    private ServiceRef resolveServiceRefHostname(ServiceRef in) {
        try {
            String hostname = resolver.resolveCanonicalHostName(in.getIp());
            logger.debug(String.format("Resolved %s to %s", in.getIp(), hostname));
            return new ServiceRef(in.getIp(), hostname, in.getPort());
        } catch (UnknownHostException e) {
            logger.debug(e);
            return null;
        }
    }
}
