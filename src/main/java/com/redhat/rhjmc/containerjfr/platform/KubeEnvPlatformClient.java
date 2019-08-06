package com.redhat.rhjmc.containerjfr.platform;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.stream.Collectors;

import com.redhat.rhjmc.containerjfr.core.sys.Environment;

class KubeEnvPlatformClient implements PlatformClient {

    private static final int TESTED_PORT = 9091;
    private static final String KUBERNETES_ENV_SUFFIX = "_PORT_" + TESTED_PORT + "_TCP_ADDR";

    private final Environment env;

    KubeEnvPlatformClient(Environment env) {
        this.env = env;
    }

    @Override
    public List<ServiceRef> listDiscoverableServices() {
        return env.getEnv().entrySet().parallelStream()
                .filter(e -> e.getKey().endsWith(KUBERNETES_ENV_SUFFIX))
                .map(e -> testHostByName(e.getValue()))
                .filter(m -> m != null)
                .collect(Collectors.toList());
    }

    private ServiceRef testHostByName(String host) {
        try {
            return testHost(InetAddress.getByName(host));
        } catch (IOException ignored) {
            return null;
        }
    }

    private ServiceRef testHost(InetAddress addr) throws IOException {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(addr, TESTED_PORT), 100);
            return new ServiceRef(addr.getCanonicalHostName(), addr.getHostAddress(), TESTED_PORT);
        }
    }

}
