package com.redhat.rhjmc.containerjfr.net;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.Set;

import javax.inject.Singleton;

import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.ssl.SSLContextBuilder;

import com.redhat.rhjmc.containerjfr.ExecutionMode;
import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnectionToolkit;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.net.internal.reports.ReportsModule;
import com.redhat.rhjmc.containerjfr.tui.ConnectionMode;

import dagger.Binds;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;

@Module(includes = {ReportsModule.class})
public abstract class NetworkModule {

    static final String AUTH_MANAGER_ENV_VAR = "CONTAINER_JFR_AUTH_MANAGER";

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
    static JFRConnectionToolkit provideJFRConnectionToolkit(ClientWriter cw) {
        return new JFRConnectionToolkit(cw);
    }

    @Provides
    static CloseableHttpClient provideHttpClient(NetworkConfiguration netConf) {
        if (!netConf.isUntrustedSslAllowed()) {
            return HttpClients.createMinimal(new BasicHttpClientConnectionManager());
        }

        try {
            SSLConnectionSocketFactory sslSocketFactory =
                    new SSLConnectionSocketFactory(
                            new SSLContextBuilder()
                                    .loadTrustMaterial(null, new TrustAllStrategy())
                                    .build(),
                            SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

            Registry<ConnectionSocketFactory> registry =
                    RegistryBuilder.<ConnectionSocketFactory>create()
                            .register("http", new PlainConnectionSocketFactory())
                            .register("https", sslSocketFactory)
                            .build();

            return HttpClients.custom()
                    .setSSLSocketFactory(sslSocketFactory)
                    .setConnectionManager(new BasicHttpClientConnectionManager(registry))
                    .build();
        } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
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
    static AuthManager provideAuthManager(
            ExecutionMode mode,
            Environment env,
            FileSystem fs,
            Set<AuthManager> authManagers,
            @ConnectionMode(ExecutionMode.WEBSOCKET) Lazy<AuthManager> platformAuthManager,
            Logger logger) {
        final String authManagerClass;
        if (env.hasEnv(AUTH_MANAGER_ENV_VAR)) {
            authManagerClass = env.getEnv(AUTH_MANAGER_ENV_VAR);
            logger.info(String.format("Selecting configured AuthManager \"%s\"", authManagerClass));
        } else if (ExecutionMode.WEBSOCKET.equals(mode)) {
            authManagerClass = platformAuthManager.get().getClass().getCanonicalName();
            logger.info(
                    String.format(
                            "Selecting platform default AuthManager \"%s\"", authManagerClass));
        } else {
            authManagerClass = NoopAuthManager.class.getCanonicalName();
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
