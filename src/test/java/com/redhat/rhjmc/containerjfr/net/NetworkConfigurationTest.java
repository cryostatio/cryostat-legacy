package com.redhat.rhjmc.containerjfr.net;

import java.net.SocketException;
import java.net.UnknownHostException;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.redhat.rhjmc.containerjfr.core.sys.Environment;

@ExtendWith(MockitoExtension.class)
class NetworkConfigurationTest {

    @Mock NetworkResolver resolver;
    @Mock Environment env;
    NetworkConfiguration conf;

    @BeforeEach
    void setup() {
        this.conf = new NetworkConfiguration(env, resolver);
    }

    @Test
    void testDefaultWebServerPort() {
        MatcherAssert.assertThat(conf.getDefaultWebServerPort(), Matchers.equalTo(8181));
    }

    @Test
    void testDefaultCommandChannelPort() {
        MatcherAssert.assertThat(conf.getDefaultCommandChannelPort(), Matchers.equalTo(9090));
    }

    @Test
    void shouldReportWebServerHost() throws SocketException, UnknownHostException {
        Mockito.when(resolver.getHostAddress()).thenReturn("foo");
        Mockito.when(env.getEnv(Mockito.eq("CONTAINER_JFR_WEB_HOST"), Mockito.anyString()))
                .thenReturn("bar");
        MatcherAssert.assertThat(conf.getWebServerHost(), Matchers.equalTo("bar"));
        Mockito.verify(resolver).getHostAddress();
        Mockito.verify(env).getEnv("CONTAINER_JFR_WEB_HOST", "foo");
    }

    @Test
    void shouldReportCommandChannelHost() throws SocketException, UnknownHostException {
        Mockito.when(resolver.getHostAddress()).thenReturn("foo");
        Mockito.when(env.getEnv(Mockito.eq("CONTAINER_JFR_WEB_HOST"), Mockito.anyString()))
                .thenReturn("bar");
        Mockito.when(env.getEnv(Mockito.eq("CONTAINER_JFR_LISTEN_HOST"), Mockito.anyString()))
                .thenReturn("baz");
        MatcherAssert.assertThat(conf.getCommandChannelHost(), Matchers.equalTo("baz"));
        Mockito.verify(resolver).getHostAddress();
        Mockito.verify(env).getEnv("CONTAINER_JFR_WEB_HOST", "foo");
        Mockito.verify(env).getEnv("CONTAINER_JFR_LISTEN_HOST", "bar");
    }

    @Test
    void shouldReportInternalWebServerPort() {
        Mockito.when(env.getEnv(Mockito.eq("CONTAINER_JFR_WEB_PORT"), Mockito.anyString()))
                .thenReturn("1234");
        MatcherAssert.assertThat(conf.getInternalWebServerPort(), Matchers.equalTo(1234));
        Mockito.verify(env).getEnv("CONTAINER_JFR_WEB_PORT", "8181");
    }

    @Test
    void shouldReportExternalWebServerPort() {
        Mockito.when(env.getEnv(Mockito.eq("CONTAINER_JFR_WEB_PORT"), Mockito.anyString()))
                .thenReturn("8282");
        Mockito.when(env.getEnv(Mockito.eq("CONTAINER_JFR_EXT_WEB_PORT"), Mockito.anyString()))
                .thenReturn("1234");
        MatcherAssert.assertThat(conf.getExternalWebServerPort(), Matchers.equalTo(1234));
        Mockito.verify(env).getEnv("CONTAINER_JFR_EXT_WEB_PORT", "8282");
        Mockito.verify(env).getEnv("CONTAINER_JFR_WEB_PORT", "8181");
    }

    @Test
    void shouldReportInternalCommandChannelPort() {
        Mockito.when(env.getEnv(Mockito.eq("CONTAINER_JFR_LISTEN_PORT"), Mockito.anyString()))
                .thenReturn("9191");
        MatcherAssert.assertThat(conf.getInternalCommandChannelPort(), Matchers.equalTo(9191));
        Mockito.verify(env).getEnv("CONTAINER_JFR_LISTEN_PORT", "9090");
    }

    @Test
    void shouldReportExternalCommandChannelPort() {
        Mockito.when(env.getEnv(Mockito.eq("CONTAINER_JFR_EXT_LISTEN_PORT"), Mockito.anyString()))
                .thenReturn("9292");
        Mockito.when(env.getEnv(Mockito.eq("CONTAINER_JFR_LISTEN_PORT"), Mockito.anyString()))
                .thenReturn("9191");
        MatcherAssert.assertThat(conf.getExternalCommandChannelPort(), Matchers.equalTo(9292));
        Mockito.verify(env).getEnv("CONTAINER_JFR_EXT_LISTEN_PORT", "9191");
        Mockito.verify(env).getEnv("CONTAINER_JFR_LISTEN_PORT", "9090");
    }

    @Test
    void shouldReportSslNotProxiedWhenVarUnset() {
        Mockito.when(env.hasEnv("CONTAINER_JFR_SSL_PROXIED")).thenReturn(false);
        Assertions.assertFalse(conf.isSslProxied());
        Mockito.verify(env).hasEnv("CONTAINER_JFR_SSL_PROXIED");
    }

    @Test
    void shouldReportSslProxiedWhenVarSet() {
        Mockito.when(env.hasEnv("CONTAINER_JFR_SSL_PROXIED")).thenReturn(true);
        Assertions.assertTrue(conf.isSslProxied());
        Mockito.verify(env).hasEnv("CONTAINER_JFR_SSL_PROXIED");
    }
}
