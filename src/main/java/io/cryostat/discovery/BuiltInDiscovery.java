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

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.Set;

import io.cryostat.core.log.Logger;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.platform.PlatformClient;
import io.cryostat.platform.discovery.AbstractNode;
import io.cryostat.platform.discovery.EnvironmentNode;

import io.vertx.core.AbstractVerticle;

public class BuiltInDiscovery extends AbstractVerticle {

    static final String NOTIFICATION_CATEGORY = "TargetJvmDiscovery";

    private final DiscoveryStorage storage;
    private final Set<PlatformClient> platformClients;
    private final NotificationFactory notificationFactory;
    private final Logger logger;

    BuiltInDiscovery(
            DiscoveryStorage storage,
            Set<PlatformClient> platformClients,
            NotificationFactory notificationFactory,
            Logger logger) {
        this.storage = storage;
        this.platformClients = platformClients;
        this.notificationFactory = notificationFactory;
        this.logger = logger;
    }

    @Override
    public void start() throws MalformedURLException, RegistrationException, IOException {
        storage.addTargetDiscoveryListener(
                tde ->
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
                                .send());

        for (PlatformClient platform : this.platformClients) {
            logger.info("Starting built-in discovery with {}", platform.getClass().getSimpleName());
            AbstractNode realm = platform.getDiscoveryTree();
            if (!(realm instanceof EnvironmentNode)) {
                logger.error(
                        "BuiltInDiscovery encountered an unexpected node type: {}",
                        realm.getClass().getName());
                continue;
            }
            String name = realm.getName();
            int id = storage.register(name, new URL("http://localhost/health"));
            platform.addTargetDiscoveryListener(
                    tde -> storage.update(id, platform.getDiscoveryTree().getChildren()));
            platform.start();
            storage.update(id, platform.getDiscoveryTree().getChildren());
        }
    }
}
