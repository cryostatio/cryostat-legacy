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

import io.cryostat.core.sys.Environment;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

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
    void shouldReportWebServerHost() throws SocketException, UnknownHostException {
        Mockito.when(resolver.getHostAddress()).thenReturn("foo");
        Mockito.when(env.getEnv(Mockito.eq("CRYOSTAT_WEB_HOST"), Mockito.anyString()))
                .thenReturn("bar");
        MatcherAssert.assertThat(conf.getWebServerHost(), Matchers.equalTo("bar"));
        Mockito.verify(resolver).getHostAddress();
        Mockito.verify(env).getEnv("CRYOSTAT_WEB_HOST", "foo");
    }

    @Test
    void shouldReportInternalWebServerPort() {
        Mockito.when(env.getEnv(Mockito.eq("CRYOSTAT_WEB_PORT"), Mockito.anyString()))
                .thenReturn("1234");
        MatcherAssert.assertThat(conf.getInternalWebServerPort(), Matchers.equalTo(1234));
        Mockito.verify(env).getEnv("CRYOSTAT_WEB_PORT", "8181");
    }

    @Test
    void shouldReportExternalWebServerPort() {
        Mockito.when(env.getEnv(Mockito.eq("CRYOSTAT_WEB_PORT"), Mockito.anyString()))
                .thenReturn("8282");
        Mockito.when(env.getEnv(Mockito.eq("CRYOSTAT_EXT_WEB_PORT"), Mockito.anyString()))
                .thenReturn("1234");
        MatcherAssert.assertThat(conf.getExternalWebServerPort(), Matchers.equalTo(1234));
        Mockito.verify(env).getEnv("CRYOSTAT_EXT_WEB_PORT", "8282");
        Mockito.verify(env).getEnv("CRYOSTAT_WEB_PORT", "8181");
    }

    @Test
    void shouldReportSslNotProxiedWhenVarUnset() {
        Mockito.when(env.hasEnv("CRYOSTAT_SSL_PROXIED")).thenReturn(false);
        Assertions.assertFalse(conf.isSslProxied());
        Mockito.verify(env).hasEnv("CRYOSTAT_SSL_PROXIED");
    }

    @Test
    void shouldReportSslProxiedWhenVarSet() {
        Mockito.when(env.hasEnv("CRYOSTAT_SSL_PROXIED")).thenReturn(true);
        Assertions.assertTrue(conf.isSslProxied());
        Mockito.verify(env).hasEnv("CRYOSTAT_SSL_PROXIED");
    }
}
