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
package com.redhat.rhjmc.containerjfr.net;

import java.net.SocketException;
import java.net.UnknownHostException;

import javax.inject.Singleton;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnectionToolkit;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.net.internal.reports.ReportsModule;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

@Module(includes = {ReportsModule.class})
public abstract class NetworkModule {

    @Provides
    @Singleton
    static HttpServer provideHttpServer(
            NetworkConfiguration netConf, SslConfiguration sslConf, Logger logger) {
        return new HttpServer(netConf, sslConf, logger);
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
    @Singleton
    static TargetConnectionManager provideTargetConnectionManager(Logger logger, ClientWriter cw) {
        return new TargetConnectionManager(logger, new JFRConnectionToolkit(cw));
    }

    @Provides
    @Singleton
    static Vertx provideVertx() {
        return Vertx.vertx();
    }

    @Provides
    @Singleton
    static WebClient provideWebClient(Vertx vertx, NetworkConfiguration netConf) {
        try {
            WebClientOptions opts =
                    new WebClientOptions()
                            .setSsl(true)
                            .setDefaultHost(netConf.getWebServerHost())
                            .setDefaultPort(netConf.getExternalWebServerPort());
            if (!netConf.isUntrustedSslAllowed()) {
                opts = opts.setTrustAll(true).setVerifyHost(false);
            }
            return WebClient.create(vertx, opts);
        } catch (SocketException | UnknownHostException e) {
            throw new RuntimeException(e); // @Provides methods may only throw unchecked exceptions
        }
    }

    @Provides
    @Singleton
    static SslConfiguration provideSslConfiguration(Environment env, FileSystem fs) {
        try {
            return new SslConfiguration(env, fs);
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
    static BasicAuthManager provideBasicAuthManager(Logger logger, FileSystem fs) {
        return new BasicAuthManager(logger, fs);
    }

    @Binds
    @IntoSet
    abstract AuthManager bindBasicAuthManager(BasicAuthManager mgr);
}
