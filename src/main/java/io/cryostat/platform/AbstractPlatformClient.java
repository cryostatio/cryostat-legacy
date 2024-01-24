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
package io.cryostat.platform;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

import io.cryostat.configuration.Variables;
import io.cryostat.core.net.discovery.JvmDiscoveryClient.EventKind;
import io.cryostat.core.sys.Environment;

public abstract class AbstractPlatformClient implements PlatformClient {

    protected final Environment environment;
    protected final Set<Consumer<TargetDiscoveryEvent>> discoveryListeners;

    protected AbstractPlatformClient(Environment environment) {
        this.environment = environment;
        this.discoveryListeners = new HashSet<>();
    }

    @Override
    public boolean isEnabled() {
        return !Boolean.parseBoolean(
                environment.getEnv(Variables.DISABLE_BUILTIN_DISCOVERY, "false"));
    }

    @Override
    public void addTargetDiscoveryListener(Consumer<TargetDiscoveryEvent> listener) {
        this.discoveryListeners.add(listener);
    }

    @Override
    public void removeTargetDiscoveryListener(Consumer<TargetDiscoveryEvent> listener) {
        this.discoveryListeners.remove(listener);
    }

    protected void notifyAsyncTargetDiscovery(EventKind eventKind, ServiceRef serviceRef) {
        discoveryListeners.forEach(c -> c.accept(new TargetDiscoveryEvent(eventKind, serviceRef)));
    }

    @Override
    public void stop() throws Exception {
        this.discoveryListeners.clear();
    }
}
