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

import java.io.IOException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Supplier;

import io.cryostat.net.security.ResourceAction;

public interface AuthManager {
    AuthenticationScheme getScheme();

    Future<UserInfo> getUserInfo(Supplier<String> httpHeaderProvider);

    Optional<String> getLoginRedirectUrl(
            Supplier<String> headerProvider, Set<ResourceAction> resourceActions)
            throws ExecutionException, InterruptedException;

    Optional<String> logout(Supplier<String> httpHeaderProvider)
            throws ExecutionException, InterruptedException, IOException, TokenNotFoundException;

    Future<Boolean> validateToken(
            Supplier<String> tokenProvider, Set<ResourceAction> resourceActions);

    Future<Boolean> validateHttpHeader(
            Supplier<String> headerProvider, Set<ResourceAction> resourceActions);

    Future<Boolean> validateWebSocketSubProtocol(
            Supplier<String> subProtocolProvider, Set<ResourceAction> resourceActions);

    AuthenticatedAction doAuthenticated(
            Supplier<String> provider, Function<Supplier<String>, Future<Boolean>> validator);
}
