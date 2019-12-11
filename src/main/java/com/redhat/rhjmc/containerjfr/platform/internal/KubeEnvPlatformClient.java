package com.redhat.rhjmc.containerjfr.platform.internal;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.platform.PlatformClient;
import com.redhat.rhjmc.containerjfr.platform.ServiceRef;

class KubeEnvPlatformClient implements PlatformClient {

    private static final Pattern SERVICE_ENV_PATTERN =
            Pattern.compile("([\\S]+)_PORT_([\\d]+)_TCP_ADDR");
    private final Environment env;

    KubeEnvPlatformClient(Environment env) {
        this.env = env;
    }

    @Override
    public List<ServiceRef> listDiscoverableServices() {
        return env.getEnv().entrySet().stream()
                .map(KubeEnvPlatformClient::envToServiceRef)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static ServiceRef envToServiceRef(Map.Entry<String, String> entry) {
        Matcher matcher = SERVICE_ENV_PATTERN.matcher(entry.getKey());
        if (!matcher.matches()) {
            return null;
        }
        String alias = matcher.group(1).toLowerCase();
        int port = Integer.parseInt(matcher.group(2));
        return new ServiceRef(entry.getValue(), alias, port);
    }
}
