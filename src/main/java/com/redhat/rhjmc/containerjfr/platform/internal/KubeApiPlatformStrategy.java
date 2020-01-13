package com.redhat.rhjmc.containerjfr.platform.internal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.documentation_messages.DocumentationMessageManager;
import com.redhat.rhjmc.containerjfr.net.NetworkResolver;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.util.Config;

class KubeApiPlatformStrategy implements PlatformDetectionStrategy<KubeApiPlatformClient> {

    private final Logger logger;
    private CoreV1Api api;
    private final String namespace;
    private final NetworkResolver resolver;
    private final DocumentationMessageManager dmm;

    KubeApiPlatformStrategy(
            Logger logger, NetworkResolver resolver, DocumentationMessageManager dmm) {
        this.logger = logger;
        try {
            Configuration.setDefaultApiClient(Config.fromCluster());
            this.api = new CoreV1Api();
        } catch (IOException e) {
            this.api = null;
        }
        this.namespace = getNamespace();
        this.resolver = resolver;
        this.dmm = dmm;
    }

    @Override
    public int getPriority() {
        return PRIORITY_PLATFORM + 10;
    }

    @Override
    public boolean isAvailable() {
        logger.trace("Testing KubeApi Platform Availability");
        if (api == null || namespace == null) {
            return false;
        }
        try {
            api.listNamespacedService(
                    namespace, null, null, null, null, null, null, null, null, null);
        } catch (ApiException e) {
            logger.debug(e.getResponseBody());
            return false;
        }
        return true;
    }

    @Override
    public KubeApiPlatformClient get() {
        logger.info("Selected KubeApi Platform Strategy");
        return new KubeApiPlatformClient(logger, api, namespace, resolver, dmm);
    }

    @SuppressFBWarnings("DMI_HARDCODED_ABSOLUTE_FILENAME")
    private static String getNamespace() {
        try {
            return Files.readString(Paths.get(Config.SERVICEACCOUNT_ROOT, "namespace"));
        } catch (IOException e) {
            return null;
        }
    }
}
