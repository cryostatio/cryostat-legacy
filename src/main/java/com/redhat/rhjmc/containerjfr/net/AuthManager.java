package com.redhat.rhjmc.containerjfr.net;

import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Supplier;

public interface AuthManager {
    AuthenticationScheme getScheme();

    Future<Boolean> validateToken(Supplier<String> tokenProvider);

    Future<Boolean> validateHttpHeader(Supplier<String> headerProvider);

    Future<Boolean> validateWebSocketSubProtocol(Supplier<String> subProtocolProvider);

    AuthenticatedAction doAuthenticated(
            Supplier<String> provider, Function<Supplier<String>, Future<Boolean>> validator);
}
