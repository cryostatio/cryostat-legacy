package com.redhat.rhjmc.containerjfr.net;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;

public class NoopAuthManager extends AbstractAuthManager {

    public NoopAuthManager(Logger logger, FileSystem fs) {
        super(logger, fs);
    }

    public NoopAuthManager() {
        super(null, null);
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
