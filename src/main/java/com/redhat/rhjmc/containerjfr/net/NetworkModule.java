package com.redhat.rhjmc.containerjfr.net;

import javax.inject.Singleton;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnectionToolkit;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.net.internal.reports.ReportsModule;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;

import dagger.Module;
import dagger.Provides;

@Module(includes = {
        ReportsModule.class
})
public abstract class NetworkModule {
    @Provides
    @Singleton
    static HttpServer provideHttpServer(NetworkConfiguration netConf, Logger logger) {
        return new HttpServer(netConf, logger);
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
    static JFRConnectionToolkit provideJFRConnectionToolkit(ClientWriter cw) {
        return new JFRConnectionToolkit(cw);
    }

    @Provides
    static CloseableHttpClient provideHttpClient() {
        return HttpClients.createMinimal(new BasicHttpClientConnectionManager());
    }
}
