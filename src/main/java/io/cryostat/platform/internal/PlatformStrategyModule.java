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
            Lazy<DiscoveryStorage> storage) {
        return new CustomTargetPlatformClient(storage);
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
            Logger logger,
            Lazy<OpenShiftAuthManager> authManager,
            Lazy<JFRConnectionToolkit> connectionToolkit,
            Environment env,
            FileSystem fs) {
        return new OpenShiftPlatformStrategy(logger, authManager, connectionToolkit, env, fs);
    }

    @Provides
    @Singleton
    static KubeApiPlatformStrategy provideKubeApiPlatformStrategy(
            Logger logger,
            Lazy<NoopAuthManager> noopAuthManager,
            Lazy<JFRConnectionToolkit> connectionToolkit,
            Environment env,
            FileSystem fs) {
        return new KubeApiPlatformStrategy(logger, noopAuthManager, connectionToolkit, env, fs);
    }

    @Provides
    @Singleton
    static PodmanPlatformStrategy providePodmanPlatformStrategy(
            Logger logger,
            Lazy<NoopAuthManager> noopAuthManager,
            @Named(UNIX_SOCKET_WEBCLIENT) Lazy<WebClient> webClient,
            Lazy<Vertx> vertx,
            Lazy<JFRConnectionToolkit> connectionToolkit,
            Gson gson,
            FileSystem fs) {
        return new PodmanPlatformStrategy(
                logger, noopAuthManager, webClient, vertx, connectionToolkit, gson, fs);
    }

    @Provides
    @Singleton
    static DefaultPlatformStrategy provideDefaultPlatformStrategy(
            Logger logger, Lazy<NoopAuthManager> noopAuthManager) {
        return new DefaultPlatformStrategy(
                logger, noopAuthManager, () -> new JvmDiscoveryClient(logger));
    }

    @Provides
    @ElementsIntoSet
    static Set<PlatformDetectionStrategy<?>> providePlatformDetectionStrategies(
            CustomTargetPlatformStrategy customTargets,
            OpenShiftPlatformStrategy openShift,
            KubeApiPlatformStrategy kubeApi,
            PodmanPlatformStrategy podman,
            DefaultPlatformStrategy jdp) {
        return Set.of(customTargets, openShift, kubeApi, podman, jdp);
    }
}
