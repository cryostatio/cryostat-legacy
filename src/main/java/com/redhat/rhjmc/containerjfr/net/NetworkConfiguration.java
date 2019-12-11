package com.redhat.rhjmc.containerjfr.net;

import java.net.SocketException;
import java.net.UnknownHostException;

import com.redhat.rhjmc.containerjfr.core.sys.Environment;

public class NetworkConfiguration {

    private final Environment env;
    private final NetworkResolver resolver;

    NetworkConfiguration(Environment env, NetworkResolver resolver) {
        this.env = env;
        this.resolver = resolver;
    }

    public String getCommandChannelHost() throws SocketException, UnknownHostException {
        return env.getEnv("CONTAINER_JFR_LISTEN_HOST", getWebServerHost());
    }

    public int getDefaultCommandChannelPort() {
        return 9090;
    }

    public int getInternalCommandChannelPort() {
        return Integer.parseInt(
                env.getEnv(
                        "CONTAINER_JFR_LISTEN_PORT",
                        String.valueOf(getDefaultCommandChannelPort())));
    }

    /**
     * "External" or "advertised" port which the command channel listens on, which is publicly
     * exposed for client connections. May vary from internal port number used when service is
     * running behind a proxy or router.
     */
    public int getExternalCommandChannelPort() {
        return Integer.parseInt(
                env.getEnv(
                        "CONTAINER_JFR_EXT_LISTEN_PORT",
                        String.valueOf(getInternalCommandChannelPort())));
    }

    public String getWebServerHost() throws SocketException, UnknownHostException {
        return env.getEnv("CONTAINER_JFR_WEB_HOST", resolver.getHostAddress());
    }

    public int getDefaultWebServerPort() {
        return 8181;
    }

    public int getInternalWebServerPort() {
        return Integer.parseInt(
                env.getEnv("CONTAINER_JFR_WEB_PORT", String.valueOf(getDefaultWebServerPort())));
    }

    public int getExternalWebServerPort() {
        return Integer.parseInt(
                env.getEnv(
                        "CONTAINER_JFR_EXT_WEB_PORT", String.valueOf(getInternalWebServerPort())));
    }

    public boolean isSslProxied() {
        return env.hasEnv("CONTAINER_JFR_SSL_PROXIED");
    }

    public boolean isUntrustedSslAllowed() {
        return env.hasEnv("CONTAINER_JFR_ALLOW_UNTRUSTED_SSL");
    }
}
