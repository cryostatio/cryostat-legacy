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
