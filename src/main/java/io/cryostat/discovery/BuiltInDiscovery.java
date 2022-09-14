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

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import io.cryostat.configuration.Variables;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Environment;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.platform.PlatformClient;
import io.cryostat.platform.TargetDiscoveryEvent;
import io.cryostat.platform.discovery.EnvironmentNode;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;

public class BuiltInDiscovery extends AbstractVerticle implements Consumer<TargetDiscoveryEvent> {

    static final String NOTIFICATION_CATEGORY = "TargetJvmDiscovery";

    private final DiscoveryStorage storage;
    private final Set<PlatformClient> platformClients;
    private final Environment env;
    private final NotificationFactory notificationFactory;
    private final Logger logger;

    BuiltInDiscovery(
            DiscoveryStorage storage,
            Set<PlatformClient> platformClients,
            Environment env,
            NotificationFactory notificationFactory,
            Logger logger) {
        this.storage = storage;
        this.platformClients = platformClients;
        this.env = env;
        this.notificationFactory = notificationFactory;
        this.logger = logger;
    }

    @Override
    public void start(Promise<Void> start) {
        try {
            if (env.hasEnv(Variables.DISABLE_BUILTIN_DISCOVERY)) {
                return;
            }
            storage.addTargetDiscoveryListener(this);

            for (PlatformClient platform : this.platformClients) {
                logger.info(
                        "Starting built-in discovery with {}", platform.getClass().getSimpleName());
                String realmName = platform.getDiscoveryTree().getName();

                UUID id = storage.register(realmName, DiscoveryStorage.NO_CALLBACK);

                platform.addTargetDiscoveryListener(
                        tde -> storage.update(id, platform.getDiscoveryTree().getChildren()));
                Promise<EnvironmentNode> promise = Promise.promise();
                promise.future().onSuccess(subtree -> storage.update(id, subtree.getChildren()));
                platform.start();
                platform.load(id, promise);
            }
            start.complete();
        } catch (Exception e) {
            start.fail(e);
        }
    }

    @Override
    public void stop() {
        storage.removeTargetDiscoveryListener(this);
        this.platformClients.forEach(
                platform -> {
                    try {
                        platform.stop();
                    } catch (Exception e) {
                        logger.error(e);
                    }
                });
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
