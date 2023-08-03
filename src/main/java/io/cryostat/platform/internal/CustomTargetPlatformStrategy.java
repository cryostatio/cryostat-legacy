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
package io.cryostat.platform.internal;

import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;

import dagger.Lazy;

public class CustomTargetPlatformStrategy
        implements PlatformDetectionStrategy<CustomTargetPlatformClient> {

    private final Logger logger;
    private final Lazy<? extends AuthManager> authMgr;
    private final Lazy<CustomTargetPlatformClient> client;

    CustomTargetPlatformStrategy(
            Logger logger,
            Lazy<? extends AuthManager> authMgr,
            Lazy<CustomTargetPlatformClient> client) {
        this.logger = logger;
        this.authMgr = authMgr;
        this.client = client;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public CustomTargetPlatformClient getPlatformClient() {
        logger.info("Selected Default Platform Strategy");
        return client.get();
    }

    @Override
    public AuthManager getAuthManager() {
        return authMgr.get();
    }
}
