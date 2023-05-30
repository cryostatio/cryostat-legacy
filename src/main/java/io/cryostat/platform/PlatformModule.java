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
package io.cryostat.platform;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

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
        Set<PlatformDetectionStrategy<?>> selectedStrategies = new HashSet<>();
        selectedStrategies.add(customTargets);
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
        for (PlatformDetectionStrategy<?> s : platformStrategies) {
            if (fn.test(s)) {
                selectedStrategies.add(s);
            }
        }
        return selectedStrategies;
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
