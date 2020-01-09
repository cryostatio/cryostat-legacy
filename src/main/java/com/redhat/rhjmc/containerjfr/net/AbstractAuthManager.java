package com.redhat.rhjmc.containerjfr.net;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import com.redhat.rhjmc.containerjfr.core.log.Logger;

public abstract class AbstractAuthManager implements AuthManager {

    private static final Map<Locale, Map<String, String>> documentationMessages;
    static {
        Map<Locale, Map<String, String>> messages = new HashMap<>();

        Map<String, String> en = new HashMap<>();
        en.put(DOC_MESSAGE_KEY_AUTH_DIALOG, "ContainerJFR connection requires a platform auth token to validate user authorization. Please enter a valid access token for your user account.");
        messages.put(Locale.ENGLISH, Collections.unmodifiableMap(en));

        documentationMessages = Collections.unmodifiableMap(messages);
    }

    protected final Logger logger;

    protected AbstractAuthManager(Logger logger) {
        this.logger = logger;
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

    @Override
    public Map<String, String> getDocumentationMessages(String langTags) {
        List<Locale.LanguageRange> languageRanges = Locale.LanguageRange.parse(langTags);
        Locale locale = Locale.lookup(languageRanges, documentationMessages.keySet());
        Map<String, String> dictionary = documentationMessages.get(locale);
        if (dictionary == null) {
            dictionary = documentationMessages.get(Locale.ENGLISH);
        }

        return dictionary;
    }
}
