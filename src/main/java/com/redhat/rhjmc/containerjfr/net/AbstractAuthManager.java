package com.redhat.rhjmc.containerjfr.net;

import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Supplier;

import com.redhat.rhjmc.containerjfr.core.log.Logger;

public abstract class AbstractAuthManager implements AuthManager {

    protected final Logger logger;

    protected AbstractAuthManager(Logger logger) {
        this.logger = logger;
    }

    @Override
    public AuthenticatedAction doAuthenticated(
            Supplier<String> provider, Function<Supplier<String>, Future<Boolean>> validator) {
        return new AuthenticatedAction() {
            private Runnable onSuccess;
            private Runnable onFailure;

            @Override
            public AuthenticatedAction onSuccess(Runnable runnable) {
                this.onSuccess = runnable;
                return this;
            }

            @Override
            public AuthenticatedAction onFailure(Runnable runnable) {
                this.onFailure = runnable;
                return this;
            }

            @Override
            public void execute() {
                try {
                    if (validator.apply(provider).get()) {
                        this.onSuccess.run();
                    } else {
                        this.onFailure.run();
                    }
                } catch (Exception e) {
                    logger.warn(e);
                    this.onFailure.run();
                }
            }
        };
    }
}
