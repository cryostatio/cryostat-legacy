package com.redhat.rhjmc.containerjfr.platform;

import java.util.Optional;

public class Platform {

    private final PlatformClient client;

    Platform() {
        if (detectKubernetes()) {
            client = null;
        } else {
            client = new DefaultPlatformClient();
        }
    }

    private boolean detectKubernetes() {
        return false;
    }

    public Optional<PlatformClient> getClient() {
        return Optional.ofNullable(client);
    }
}
