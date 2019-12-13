package com.redhat.rhjmc.containerjfr.net;

import java.util.function.Supplier;

public interface AuthManager {
    boolean validateToken(Supplier<String> tokenProvider);
}
