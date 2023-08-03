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

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Named;
import javax.inject.Singleton;

import io.cryostat.configuration.Variables;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Environment;
import io.cryostat.net.AuthManager;
import io.cryostat.platform.discovery.PlatformDiscoveryModule;
import io.cryostat.platform.internal.CustomTargetPlatformStrategy;
import io.cryostat.platform.internal.PlatformDetectionStrategy;
import io.cryostat.platform.internal.PlatformStrategyModule;

import dagger.Module;
import dagger.Provides;

@Module(includes = {PlatformStrategyModule.class, PlatformDiscoveryModule.class})
public abstract class PlatformModule {

    public static final String SELECTED_PLATFORMS = "SELECTED_PLATFORMS";
    public static final String UNSELECTED_PLATFORMS = "UNSELECTED_PLATFORMS";

    @Provides
    @Singleton
    static AuthManager provideAuthManager(
            PlatformDetectionStrategy<?> platformStrategy,
            Environment env,
            Set<AuthManager> authManagers,
            Logger logger) {
        final String authManagerClass;
        if (env.hasEnv(Variables.AUTH_MANAGER_ENV_VAR)) {
            authManagerClass = env.getEnv(Variables.AUTH_MANAGER_ENV_VAR);
            logger.info("Selecting configured AuthManager \"{}\"", authManagerClass);
            return authManagers.stream()
                    .filter(
                            mgr ->
                                    Objects.equals(
                                            mgr.getClass().getCanonicalName(), authManagerClass))
                    .findFirst()
                    .orElseThrow(
                            () ->
                                    new RuntimeException(
                                            String.format(
                                                    "Selected AuthManager \"%s\" is not available",
                                                    authManagerClass)));
        } else {
            AuthManager auth = platformStrategy.getAuthManager();
            logger.info(
                    "Selecting platform default AuthManager \"{}\"",
                    auth.getClass().getCanonicalName());
            return auth;
        }
    }

    @Provides
    @Singleton
    @Named(SELECTED_PLATFORMS)
    static Set<PlatformDetectionStrategy<?>> provideSelectedPlatformStrategies(
            CustomTargetPlatformStrategy customTargets,
            Set<PlatformDetectionStrategy<?>> platformStrategies,
            Environment env) {
        Predicate<PlatformDetectionStrategy<?>> fn;
        if (env.hasEnv(Variables.PLATFORM_STRATEGY_ENV_VAR)) {
            List<String> platforms =
                    Arrays.asList(env.getEnv(Variables.PLATFORM_STRATEGY_ENV_VAR).split(","));
            fn = s -> platforms.contains(s.getClass().getCanonicalName());
        } else if (env.hasEnv(Variables.DISABLE_BUILTIN_DISCOVERY)) {
            fn = s -> false;
        } else {
            fn = PlatformDetectionStrategy::isAvailable;
        }
        return Stream.concat(Stream.of(customTargets), platformStrategies.stream().filter(fn))
                .collect(Collectors.toSet());
    }

    @Provides
    @Singleton
    @Named(UNSELECTED_PLATFORMS)
    static Set<PlatformDetectionStrategy<?>> provideUnselectedPlatformStrategies(
            @Named(SELECTED_PLATFORMS) Set<PlatformDetectionStrategy<?>> selectedStrategies,
            Set<PlatformDetectionStrategy<?>> platformStrategies) {
        Set<PlatformDetectionStrategy<?>> unselected = new HashSet<>();
        unselected.addAll(platformStrategies);
        unselected.removeAll(selectedStrategies);
        return unselected;
    }

    @Provides
    @Singleton
    static PlatformDetectionStrategy<?> providePlatformStrategy(
            @Named(SELECTED_PLATFORMS) Set<PlatformDetectionStrategy<?>> selectedStrategies,
            Set<PlatformDetectionStrategy<?>> strategies) {
        return selectedStrategies.stream()
                .findFirst()
                .orElseThrow(
                        () ->
                                new NoSuchElementException(
                                        String.format(
                                                "No selected platforms found. Available platforms:"
                                                        + " %s",
                                                strategies.stream()
                                                        .map(s -> s.getClass().getCanonicalName())
                                                        .toList())));
    }
}
