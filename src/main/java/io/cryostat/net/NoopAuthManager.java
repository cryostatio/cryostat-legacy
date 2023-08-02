/*
 * Copyright The Cryostat Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.cryostat.net;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import io.cryostat.core.log.Logger;
import io.cryostat.net.security.ResourceAction;

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
        return CompletableFuture.completedFuture(new UserInfo(""));
    }

    @Override
    public Optional<String> getLoginRedirectUrl(
            Supplier<String> headerProvider, Set<ResourceAction> resourceActions) {
        return Optional.empty();
    }

    @Override
    public Future<Boolean> validateToken(
            Supplier<String> tokenProvider, Set<ResourceAction> resourceActions) {
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public Future<Boolean> validateHttpHeader(
            Supplier<String> headerProvider, Set<ResourceAction> resourceActions) {
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public Future<Boolean> validateWebSocketSubProtocol(
            Supplier<String> subProtocolProvider, Set<ResourceAction> resourceActions) {
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public Optional<String> logout(Supplier<String> httpHeaderProvider) {
        return Optional.empty();
    }
}
