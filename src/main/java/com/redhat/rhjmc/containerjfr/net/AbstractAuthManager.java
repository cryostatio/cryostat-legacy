package com.redhat.rhjmc.containerjfr.net;

import java.util.Locale;
import java.util.function.Supplier;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.documentation_messages.DocumentationMessageManager;

public abstract class AbstractAuthManager implements AuthManager {
    public static final String DOC_MESSAGE_KEY_AUTH_DIALOG = "AUTH_DIALOG";

    protected final Logger logger;
    protected final DocumentationMessageManager dmm;

    protected AbstractAuthManager(Logger logger, DocumentationMessageManager dmm) {
        this.logger = logger;
        this.dmm = dmm;

        dmm.putMessage(
                Locale.ENGLISH,
                DOC_MESSAGE_KEY_AUTH_DIALOG,
                "ContainerJFR connection requires a platform auth token to validate user authorization. Please enter a valid access token for your user account.");
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
