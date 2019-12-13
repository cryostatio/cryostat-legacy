package com.redhat.rhjmc.containerjfr.net;

import java.util.function.Supplier;

public class NoopAuthManager implements AuthManager {
    @Override
    public boolean validateToken(Supplier<String> tokenProvider) {
        return true;
    }
}
