package com.redhat.rhjmc.containerjfr.net;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import com.redhat.rhjmc.containerjfr.core.log.Logger;

public class NoopAuthManager extends AbstractAuthManager {

    public NoopAuthManager(Logger logger) {
        super(logger);
    }

    @Override
    public Future<Boolean> validateToken(Supplier<String> tokenProvider) {
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public Future<Boolean> validateHttpHeader(Supplier<String> headerProvider) {
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public Future<Boolean> validateWebSocketSubProtocol(Supplier<String> subProtocolProvider) {
        return CompletableFuture.completedFuture(true);
    }
}
