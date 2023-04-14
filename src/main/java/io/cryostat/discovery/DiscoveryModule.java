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

import java.time.Duration;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ExecutorService;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.persistence.EntityManager;

import io.cryostat.VerticleDeployer;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.configuration.Variables;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Environment;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.platform.PlatformModule;
import io.cryostat.platform.discovery.AbstractNode;
import io.cryostat.platform.internal.CustomTargetPlatformClient;
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
            Logger logger) {
        return new DiscoveryStorage(
                deployer,
                pingPeriod,
                builtin,
                dao,
                jvmIdHelper,
                credentialsManager,
                matchExpressionEvaluator,
                gson,
                http,
                logger);
    }

    @Provides
    @Singleton
    static BuiltInDiscovery provideBuiltInDiscovery(
            ExecutorService executor,
            DiscoveryStorage storage,
            @Named(PlatformModule.SELECTED_PLATFORMS)
                    SortedSet<PlatformDetectionStrategy<?>> selectedStrategies,
            @Named(PlatformModule.UNSELECTED_PLATFORMS)
                    SortedSet<PlatformDetectionStrategy<?>> unselectedStrategies,
            Lazy<CustomTargetPlatformClient> customTargets,
            NotificationFactory notificationFactory,
            Logger logger) {
        return new BuiltInDiscovery(
                executor,
                storage,
                selectedStrategies,
                unselectedStrategies,
                customTargets,
                notificationFactory,
                logger);
    }

    @Provides
    @IntoSet
    static PluggableTypeAdapter<?> provideBaseNodeTypeAdapter(
            Lazy<Set<PluggableTypeAdapter<?>>> adapters, Logger logger) {
        return new AbstractNodeTypeAdapter(AbstractNode.class, adapters, logger);
    }
}
