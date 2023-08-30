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
import io.cryostat.core.net.discovery.JvmDiscoveryClient;
import io.cryostat.net.AuthManager;

import dagger.Lazy;

class DefaultPlatformStrategy implements PlatformDetectionStrategy<DefaultPlatformClient> {

    private final Logger logger;
    private final Lazy<? extends AuthManager> authMgr;
    private final Lazy<JvmDiscoveryClient> discoveryClient;

    DefaultPlatformStrategy(
            Logger logger,
            Lazy<? extends AuthManager> authMgr,
            Lazy<JvmDiscoveryClient> discoveryClient) {
        this.logger = logger;
        this.authMgr = authMgr;
        this.discoveryClient = discoveryClient;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public DefaultPlatformClient getPlatformClient() {
        logger.info("Selected Default Platform Strategy");
        return new DefaultPlatformClient(logger, discoveryClient.get());
    }

    @Override
    public AuthManager getAuthManager() {
        return authMgr.get();
    }
}
