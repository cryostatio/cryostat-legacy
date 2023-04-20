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
package io.cryostat.net;

import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;

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
            AgentClient.Factory clientFactory, JvmIdHelper idHelper, Logger logger) {
        return new AgentConnection.Factory(clientFactory, idHelper, logger);
    }

    @Provides
    @Singleton
    static AgentClient.Factory provideAgentClientFactory(
            Vertx vertx,
            ExecutorService executor,
            Gson gson,
            @Named(HttpModule.HTTP_REQUEST_TIMEOUT_SECONDS) long httpTimeout,
            WebClient webClient,
            CredentialsManager credentialsManager,
            Logger logger) {
        return new AgentClient.Factory(
                vertx, executor, gson, httpTimeout, webClient, credentialsManager, logger);
    }

    @Provides
    @Singleton
    static TargetConnectionManager provideTargetConnectionManager(
            Lazy<JFRConnectionToolkit> connectionToolkit,
            Lazy<AgentConnection.Factory> agentConnectionFactory,
            DiscoveryStorage storage,
            @Named(Variables.TARGET_CACHE_TTL) Duration maxTargetTtl,
            @Named(Variables.TARGET_MAX_CONCURRENT_CONNECTIONS) int maxTargetConnections,
            @Named(Variables.JMX_CONNECTION_TIMEOUT) long jmxConnectionTimeout,
            ExecutorService executor,
            Logger logger) {
        return new TargetConnectionManager(
                connectionToolkit,
                agentConnectionFactory,
                storage,
                executor,
                Scheduler.systemScheduler(),
                maxTargetTtl,
                maxTargetConnections,
                jmxConnectionTimeout,
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
        return Vertx.vertx(new VertxOptions().setPreferNativeTransport(true));
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
