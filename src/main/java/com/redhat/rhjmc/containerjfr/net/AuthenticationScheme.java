package com.redhat.rhjmc.containerjfr.net;

import org.apache.commons.lang3.StringUtils;

public enum AuthenticationScheme {
    BASIC,
    BEARER,
    ;

    @Override
    public String toString() {
        return StringUtils.capitalize(this.name().toLowerCase());
    }
}
