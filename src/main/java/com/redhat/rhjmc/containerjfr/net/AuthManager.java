package com.redhat.rhjmc.containerjfr.net;

import java.util.Map;
import java.util.concurrent.Future;
import java.util.function.Supplier;

public interface AuthManager {
    String DOC_MESSAGE_KEY_AUTH_DIALOG = "AUTH_DIALOG";

    Future<Boolean> validateToken(Supplier<String> tokenProvider) throws Exception;

    AuthenticatedAction doAuthenticated(Supplier<String> tokenProvider);

    Map<String, String> getDocumentationMessages(String langTags);
}
