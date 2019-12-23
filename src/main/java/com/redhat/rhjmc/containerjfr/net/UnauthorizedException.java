package com.redhat.rhjmc.containerjfr.net;

@SuppressWarnings("serial")
public class UnauthorizedException extends Exception {
    UnauthorizedException(String user) {
        super(String.format("User \"%s\" failed auth", user));
    }
}
