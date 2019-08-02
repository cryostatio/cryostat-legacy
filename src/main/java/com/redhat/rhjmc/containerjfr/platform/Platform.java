package com.redhat.rhjmc.containerjfr.platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Optional;

import com.redhat.rhjmc.containerjfr.core.util.log.Logger;

import org.apache.commons.lang3.exception.ExceptionUtils;

import io.kubernetes.client.ApiException;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.util.Config;

public class Platform {

    private final Logger logger;
    private final PlatformClient client;

    Platform(Logger logger) {
        this.logger = logger;
        if (detectKubernetes()) {
            logger.info("Kubernetes configuration detected");
            client = new KubePlatformClient(logger, new CoreV1Api());
        } else {
            logger.info("No runtime platform support available");
            client = new DefaultPlatformClient();
        }
    }

    private boolean detectKubernetes() {
        try {
            Configuration.setDefaultApiClient(Config.fromCluster());
            // arbitrary request - don't care about the result, just whether the API is available
            new CoreV1Api().listNamespacedService(Files.readString(Paths.get(Config.SERVICEACCOUNT_ROOT, "namespace")), null, null, null, null, null, null, null, null, null);
        } catch (IOException e) {
            logger.debug(ExceptionUtils.getMessage(e));
            logger.debug(ExceptionUtils.getStackTrace(e));
            return false;
        } catch (ApiException e) {
            logger.debug(ExceptionUtils.getMessage(e));
            logger.debug(e.getResponseBody());
            logger.debug(ExceptionUtils.getStackTrace(e));
            return false;
        }
        return true;
    }

    public Optional<PlatformClient> getClient() {
        return Optional.of(client);
    }
}
