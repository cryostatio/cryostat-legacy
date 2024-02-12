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
package io.cryostat.platform.internal;

import java.util.Set;

import javax.inject.Named;
import javax.inject.Singleton;

import io.cryostat.core.log.Logger;
import io.cryostat.core.net.JFRConnectionToolkit;
import io.cryostat.core.net.discovery.JvmDiscoveryClient;
import io.cryostat.core.sys.Environment;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.net.NoopAuthManager;
import io.cryostat.net.openshift.OpenShiftAuthManager;

import com.google.gson.Gson;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ElementsIntoSet;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;

@Module
public abstract class PlatformStrategyModule {

    public static final String UNIX_SOCKET_WEBCLIENT = "UNIX_SOCKET_WEBCLIENT";

    @Provides
    @Singleton
    @Named(UNIX_SOCKET_WEBCLIENT)
    static WebClient provideUnixSocketWebClient(Vertx vertx) {
        return WebClient.create(vertx);
    }

    @Provides
    @Singleton
    static CustomTargetPlatformClient provideCustomTargetPlatformClient(
            Environment environment, Lazy<DiscoveryStorage> storage) {
        return new CustomTargetPlatformClient(environment, storage);
    }

    @Provides
    @Singleton
    static CustomTargetPlatformStrategy provideCustomTargetPlatformStrategy(
            Logger logger,
            Lazy<NoopAuthManager> noopAuthManager,
            Lazy<CustomTargetPlatformClient> client) {
        return new CustomTargetPlatformStrategy(logger, noopAuthManager, client);
    }

    @Provides
    @Singleton
    static OpenShiftPlatformStrategy provideOpenShiftPlatformStrategy(
            Lazy<OpenShiftAuthManager> authManager, Environment env, FileSystem fs, Logger logger) {
        return new OpenShiftPlatformStrategy(authManager, env, fs, logger);
    }

    @Provides
    @Singleton
    static KubeApiPlatformStrategy provideKubeApiPlatformStrategy(
            Lazy<NoopAuthManager> noopAuthManager, Environment env, FileSystem fs, Logger logger) {
        return new KubeApiPlatformStrategy(noopAuthManager, env, fs, logger);
    }

    @Provides
    @Singleton
    static PodmanPlatformStrategy providePodmanPlatformStrategy(
            Lazy<NoopAuthManager> noopAuthManager,
            @Named(UNIX_SOCKET_WEBCLIENT) Lazy<WebClient> webClient,
            Lazy<Vertx> vertx,
            Lazy<JFRConnectionToolkit> connectionToolkit,
            Gson gson,
            Environment environment,
            FileSystem fs,
            Logger logger) {
        return new PodmanPlatformStrategy(
                noopAuthManager,
                webClient,
                vertx,
                connectionToolkit,
                gson,
                environment,
                fs,
                logger);
    }

    @Provides
    @Singleton
    static DockerPlatformStrategy provideDockerPlatformStrategy(
            Lazy<NoopAuthManager> noopAuthManager,
            @Named(UNIX_SOCKET_WEBCLIENT) Lazy<WebClient> webClient,
            Lazy<Vertx> vertx,
            Lazy<JFRConnectionToolkit> connectionToolkit,
            Gson gson,
            Environment environment,
            FileSystem fs,
            Logger logger) {
        return new DockerPlatformStrategy(
                noopAuthManager,
                webClient,
                vertx,
                connectionToolkit,
                gson,
                environment,
                fs,
                logger);
    }

    @Provides
    @Singleton
    static DefaultPlatformStrategy provideDefaultPlatformStrategy(
            Environment environment, Lazy<NoopAuthManager> noopAuthManager, Logger logger) {
        return new DefaultPlatformStrategy(
                environment, noopAuthManager, () -> new JvmDiscoveryClient(logger), logger);
    }

    @Provides
    @ElementsIntoSet
    static Set<PlatformDetectionStrategy<?>> providePlatformDetectionStrategies(
            CustomTargetPlatformStrategy customTargets,
            OpenShiftPlatformStrategy openShift,
            KubeApiPlatformStrategy kubeApi,
            PodmanPlatformStrategy podman,
            DockerPlatformStrategy docker,
            DefaultPlatformStrategy jdp) {
        return Set.of(customTargets, openShift, kubeApi, podman, docker, jdp);
    }
}
