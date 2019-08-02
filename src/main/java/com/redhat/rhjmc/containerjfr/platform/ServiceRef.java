package com.redhat.rhjmc.containerjfr.platform;

public class ServiceRef {

    private final String ip;
    private final String hostname;
    private final int port;

    public ServiceRef(String ip, String hostname, int port) {
        this.ip = ip;
        this.hostname = hostname;
        this.port = port;
    }

    public String getIp() {
        return ip;
    }

    public String getHostname() {
        return hostname;
    }

    public int getPort() {
        return port;
    }

}
