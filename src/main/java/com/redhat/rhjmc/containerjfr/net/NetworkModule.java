package com.redhat.rhjmc.containerjfr.net;

import java.nio.file.Path;

import javax.inject.Named;
import javax.inject.Singleton;

import com.redhat.rhjmc.containerjfr.core.net.JFRConnectionToolkit;
import com.redhat.rhjmc.containerjfr.core.sys.Clock;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.core.util.log.Logger;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;

@Module
public abstract class NetworkModule {
    @Binds
    @IntoSet
    abstract ConnectionListener bindWebServer(WebServer exporter);

    @Provides
    @Singleton
    static WebServer provideWebServer(NetworkConfiguration netConf, @Named("RECORDINGS_PATH") Path recordingsPath, Logger logger) {
        return new WebServer(netConf, recordingsPath, logger);
    }

    @Provides
    @Singleton
    static NetworkConfiguration provideNetworkConfiguration(Environment env, NetworkResolver resolver) {
        return new NetworkConfiguration(env, resolver);
    }

    @Provides
    @Singleton
    static NetworkResolver provideNetworkResolver() {
        return new NetworkResolver();
    }

    @Provides
    @Singleton
    static JFRConnectionToolkit provideJFRConnectionToolkit(ClientWriter cw, Clock clock) {
        return new JFRConnectionToolkit(cw, clock);
    }

    @Provides
    static CloseableHttpClient provideHttpClient() {
        return HttpClients.createMinimal(new BasicHttpClientConnectionManager());
    }
}
