package com.redhat.rhjmc.containerjfr.net;

import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * AuthManager implementations must also include a constructor with signature: ({@link
 * com.redhat.rhjmc.containerjfr.core.log.Logger}, {@link
 * com.redhat.rhjmc.containerjfr.core.sys.FileSystem}). This is to enable runtime configurable
 * selection of the activated AuthManager by reflection. This interface is meant as internal API and
 * so this requirement is subject to change.
 */
public interface AuthManager {
    AuthenticationScheme getScheme();

    Future<Boolean> validateToken(Supplier<String> tokenProvider);

    Future<Boolean> validateHttpHeader(Supplier<String> headerProvider);

    Future<Boolean> validateWebSocketSubProtocol(Supplier<String> subProtocolProvider);

    AuthenticatedAction doAuthenticated(
            Supplier<String> provider, Function<Supplier<String>, Future<Boolean>> validator);
}
