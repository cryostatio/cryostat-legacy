package com.redhat.rhjmc.containerjfr.net;

import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

public interface AuthManager {
    Future<Boolean> validateToken(Supplier<String> tokenProvider) throws TimeoutException;

    AuthenticatedAction doAuthenticated(Supplier<String> tokenProvider);
}
