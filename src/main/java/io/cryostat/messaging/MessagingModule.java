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
package io.cryostat.messaging;

import javax.inject.Named;
import javax.inject.Singleton;

import io.cryostat.configuration.Variables;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Clock;
import io.cryostat.core.sys.Environment;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.messaging.notifications.NotificationListener;
import io.cryostat.messaging.notifications.NotificationsModule;
import io.cryostat.net.AuthManager;
import io.cryostat.net.HttpServer;

import com.google.gson.Gson;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import io.vertx.core.Vertx;

@Module(
        includes = {
            NotificationsModule.class,
        })
public abstract class MessagingModule {

    static final String WS_MAX_CONNECTIONS = "WS_MAX_CONNECTIONS";

    @Provides
    @Singleton
    static MessagingServer provideWebSocketMessagingServer(
            Vertx vertx,
            HttpServer server,
            Environment env,
            AuthManager authManager,
            NotificationFactory notificationFactory,
            @Named(WS_MAX_CONNECTIONS) int maxConnections,
            Clock clock,
            Logger logger,
            Gson gson) {
        return new MessagingServer(
                vertx,
                server,
                env,
                authManager,
                notificationFactory,
                maxConnections,
                clock,
                logger,
                gson);
    }

    @Binds
    @IntoSet
    abstract NotificationListener bindMessagingServer(MessagingServer server);

    @Provides
    @Named(WS_MAX_CONNECTIONS)
    static int provideWebSocketMaxConnections(Environment env, Logger logger) {
        try {
            int count =
                    Integer.parseInt(
                            env.getEnv(
                                    Variables.MAX_CONNECTIONS_ENV_VAR,
                                    String.valueOf(Integer.MAX_VALUE)));
            if (count <= 0) {
                logger.warn(
                        "{} was set to {} - ignoring", Variables.MAX_CONNECTIONS_ENV_VAR, count);
                count = Integer.MAX_VALUE;
            }
            return count;
        } catch (NumberFormatException nfe) {
            logger.warn(nfe);
            return Integer.MAX_VALUE;
        }
    }
}
