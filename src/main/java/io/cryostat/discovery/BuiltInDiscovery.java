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
package io.cryostat.discovery;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import io.cryostat.core.log.Logger;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.platform.PlatformClient;
import io.cryostat.platform.TargetDiscoveryEvent;
import io.cryostat.platform.discovery.EnvironmentNode;
import io.cryostat.platform.internal.PlatformDetectionStrategy;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;

public class BuiltInDiscovery extends AbstractVerticle implements Consumer<TargetDiscoveryEvent> {

    static final String NOTIFICATION_CATEGORY = "TargetJvmDiscovery";

    private final DiscoveryStorage storage;
    private final Set<PlatformDetectionStrategy<?>> selectedStrategies;
    private final Set<PlatformDetectionStrategy<?>> unselectedStrategies;
    private final Set<PlatformClient> enabledClients = new HashSet<>();
    private final NotificationFactory notificationFactory;
    private final Logger logger;

    BuiltInDiscovery(
            DiscoveryStorage storage,
            Set<PlatformDetectionStrategy<?>> selectedStrategies,
            Set<PlatformDetectionStrategy<?>> unselectedStrategies,
            NotificationFactory notificationFactory,
            Logger logger) {
        this.storage = storage;
        this.selectedStrategies = selectedStrategies;
        this.unselectedStrategies = unselectedStrategies;
        this.notificationFactory = notificationFactory;
        this.logger = logger;
    }

    @Override
    public void start(Promise<Void> start) {
        storage.addTargetDiscoveryListener(this);

        unselectedStrategies.stream()
                .map(PlatformDetectionStrategy::getPlatformClient)
                .forEach(
                        platform ->
                                storage.getBuiltInPluginByRealm(
                                                platform.getDiscoveryTree().getName())
                                        .map(PluginInfo::getId)
                                        .ifPresent(storage::deregister));

        selectedStrategies.stream()
                .map(PlatformDetectionStrategy::getPlatformClient)
                .distinct()
                .forEach(
                        platform -> {
                            logger.info(
                                    "Starting built-in discovery with {}",
                                    platform.getClass().getSimpleName());
                            String realmName = platform.getDiscoveryTree().getName();

                            UUID id =
                                    storage.getBuiltInPluginByRealm(realmName)
                                            .map(PluginInfo::getId)
                                            .orElseGet(
                                                    () -> {
                                                        try {
                                                            return storage.register(
                                                                    realmName,
                                                                    DiscoveryStorage.NO_CALLBACK);
                                                        } catch (RegistrationException e) {
                                                            start.fail(e);
                                                            return null;
                                                        }
                                                    });

                            platform.addTargetDiscoveryListener(
                                    tde ->
                                            getVertx()
                                                    .executeBlocking(
                                                            promise ->
                                                                    promise.complete(
                                                                            storage.update(
                                                                                    id,
                                                                                    platform.getDiscoveryTree()
                                                                                            .getChildren()))));
                            Promise<EnvironmentNode> promise = Promise.promise();
                            promise.future()
                                    .onSuccess(
                                            subtree -> storage.update(id, subtree.getChildren()));
                            try {
                                platform.start();
                                platform.load(promise);
                                enabledClients.add(platform);
                            } catch (Exception e) {
                                logger.warn(e);
                            }
                        });
        start.tryComplete();
    }

    @Override
    public void stop() {
        storage.removeTargetDiscoveryListener(this);
        Iterator<PlatformClient> it = enabledClients.iterator();
        while (it.hasNext()) {
            try {
                it.next().stop();
            } catch (Exception e) {
                logger.error(e);
            }
            it.remove();
        }
    }

    @Override
    public void accept(TargetDiscoveryEvent tde) {
        notificationFactory
                .createBuilder()
                .metaCategory(NOTIFICATION_CATEGORY)
                .message(
                        Map.of(
                                "event",
                                Map.of(
                                        "kind",
                                        tde.getEventKind(),
                                        "serviceRef",
                                        tde.getServiceRef())))
                .build()
                .send();
    }
}
