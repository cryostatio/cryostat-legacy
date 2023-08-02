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
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import io.cryostat.platform.discovery.EnvironmentNode;

import io.vertx.core.Promise;

public interface PlatformClient {
    default void start() throws Exception {}

    default void load(Promise<EnvironmentNode> promise) {
        promise.complete(getDiscoveryTree());
    }

    void stop() throws Exception;

    List<ServiceRef> listDiscoverableServices();

    default List<ServiceRef> listUniqueReachableServices() {
        Set<String> uniqueIds = new HashSet<>();
        return listDiscoverableServices().stream()
                .filter((ref) -> ref.getJvmId() != null && uniqueIds.add(ref.getJvmId()))
                .toList();
    }

    default boolean contains(ServiceRef ref) {
        var existingRef =
                listUniqueReachableServices().stream()
                        .filter(sr -> !sr.equals(ref) && sr.getJvmId().equals(ref.getJvmId()))
                        .findAny();
        return existingRef.isPresent();
    }

    void addTargetDiscoveryListener(Consumer<TargetDiscoveryEvent> listener);

    void removeTargetDiscoveryListener(Consumer<TargetDiscoveryEvent> listener);

    EnvironmentNode getDiscoveryTree();
}
