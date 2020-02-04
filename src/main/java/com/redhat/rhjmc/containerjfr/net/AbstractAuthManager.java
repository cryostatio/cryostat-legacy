package com.redhat.rhjmc.containerjfr.net;

import java.util.function.Supplier;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.localization.LocalizationManager;

public abstract class AbstractAuthManager implements AuthManager {
    public static final String DOC_MESSAGE_KEY_AUTH_DIALOG_MESSAGE = "AUTH_DIALOG_MESSAGE";

    protected final Logger logger;
    protected final LocalizationManager lm;

    protected AbstractAuthManager(Logger logger, LocalizationManager lm) {
        this.logger = logger;
        this.lm = lm;
    }

    @Override
    public AuthenticatedAction doAuthenticated(Supplier<String> tokenProvider) {
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
                    if (validateToken(tokenProvider).get()) {
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
