/*-
 * #%L
 * Container JFR
 * %%
 * Copyright (C) 2020 Red Hat, Inc.
 * %%
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
 * #L%
 */
package com.redhat.rhjmc.containerjfr.platform;

import java.util.Objects;
import java.util.Set;

import javax.inject.Singleton;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.net.discovery.JvmDiscoveryClient;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.net.AuthManager;
import com.redhat.rhjmc.containerjfr.platform.internal.PlatformDetectionStrategy;
import com.redhat.rhjmc.containerjfr.platform.internal.PlatformStrategyModule;
import com.redhat.rhjmc.containerjfr.platform.openshift.OpenShiftAuthManager;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;

@Module(includes = {PlatformStrategyModule.class})
public abstract class PlatformModule {

    static final String PLATFORM_STRATEGY_ENV_VAR = "CONTAINER_JFR_PLATFORM";
    static final String AUTH_MANAGER_ENV_VAR = "CONTAINER_JFR_AUTH_MANAGER";

    @Provides
    @Singleton
    static PlatformClient providePlatformClient(
            PlatformDetectionStrategy<?> platformStrategy, Environment env, Logger logger) {
        return platformStrategy.getPlatformClient();
    }

    @Provides
    @Singleton
    static AuthManager provideAuthManager(
            PlatformDetectionStrategy<?> platformStrategy,
            Environment env,
            FileSystem fs,
            Set<AuthManager> authManagers,
            Logger logger) {
        final String authManagerClass;
        if (env.hasEnv(AUTH_MANAGER_ENV_VAR)) {
            authManagerClass = env.getEnv(AUTH_MANAGER_ENV_VAR);
            logger.info(String.format("Selecting configured AuthManager \"%s\"", authManagerClass));
        } else {
            authManagerClass = platformStrategy.getAuthManager().getClass().getCanonicalName();
            logger.info(
                    String.format(
                            "Selecting platform default AuthManager \"%s\"", authManagerClass));
        }
        return authManagers.stream()
                .filter(mgr -> Objects.equals(mgr.getClass().getCanonicalName(), authManagerClass))
                .findFirst()
                .orElseThrow(
                        () ->
                                new RuntimeException(
                                        String.format(
                                                "Selected AuthManager \"%s\" is not available",
                                                authManagerClass)));
    }

    @Provides
    @Singleton
    static PlatformDetectionStrategy<?> providePlatformStrategy(
            Logger logger, Set<PlatformDetectionStrategy<?>> strategies, Environment env) {
        if (env.hasEnv(PLATFORM_STRATEGY_ENV_VAR)) {
            String platform = env.getEnv(PLATFORM_STRATEGY_ENV_VAR);
            logger.info(
                    String.format(
                            "Selecting configured PlatformDetectionStrategy \"%s\"", platform));
            for (PlatformDetectionStrategy<?> strat : strategies) {
                if (Objects.equals(platform, strat.getClass().getCanonicalName())) {
                    return strat;
                }
            }
            throw new RuntimeException(
                    String.format("Selected PlatformDetectionStrategy \"%s\" not found", platform));
        }
        return strategies.stream()
                // reverse sort, higher priorities should be earlier in the stream
                .sorted((a, b) -> Integer.compare(b.getPriority(), a.getPriority()))
                .filter(PlatformDetectionStrategy::isAvailable)
                .findFirst()
                .orElseThrow();
    }

    @Provides
    @Singleton
    static JvmDiscoveryClient provideJvmDiscoveryClient(Logger logger) {
        return new JvmDiscoveryClient(logger);
    }

    @Provides
    @Singleton
    static OpenShiftAuthManager provideOpenShiftAuthManager(Logger logger, FileSystem fs) {
        return new OpenShiftAuthManager(logger, fs);
    }

    @Binds
    @IntoSet
    abstract AuthManager bindOpenShiftAuthManager(OpenShiftAuthManager mgr);
}
