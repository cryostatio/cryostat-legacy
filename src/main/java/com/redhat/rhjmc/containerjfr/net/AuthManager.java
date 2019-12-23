package com.redhat.rhjmc.containerjfr.net;

import java.util.concurrent.Future;
import java.util.function.Supplier;

public interface AuthManager {
    Future<Boolean> validateToken(Supplier<String> tokenProvider) throws Exception;

    AuthenticatedAction doAuthenticated(Supplier<String> tokenProvider);
}
