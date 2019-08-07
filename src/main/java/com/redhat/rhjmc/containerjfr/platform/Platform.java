package com.redhat.rhjmc.containerjfr.platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.core.util.log.Logger;
import com.redhat.rhjmc.containerjfr.net.NetworkResolver;

import org.apache.commons.lang3.exception.ExceptionUtils;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.util.Config;

class Platform {

    private final Logger logger;
    private final PlatformClient client;

    Platform(Logger logger, Environment env, NetworkResolver resolver) {
        this.logger = logger;
        PlatformClient client;
        if ((client = detectKubernetesApi(resolver)) != null) {
            logger.info("Kubernetes configuration detected and API is accessible");
            this.client = client;
        } else if ((client = detectKubernetesEnv(env)) != null) {
            logger.info("Kubernetes configuration detected but API is inaccessible");
            this.client = client;
        } else {
            logger.info("No runtime platform support available");
            this.client = new DefaultPlatformClient(resolver);
        }
    }

    private PlatformClient detectKubernetesApi(NetworkResolver resolver) {
        try {
            String namespace = getKubernetesNamespace();
            Configuration.setDefaultApiClient(Config.fromCluster());
            CoreV1Api api = new CoreV1Api();
            // arbitrary request - don't care about the result, just whether the API is available
            api.listNamespacedService(namespace, null, null, null, null, null, null, null, null, null);
            return new KubeApiPlatformClient(logger, api, namespace, resolver);
        } catch (IOException e) {
            logger.debug(ExceptionUtils.getMessage(e));
            logger.debug(ExceptionUtils.getStackTrace(e));
        } catch (ApiException e) {
            logger.debug(ExceptionUtils.getMessage(e));
            logger.debug(e.getResponseBody());
            logger.debug(ExceptionUtils.getStackTrace(e));
        }
        return null;
    }

    private PlatformClient detectKubernetesEnv(Environment env) {
        if (env.getEnv().keySet().stream().anyMatch(s -> s.equals("KUBERNETES_SERVICE_HOST"))) {
            return new KubeEnvPlatformClient(env);
        }
        return null;
    }

    @SuppressFBWarnings("DMI_HARDCODED_ABSOLUTE_FILENAME")
    private static String getKubernetesNamespace() throws IOException {
        return Files.readString(Paths.get(Config.SERVICEACCOUNT_ROOT, "namespace"));
    }

    PlatformClient getClient() {
        return client;
    }

}
