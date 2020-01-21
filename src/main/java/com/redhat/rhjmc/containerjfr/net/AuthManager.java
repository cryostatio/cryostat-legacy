package com.redhat.rhjmc.containerjfr.net;

import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * AuthManager implementations must also include a constructor which takes a single parameter of a
 * {@link com.redhat.rhjmc.containerjfr.core.log.Logger}. This is to enable runtime configurable
 * selection of the activated AuthManager by reflection. This interface is meant as internal API
 * and so this requirement is subject to change.
 */
public interface AuthManager {
    Future<Boolean> validateToken(Supplier<String> tokenProvider) throws Exception;

    AuthenticatedAction doAuthenticated(Supplier<String> tokenProvider);
}
