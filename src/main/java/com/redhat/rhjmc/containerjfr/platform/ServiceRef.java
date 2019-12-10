package com.redhat.rhjmc.containerjfr.platform;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class ServiceRef {

    private final String connectUrl;
    private final String alias;
    private final int port;

    public ServiceRef(String connectUrl, int port) {
        this(connectUrl, connectUrl, port);
    }

    public ServiceRef(String connectUrl, String alias, int port) {
        this.connectUrl = connectUrl;
        this.alias = alias;
        this.port = port;
    }

    public String getConnectUrl() {
        return connectUrl;
    }

    public String getAlias() {
        return alias;
    }

    public int getPort() {
        return port;
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }
}
