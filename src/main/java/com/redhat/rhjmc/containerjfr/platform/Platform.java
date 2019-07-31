package com.redhat.rhjmc.containerjfr.platform;

import java.util.Optional;

import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.core.util.log.Logger;

import org.apache.commons.lang3.StringUtils;

public class Platform {

    private final PlatformClient client;

    Platform(Environment env) {
        if (detectKubernetes(env)) {
            Logger.INSTANCE.info("Kubernetes configuration detected");
            client = new KubePlatformClient(env);
        } else {
            Logger.INSTANCE.info("No runtime platform detected");
            client = new DefaultPlatformClient();
        }
    }

    private boolean detectKubernetes(Environment env) {
        // TODO implement check using Kubernetes API
        return StringUtils.isNotBlank(env.getEnv("KUBERNETES_PORT"));
    }

    public Optional<PlatformClient> getClient() {
        return Optional.of(client);
    }
}
