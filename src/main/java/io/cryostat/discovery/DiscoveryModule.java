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

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.Executors;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.persistence.EntityManager;

import io.cryostat.VerticleDeployer;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.configuration.Variables;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Clock;
import io.cryostat.core.sys.Environment;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.platform.PlatformModule;
import io.cryostat.platform.discovery.AbstractNode;
import io.cryostat.platform.internal.PlatformDetectionStrategy;
import io.cryostat.recordings.JvmIdHelper;
import io.cryostat.rules.MatchExpressionEvaluator;
import io.cryostat.util.PluggableTypeAdapter;

import com.google.gson.Gson;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import io.vertx.ext.web.client.WebClient;

@Module
public abstract class DiscoveryModule {

    public static final String DISCOVERY_PING_DURATION = "DISCOVERY_PING_DURATION";

    @Provides
    @Singleton
    @Named(DISCOVERY_PING_DURATION)
    static Duration provideDiscoveryPingDuration(Environment env) {
        String d =
                env.getEnv(
                        Variables.DISCOVERY_PING_PERIOD_MS,
                        String.valueOf(Duration.ofMinutes(5).toMillis()));
        return Duration.ofMillis(Long.parseLong(d));
    }

    @Provides
    @Singleton
    static PluginInfoDao providePluginInfoDao(EntityManager em, Gson gson, Logger logger) {
        return new PluginInfoDao(em, gson, logger);
    }

    @Provides
    @Singleton
    static DiscoveryStorage provideDiscoveryStorage(
            VerticleDeployer deployer,
            @Named(DISCOVERY_PING_DURATION) Duration pingPeriod,
            Lazy<BuiltInDiscovery> builtin,
            PluginInfoDao dao,
            Lazy<JvmIdHelper> jvmIdHelper,
            Lazy<CredentialsManager> credentialsManager,
            Lazy<MatchExpressionEvaluator> matchExpressionEvaluator,
            Gson gson,
            WebClient http,
            Clock clock,
            Logger logger) {
        return new DiscoveryStorage(
                deployer,
                Executors.newSingleThreadScheduledExecutor(),
                Executors.newCachedThreadPool(),
                pingPeriod,
                builtin,
                dao,
                jvmIdHelper,
                credentialsManager,
                matchExpressionEvaluator,
                gson,
                http,
                clock,
                logger);
    }

    @Provides
    @Singleton
    static BuiltInDiscovery provideBuiltInDiscovery(
            DiscoveryStorage storage,
            @Named(PlatformModule.SELECTED_PLATFORMS)
                    Set<PlatformDetectionStrategy<?>> selectedStrategies,
            @Named(PlatformModule.UNSELECTED_PLATFORMS)
                    Set<PlatformDetectionStrategy<?>> unselectedStrategies,
            NotificationFactory notificationFactory,
            Logger logger) {
        return new BuiltInDiscovery(
                storage, selectedStrategies, unselectedStrategies, notificationFactory, logger);
    }

    @Provides
    @IntoSet
    static PluggableTypeAdapter<?> provideBaseNodeTypeAdapter(
            Lazy<Set<PluggableTypeAdapter<?>>> adapters, Logger logger) {
        return new AbstractNodeTypeAdapter(AbstractNode.class, adapters, logger);
    }
}
