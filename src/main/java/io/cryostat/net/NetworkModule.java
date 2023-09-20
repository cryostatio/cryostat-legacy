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
package io.cryostat.net;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Executors;

import javax.inject.Named;
import javax.inject.Singleton;

import io.cryostat.configuration.ConfigurationModule;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.configuration.Variables;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.JFRConnectionToolkit;
import io.cryostat.core.sys.Environment;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.core.tui.ClientWriter;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.net.openshift.OpenShiftNetworkModule;
import io.cryostat.net.reports.ReportsModule;
import io.cryostat.net.security.SecurityModule;
import io.cryostat.net.web.WebModule;
import io.cryostat.net.web.http.HttpModule;
import io.cryostat.recordings.JvmIdHelper;

import com.github.benmanes.caffeine.cache.Scheduler;
import com.google.gson.Gson;
import dagger.Binds;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

@Module(
        includes = {
            WebModule.class,
            ReportsModule.class,
            SecurityModule.class,
            OpenShiftNetworkModule.class,
        })
public abstract class NetworkModule {

    @Provides
    @Singleton
    static HttpServer provideHttpServer(
            Vertx vertx, NetworkConfiguration netConf, SslConfiguration sslConf, Logger logger) {
        return new HttpServer(vertx, netConf, sslConf, logger);
    }

    @Provides
    @Singleton
    static NetworkConfiguration provideNetworkConfiguration(
            Environment env, NetworkResolver resolver) {
        return new NetworkConfiguration(env, resolver);
    }

    @Provides
    @Singleton
    static NetworkResolver provideNetworkResolver() {
        return new NetworkResolver();
    }

    @Provides
    @Named(Variables.TARGET_MAX_CONCURRENT_CONNECTIONS)
    static int provideMaxTargetConnections(Environment env) {
        return Integer.parseInt(env.getEnv(Variables.TARGET_MAX_CONCURRENT_CONNECTIONS, "-1"));
    }

    @Provides
    @Named(Variables.TARGET_CACHE_TTL)
    static Duration provideMaxTargetTTL(Environment env) {
        return Duration.ofSeconds(
                Math.max(1, Integer.parseInt(env.getEnv(Variables.TARGET_CACHE_TTL, "10"))));
    }

    @Provides
    @Singleton
    static AgentConnection.Factory provideAgentConnectionFactory(
            AgentClient.Factory clientFactory,
            JvmIdHelper idHelper,
            FileSystem fs,
            Environment env,
            Logger logger) {
        return new AgentConnection.Factory(clientFactory, idHelper, fs, env, logger);
    }

    @Provides
    @Singleton
    static AgentClient.Factory provideAgentClientFactory(
            Gson gson,
            @Named(HttpModule.HTTP_REQUEST_TIMEOUT_SECONDS) long httpTimeout,
            WebClient webClient,
            CredentialsManager credentialsManager,
            Logger logger) {
        return new AgentClient.Factory(
                Executors.newCachedThreadPool(),
                gson,
                httpTimeout,
                webClient,
                credentialsManager,
                logger);
    }

    @Provides
    @Singleton
    static TargetConnectionManager provideTargetConnectionManager(
            Lazy<JFRConnectionToolkit> connectionToolkit,
            Lazy<AgentConnection.Factory> agentConnectionFactory,
            DiscoveryStorage storage,
            @Named(Variables.TARGET_CACHE_TTL) Duration maxTargetTtl,
            @Named(Variables.TARGET_MAX_CONCURRENT_CONNECTIONS) int maxTargetConnections,
            @Named(Variables.JMX_CONNECTION_TIMEOUT) long connectionTimeoutSeconds,
            Logger logger) {
        return new TargetConnectionManager(
                connectionToolkit,
                agentConnectionFactory,
                storage,
                Executors.newCachedThreadPool(),
                Scheduler.systemScheduler(),
                maxTargetTtl,
                maxTargetConnections,
                connectionTimeoutSeconds,
                logger);
    }

    @Provides
    @Singleton
    static JFRConnectionToolkit provideJFRConnectionToolkit(
            ClientWriter cw, FileSystem fs, Environment env) {
        return new JFRConnectionToolkit(cw, fs, env);
    }

    @Provides
    @Singleton
    static Vertx provideVertx() {
        VertxOptions defaults = new VertxOptions();
        return Vertx.vertx(
                new VertxOptions()
                        .setPreferNativeTransport(true)
                        .setEventLoopPoolSize(defaults.getEventLoopPoolSize() + 6));
    }

    @Provides
    @Singleton
    static WebClient provideWebClient(Vertx vertx, NetworkConfiguration netConf) {
        try {
            WebClientOptions opts =
                    new WebClientOptions()
                            .setSsl(true)
                            .setDefaultHost(netConf.getWebServerHost())
                            .setDefaultPort(netConf.getExternalWebServerPort())
                            .setFollowRedirects(true)
                            .setTryUseCompression(true);
            if (netConf.isUntrustedSslAllowed()) {
                opts = opts.setTrustAll(true).setVerifyHost(false);
            }
            return WebClient.create(vertx, opts);
        } catch (SocketException | UnknownHostException e) {
            throw new RuntimeException(e); // @Provides methods may only throw unchecked exceptions
        }
    }

    @Provides
    @Singleton
    static SslConfiguration provideSslConfiguration(Environment env, FileSystem fs, Logger logger) {
        try {
            return new SslConfiguration(env, fs, logger);
        } catch (SslConfiguration.SslConfigurationException e) {
            throw new RuntimeException(e); // @Provides methods may only throw unchecked exceptions
        }
    }

    @Provides
    @Singleton
    static NoopAuthManager provideNoopAuthManager(Logger logger, FileSystem fs) {
        return new NoopAuthManager(logger);
    }

    @Binds
    @IntoSet
    abstract AuthManager bindNoopAuthManager(NoopAuthManager mgr);

    @Provides
    @Singleton
    static BasicAuthManager provideBasicAuthManager(
            Logger logger,
            FileSystem fs,
            @Named(ConfigurationModule.CONFIGURATION_PATH) Path confDir) {
        return new BasicAuthManager(logger, fs, confDir);
    }

    @Binds
    @IntoSet
    abstract AuthManager bindBasicAuthManager(BasicAuthManager mgr);
}
