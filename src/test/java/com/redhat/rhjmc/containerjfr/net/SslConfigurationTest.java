package com.redhat.rhjmc.containerjfr.net;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import io.vertx.core.http.HttpServerOptions;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openjdk.jmc.common.util.Pair;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SslConfigurationTest {
    @Mock Environment env;
    @Mock FileSystem fs;
    @Mock Logger logger;
    
    SslConfiguration sslConf;
    
    @BeforeEach
    void setup() {
        sslConf = new SslConfiguration(env, fs, logger, null, null, null, null);
    }
    
    @Test
    void testDefaultSslConfiguration() {
        MatcherAssert.assertThat(sslConf.enabled(), Matchers.equalTo(false));
    }
    
    @Test
    void shouldObtainKeyStorePathIfSpecified() {
        Path path = mock(Path.class);
        
        when(env.hasEnv("KEYSTORE_PATH")).thenReturn(true);
        when(env.getEnv("KEYSTORE_PATH")).thenReturn("foobar");
        when(fs.pathOf("foobar")).thenReturn(path);
        when(path.normalize()).thenReturn(path);
        when(fs.exists(path)).thenReturn(true);
        MatcherAssert.assertThat(sslConf.obtainKeyStorePathIfSpecified(), Matchers.equalTo(path));
    }
    
    @Test
    void shouldReturnNullIfKeyStorePathUnspecified() {
        when(env.hasEnv("KEYSTORE_PATH")).thenReturn(false);

        MatcherAssert.assertThat(sslConf.obtainKeyStorePathIfSpecified(), Matchers.equalTo(null));
        verifyNoMoreInteractions(env);
        verifyNoMoreInteractions(fs);
    }
    
    @Test
    void shouldThrowIfKeyStorePathRefersToANonExistentFile() {
        Path dne = mock(Path.class);
        
        when(env.hasEnv("KEYSTORE_PATH")).thenReturn(true);
        when(env.getEnv("KEYSTORE_PATH")).thenReturn("foobar");
        when(fs.pathOf("foobar")).thenReturn(dne);
        when(dne.normalize()).thenReturn(dne);
        when(fs.exists(dne)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> sslConf.obtainKeyStorePathIfSpecified());
    }
    
    @Test 
    void shouldDiscoverJksKeyStoreIfExists() {
        Path home = mock(Path.class);
        Path dst = mock(Path.class);
        
        when(fs.pathOf(System.getProperty("user.home", "/"))).thenReturn(home);
        when(home.resolve("container-jfr-keystore.jks")).thenReturn(dst);
        when(fs.exists(dst)).thenReturn(true);
        
        MatcherAssert.assertThat(sslConf.discoverKeyStorePathInDefaultLocations(), Matchers.equalTo(dst));
        
        verifyNoMoreInteractions(fs);
        verifyNoMoreInteractions(home);
    }
    
    @Test
    void shouldDiscoverPfxKeyStoreIfExists() {
        Path home = mock(Path.class);
        Path dst = mock(Path.class);
        Path dne = mock(Path.class);
        
        when(fs.pathOf(System.getProperty("user.home", "/"))).thenReturn(home);
        
        when(home.resolve(anyString())).thenReturn(dne);
        when(fs.exists(dne)).thenReturn(false);
        
        when(home.resolve("container-jfr-keystore.pfx")).thenReturn(dst);
        when(fs.exists(dst)).thenReturn(true);

        MatcherAssert.assertThat(sslConf.discoverKeyStorePathInDefaultLocations(), Matchers.equalTo(dst));

        verifyNoMoreInteractions(fs);
        verifyNoMoreInteractions(home);
    }

    @Test
    void shouldDiscoverP12KeyStoreIfExists() {
        Path home = mock(Path.class);
        Path dst = mock(Path.class);
        Path dne = mock(Path.class);

        when(fs.pathOf(System.getProperty("user.home", "/"))).thenReturn(home);

        when(home.resolve(anyString())).thenReturn(dne);
        when(fs.exists(dne)).thenReturn(false);

        when(home.resolve("container-jfr-keystore.p12")).thenReturn(dst);
        when(fs.exists(dst)).thenReturn(true);

        MatcherAssert.assertThat(sslConf.discoverKeyStorePathInDefaultLocations(), Matchers.equalTo(dst));

        verifyNoMoreInteractions(fs);
        verifyNoMoreInteractions(home);
    }
    
    @Test
    void shouldReturnNullIfNoKeyStoreInDefaultLocations() {
        Path home = mock(Path.class);
        Path dne = mock(Path.class);

        when(fs.pathOf(System.getProperty("user.home", "/"))).thenReturn(home);
        when(home.resolve(anyString())).thenReturn(dne);
        when(fs.exists(dne)).thenReturn(false);

        MatcherAssert.assertThat(sslConf.discoverKeyStorePathInDefaultLocations(), Matchers.equalTo(null));

        InOrder inOrder = inOrder(home);
        inOrder.verify(home).resolve("container-jfr-keystore.jks");
        inOrder.verify(home).resolve("container-jfr-keystore.pfx");
        inOrder.verify(home).resolve("container-jfr-keystore.p12");
        
        verify(fs, times(3)).exists(dne);
        verifyNoMoreInteractions(fs);
        verifyNoMoreInteractions(home);
    }

    @Test
    void shouldObtainKeyCertPathPairIfSpecified() {
        Path key = mock(Path.class);
        Path cert = mock(Path.class);
        
        when(env.hasEnv("KEY_PATH")).thenReturn(true);
        when(env.getEnv("KEY_PATH")).thenReturn("foo");
        when(fs.pathOf("foo")).thenReturn(key);
        when(key.normalize()).thenReturn(key);
        when(fs.exists(key)).thenReturn(true);

        when(env.hasEnv("CERT_PATH")).thenReturn(true);
        when(env.getEnv("CERT_PATH")).thenReturn("bar");
        when(fs.pathOf("bar")).thenReturn(cert);
        when(cert.normalize()).thenReturn(cert);
        when(fs.exists(cert)).thenReturn(true);

        Pair<Path, Path> pair = sslConf.obtainKeyCertPathPairIfSpecified();
        MatcherAssert.assertThat(pair.left, Matchers.equalTo(key));
        MatcherAssert.assertThat(pair.right, Matchers.equalTo(cert));

        verifyNoMoreInteractions(fs);
        verifyNoMoreInteractions(env);
    }
    
    @Test
    void shouldReturnNullIfKeyCertPathsUnspecified() {
        when(env.hasEnv("KEY_PATH")).thenReturn(false);
        when(env.hasEnv("CERT_PATH")).thenReturn(false);

        MatcherAssert.assertThat(sslConf.obtainKeyCertPathPairIfSpecified(), Matchers.equalTo(null));

        verifyNoMoreInteractions(fs);
        verifyNoMoreInteractions(env);
    }
    
    @Test
    void shouldThrowIfKeyPathUnspecified() {
        when(env.hasEnv("KEY_PATH")).thenReturn(false);
        when(env.hasEnv("CERT_PATH")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> sslConf.obtainKeyCertPathPairIfSpecified());

        verifyNoMoreInteractions(fs);
        verifyNoMoreInteractions(env);
    }

    @Test
    void shouldThrowIfCertPathUnspecified() {
        when(env.hasEnv("KEY_PATH")).thenReturn(true);
        when(env.hasEnv("CERT_PATH")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> sslConf.obtainKeyCertPathPairIfSpecified());

        verifyNoMoreInteractions(fs);
        verifyNoMoreInteractions(env);
    }
    
    @Test
    void shouldThrowIfKeyPathRefersToANonExistentFile() {
        Path key = mock(Path.class);
        Path cert = mock(Path.class);
        
        when(env.hasEnv("KEY_PATH")).thenReturn(true);
        when(env.getEnv("KEY_PATH")).thenReturn("foo");
        when(fs.pathOf("foo")).thenReturn(key);
        when(key.normalize()).thenReturn(key);
        when(fs.exists(key)).thenReturn(false);

        when(env.hasEnv("CERT_PATH")).thenReturn(true);
        when(env.getEnv("CERT_PATH")).thenReturn("bar");
        when(fs.pathOf("bar")).thenReturn(cert);
        when(cert.normalize()).thenReturn(cert);

        assertThrows(IllegalArgumentException.class, () -> sslConf.obtainKeyCertPathPairIfSpecified());
        
        verifyNoMoreInteractions(fs);
        verifyNoMoreInteractions(env);
    }

    @Test
    void shouldThrowIfCertPathRefersToANonExistentFile() {
        Path key = mock(Path.class);
        Path cert = mock(Path.class);

        when(env.hasEnv("KEY_PATH")).thenReturn(true);
        when(env.getEnv("KEY_PATH")).thenReturn("foo");
        when(fs.pathOf("foo")).thenReturn(key);
        when(key.normalize()).thenReturn(key);
        when(fs.exists(key)).thenReturn(true);

        when(env.hasEnv("CERT_PATH")).thenReturn(true);
        when(env.getEnv("CERT_PATH")).thenReturn("bar");
        when(fs.pathOf("bar")).thenReturn(cert);
        when(cert.normalize()).thenReturn(cert);
        when(fs.exists(cert)).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> sslConf.obtainKeyCertPathPairIfSpecified());

        verifyNoMoreInteractions(fs);
        verifyNoMoreInteractions(env);
    }
    
    @Test
    void shouldDiscoverKeyCertPathPairInDefaultLocations() {
        Path home = mock(Path.class);
        Path key = mock(Path.class);
        Path cert = mock(Path.class);

        when(fs.pathOf(System.getProperty("user.home", "/"))).thenReturn(home);
        
        when(home.resolve("container-jfr-key.pem")).thenReturn(key);
        when(fs.exists(key)).thenReturn(true);
        
        when(home.resolve("container-jfr-cert.pem")).thenReturn(cert);
        when(fs.exists(cert)).thenReturn(true);

        Pair<Path, Path> pair = sslConf.discoverKeyCertPathPairInDefaultLocations();
        MatcherAssert.assertThat(pair.left, Matchers.equalTo(key));
        MatcherAssert.assertThat(pair.right, Matchers.equalTo(cert));

        verifyNoMoreInteractions(fs);
        verifyNoMoreInteractions(home);
    }

    @Test
    void shouldReturnNullIfKeyFileNotDiscovered() {
        Path home = mock(Path.class);
        Path key = mock(Path.class);
        Path cert = mock(Path.class);

        when(fs.pathOf(System.getProperty("user.home", "/"))).thenReturn(home);

        when(home.resolve("container-jfr-key.pem")).thenReturn(key);
        when(fs.exists(key)).thenReturn(false);

        when(home.resolve("container-jfr-cert.pem")).thenReturn(cert);
        when(fs.exists(cert)).thenReturn(true);

        MatcherAssert.assertThat(sslConf.discoverKeyCertPathPairInDefaultLocations(), Matchers.equalTo(null));

        verifyNoMoreInteractions(fs);
        verifyNoMoreInteractions(home);
    }

    @Test
    void shouldReturnNullIfCertFileNotDiscovered() {
        Path home = mock(Path.class);
        Path key = mock(Path.class);
        Path cert = mock(Path.class);

        when(fs.pathOf(System.getProperty("user.home", "/"))).thenReturn(home);

        when(home.resolve("container-jfr-key.pem")).thenReturn(key);
        when(fs.exists(key)).thenReturn(true);

        when(home.resolve("container-jfr-cert.pem")).thenReturn(cert);
        when(fs.exists(cert)).thenReturn(false);

        MatcherAssert.assertThat(sslConf.discoverKeyCertPathPairInDefaultLocations(), Matchers.equalTo(null));

        verifyNoMoreInteractions(fs);
        verifyNoMoreInteractions(home);
    }

    @Test
    void shouldSetSslToFalseIfDisabled() {
        MatcherAssert.assertThat(sslConf.enabled(), Matchers.equalTo(false));

        HttpServerOptions options = mock(HttpServerOptions.class);
        when(options.setSsl(anyBoolean())).thenReturn(options);
        
        MatcherAssert.assertThat(sslConf.setToHttpServerOptions(options), Matchers.equalTo(options));
        
        verify(options).setSsl(false);
        verifyNoMoreInteractions(options);
    }
}
