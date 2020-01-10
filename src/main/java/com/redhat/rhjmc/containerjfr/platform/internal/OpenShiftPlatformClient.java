package com.redhat.rhjmc.containerjfr.platform.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.localization.LocalizationManager;
import com.redhat.rhjmc.containerjfr.net.AbstractAuthManager;
import com.redhat.rhjmc.containerjfr.net.AuthManager;
import com.redhat.rhjmc.containerjfr.net.NetworkResolver;
import com.redhat.rhjmc.containerjfr.platform.PlatformClient;
import com.redhat.rhjmc.containerjfr.platform.ServiceRef;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.OpenShiftConfigBuilder;
import org.apache.commons.lang3.StringUtils;

class OpenShiftPlatformClient implements PlatformClient {

    private final Logger logger;
    private final OpenShiftClient osClient;
    private final NetworkResolver resolver;
    private final LocalizationManager lm;

    OpenShiftPlatformClient(
            Logger logger,
            OpenShiftClient osClient,
            NetworkResolver resolver,
            LocalizationManager lm) {
        this.logger = logger;
        this.osClient = osClient;
        this.resolver = resolver;
        this.lm = lm;
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

    @Override
    public AuthManager getAuthManager() {
        return new OpenShiftAuthManager(logger, lm);
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

    @SuppressFBWarnings("DMI_HARDCODED_ABSOLUTE_FILENAME")
    private static String getNamespace() throws IOException {
        return Files.readString(Paths.get(Config.KUBERNETES_NAMESPACE_PATH));
    }

    private static class OpenShiftAuthManager extends AbstractAuthManager {
        OpenShiftAuthManager(Logger logger, LocalizationManager lm) {
            super(logger, lm);

            lm.putMessage(
                    Locale.ENGLISH,
                    AbstractAuthManager.DOC_MESSAGE_KEY_AUTH_DIALOG,
                    "ContainerJFR connection requires a platform auth token to validate user authorization. Please enter a valid access token for your user account. You can enter the token given by \"oc whoami --show-token\"");
        }

        @Override
        public Future<Boolean> validateToken(Supplier<String> tokenProvider)
                throws TimeoutException {
            String token = tokenProvider.get();
            if (StringUtils.isBlank(token)) {
                return CompletableFuture.completedFuture(false);
            }
            return CompletableFuture.supplyAsync(
                            () -> {
                                try (OpenShiftClient authClient =
                                        new DefaultOpenShiftClient(
                                                new OpenShiftConfigBuilder()
                                                        .withOauthToken(token)
                                                        .build())) {
                                    // only an authenticated user should be allowed to list routes
                                    // in the namespace
                                    // TODO find a better way to authenticate tokens
                                    authClient
                                            .routes()
                                            .inNamespace(OpenShiftPlatformClient.getNamespace())
                                            .list();
                                    return true;
                                } catch (KubernetesClientException e) {
                                    logger.info(e);
                                } catch (Exception e) {
                                    logger.error(e);
                                }
                                return false;
                            })
                    .orTimeout(15, TimeUnit.SECONDS);
        }
    }
}
