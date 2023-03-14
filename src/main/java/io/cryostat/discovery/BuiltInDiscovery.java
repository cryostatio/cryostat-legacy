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
package io.cryostat.discovery;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import io.cryostat.core.log.Logger;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.platform.PlatformClient;
import io.cryostat.platform.TargetDiscoveryEvent;
import io.cryostat.platform.discovery.EnvironmentNode;
import io.cryostat.platform.internal.CustomTargetPlatformClient;
import io.cryostat.platform.internal.PlatformDetectionStrategy;

import dagger.Lazy;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;

public class BuiltInDiscovery extends AbstractVerticle implements Consumer<TargetDiscoveryEvent> {

    static final String NOTIFICATION_CATEGORY = "TargetJvmDiscovery";

    private final DiscoveryStorage storage;
    private final Set<PlatformDetectionStrategy<?>> selectedStrategies;
    private final Set<PlatformDetectionStrategy<?>> unselectedStrategies;
    private final Lazy<CustomTargetPlatformClient> customTargets;
    private final Set<PlatformClient> enabledClients = new HashSet<>();
    private final NotificationFactory notificationFactory;
    private final Logger logger;

    BuiltInDiscovery(
            DiscoveryStorage storage,
            SortedSet<PlatformDetectionStrategy<?>> selectedStrategies,
            SortedSet<PlatformDetectionStrategy<?>> unselectedStrategies,
            Lazy<CustomTargetPlatformClient> customTargets,
            NotificationFactory notificationFactory,
            Logger logger) {
        this.storage = storage;
        this.selectedStrategies = selectedStrategies;
        this.unselectedStrategies = unselectedStrategies;
        this.customTargets = customTargets;
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

        Stream.concat(
                        // ensure custom targets is always available regardless of other
                        // configurations
                        Stream.of(customTargets.get()),
                        selectedStrategies.stream()
                                .map(PlatformDetectionStrategy::getPlatformClient))
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
                                start.fail(e);
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
