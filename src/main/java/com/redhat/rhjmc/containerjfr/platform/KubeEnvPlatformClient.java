package com.redhat.rhjmc.containerjfr.platform;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.redhat.rhjmc.containerjfr.core.sys.Environment;

class KubeEnvPlatformClient implements PlatformClient {

    private static final Pattern SERVICE_ENV_PATTERN = Pattern.compile("([\\S]+)_PORT_([\\d]+)_TCP_ADDR");
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
        return testTarget(entry.getValue(), alias, port);
    }

    private static ServiceRef testTarget(String host, String alias, int port) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(InetAddress.getByName(host), port), 100);
            return new ServiceRef(host, alias, port);
        } catch (IOException e) {
            return null;
        }
    }

}
