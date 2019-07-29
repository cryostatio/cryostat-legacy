package com.redhat.rhjmc.containerjfr.net;

import java.nio.file.Path;

import javax.inject.Named;
import javax.inject.Singleton;

import com.redhat.rhjmc.containerjfr.core.net.JFRConnectionToolkit;
import com.redhat.rhjmc.containerjfr.core.sys.Clock;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;

@Module
public abstract class NetworkModule {
    @Binds
    @IntoSet
    abstract ConnectionListener bindRecordingExporter(RecordingExporter exporter);

    @Provides
    @Singleton
    static RecordingExporter provideRecordingExporter(@Named("RECORDINGS_PATH") Path recordingsPath, Environment env, ClientWriter cw, NetworkResolver resolver) {
        return new RecordingExporter(recordingsPath, env, cw, resolver);
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
