/*
 * Copyright The Cryostat Authors
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software (each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.cryostat.net;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import io.cryostat.core.log.Logger;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.SecurityContext;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.discovery.AbstractNode;

public class NoopAuthManager extends AbstractAuthManager {

    public NoopAuthManager(Logger logger) {
        super(logger);
    }

    @Override
    public AuthenticationScheme getScheme() {
        return AuthenticationScheme.NONE;
    }

    @Override
    public Future<UserInfo> getUserInfo(Supplier<String> httpHeaderProvider) {
        return CompletableFuture.completedFuture(new UserInfo("anonymous"));
    }

    @Override
    public Optional<String> getLoginRedirectUrl(
            Supplier<String> headerProvider, Set<ResourceAction> resourceActions) {
        return Optional.empty();
    }

    @Override
    public Future<Boolean> validateToken(
            Supplier<String> tokenProvider,
            SecurityContext securityContext,
            Set<ResourceAction> resourceActions) {
        resourceActions.forEach(
                action ->
                        logger.info(
                                "anonymous user with credentials '{}' granted [{} {}] in context"
                                        + " '{}'",
                                tokenProvider.get(),
                                action.getVerb(),
                                action.getResource(),
                                securityContext));
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public Future<Boolean> validateHttpHeader(
            Supplier<String> headerProvider,
            SecurityContext securityContext,
            Set<ResourceAction> resourceActions) {
        return validateToken(headerProvider, securityContext, resourceActions);
    }

    @Override
    public Future<Boolean> validateWebSocketSubProtocol(
            Supplier<String> subProtocolProvider,
            SecurityContext securityContext,
            Set<ResourceAction> resourceActions) {
        return validateToken(subProtocolProvider, securityContext, resourceActions);
    }

    @Override
    public Optional<String> logout(Supplier<String> httpHeaderProvider) {
        return Optional.empty();
    }

    @Override
    public List<SecurityContext> getSecurityContexts() {
        return List.of(SecurityContext.DEFAULT);
    }

    @Override
    public SecurityContext contextFor(AbstractNode node) {
        return SecurityContext.DEFAULT;
    }

    @Override
    public SecurityContext contextFor(ServiceRef serviceRef) {
        return SecurityContext.DEFAULT;
    }
}
