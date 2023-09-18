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

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

import javax.management.remote.JMXServiceURL;

import io.cryostat.DirectExecutor;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.core.net.JFRConnectionToolkit;
import io.cryostat.platform.PlatformClient;

import com.github.benmanes.caffeine.cache.Scheduler;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class TargetConnectionManagerTest {

    TargetConnectionManager mgr;
    @Mock Logger logger;
    @Mock JFRConnectionToolkit jfrConnectionToolkit;
    @Mock AgentConnection.Factory agentConnectionFactory;
    @Mock PlatformClient platformClient;
    Duration TTL = Duration.ofMillis(250);

    @BeforeEach
    void setup() {
        this.mgr =
                new TargetConnectionManager(
                        () -> jfrConnectionToolkit,
                        () -> agentConnectionFactory,
                        platformClient,
                        new DirectExecutor(),
                        Scheduler.disabledScheduler(),
                        TTL,
                        -1,
                        10,
                        logger);
    }

    @Test
    void shouldReuseConnectionForNestedTasks() throws Exception {
        Mockito.when(jfrConnectionToolkit.createServiceURL(Mockito.anyString(), Mockito.anyInt()))
                .thenAnswer(
                        new Answer<JMXServiceURL>() {
                            @Override
                            public JMXServiceURL answer(InvocationOnMock args) throws Throwable {
                                String host = args.getArgument(0);
                                int port = args.getArgument(1);
                                return new JMXServiceURL(
                                        "rmi",
                                        "",
                                        0,
                                        String.format("/jndi/rmi://%s:%d/jmxrmi", host, port));
                            }
                        });
        Mockito.when(jfrConnectionToolkit.connect(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenAnswer(
                        new Answer<JFRConnection>() {
                            @Override
                            public JFRConnection answer(InvocationOnMock invocation)
                                    throws Throwable {
                                return Mockito.mock(JFRConnection.class);
                            }
                        });
        ConnectionDescriptor descriptor = new ConnectionDescriptor("localhost:0");
        mgr.executeConnectedTask(
                descriptor,
                conn1 -> {
                    mgr.executeConnectedTask(
                            descriptor,
                            conn2 -> {
                                MatcherAssert.assertThat(conn1, Matchers.sameInstance(conn2));
                                return null;
                            });
                    return null;
                });
    }

    @Test
    void shouldReuseConnectionInSequentialAccessWithoutDelay() throws Exception {
        Mockito.when(jfrConnectionToolkit.connect(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenAnswer(
                        new Answer<JFRConnection>() {
                            @Override
                            public JFRConnection answer(InvocationOnMock invocation)
                                    throws Throwable {
                                return Mockito.mock(JFRConnection.class);
                            }
                        });
        ConnectionDescriptor desc =
                new ConnectionDescriptor("service:jmx:rmi:///jndi/rmi://localhost:0/jmxrmi");
        JFRConnection conn1 = mgr.executeConnectedTask(desc, a -> a);
        JFRConnection conn2 = mgr.executeConnectedTask(desc, a -> a);
        MatcherAssert.assertThat(conn1, Matchers.sameInstance(conn2));
    }

    @Test
    void shouldCreateNewConnectionIfPreviousExplicitlyClosed() throws Exception {
        ArgumentCaptor<List<Runnable>> closeListeners = ArgumentCaptor.forClass(List.class);
        Mockito.when(
                        jfrConnectionToolkit.connect(
                                Mockito.any(), Mockito.any(), closeListeners.capture()))
                .thenAnswer(
                        new Answer<JFRConnection>() {
                            @Override
                            public JFRConnection answer(InvocationOnMock invocation)
                                    throws Throwable {
                                return Mockito.mock(JFRConnection.class);
                            }
                        });
        ConnectionDescriptor desc =
                new ConnectionDescriptor("service:jmx:rmi:///jndi/rmi://localhost:0/jmxrmi");
        JFRConnection conn1 = mgr.executeConnectedTask(desc, a -> a);
        closeListeners.getValue().forEach(Runnable::run);
        JFRConnection conn2 = mgr.executeConnectedTask(desc, a -> a);
        MatcherAssert.assertThat(conn1, Matchers.not(Matchers.sameInstance(conn2)));
    }

    @Test
    void shouldCreateNewConnectionForAccessDelayedLongerThanTTL() throws Exception {
        TargetConnectionManager mgr =
                new TargetConnectionManager(
                        () -> jfrConnectionToolkit,
                        () -> agentConnectionFactory,
                        platformClient,
                        ForkJoinPool.commonPool(),
                        Scheduler.systemScheduler(),
                        Duration.ofNanos(1),
                        1,
                        10,
                        logger);
        Mockito.when(jfrConnectionToolkit.connect(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenAnswer(
                        new Answer<JFRConnection>() {
                            @Override
                            public JFRConnection answer(InvocationOnMock invocation)
                                    throws Throwable {
                                return Mockito.mock(JFRConnection.class);
                            }
                        });
        ConnectionDescriptor desc =
                new ConnectionDescriptor("service:jmx:rmi:///jndi/rmi://localhost:0/jmxrmi");
        JFRConnection conn1 = mgr.executeConnectedTask(desc, a -> a);
        Thread.sleep(10);
        JFRConnection conn2 = mgr.executeConnectedTask(desc, a -> a);
        MatcherAssert.assertThat(conn1, Matchers.not(Matchers.sameInstance(conn2)));
    }

    @Test
    void shouldCreateNewConnectionPerTarget() throws Exception {
        TargetConnectionManager mgr =
                new TargetConnectionManager(
                        () -> jfrConnectionToolkit,
                        () -> agentConnectionFactory,
                        platformClient,
                        Runnable::run,
                        Scheduler.disabledScheduler(),
                        Duration.ofNanos(1),
                        -1,
                        10,
                        logger);
        Mockito.when(jfrConnectionToolkit.connect(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenAnswer(
                        new Answer<JFRConnection>() {
                            @Override
                            public JFRConnection answer(InvocationOnMock invocation)
                                    throws Throwable {
                                return Mockito.mock(JFRConnection.class);
                            }
                        });
        ConnectionDescriptor desc1 =
                new ConnectionDescriptor("service:jmx:rmi:///jndi/rmi://localhost:0/jmxrmi");
        ConnectionDescriptor desc2 =
                new ConnectionDescriptor("service:jmx:rmi:///jndi/rmi://example:1/jmxrmi");
        JFRConnection conn1 = mgr.executeConnectedTask(desc1, a -> a);
        JFRConnection conn2 = mgr.executeConnectedTask(desc2, a -> a);
        MatcherAssert.assertThat(conn1, Matchers.not(Matchers.sameInstance(conn2)));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "http://localhost:8080",
                "https://cryostat.example.com",
                "cryostat-agent://mypod.mycluster.svc:1234"
            })
    void shouldConnectToAgents(String url) throws Exception {
        AgentConnection agentConn = Mockito.mock(AgentConnection.class);
        Mockito.when(agentConnectionFactory.createConnection(Mockito.any())).thenReturn(agentConn);
        TargetConnectionManager mgr =
                new TargetConnectionManager(
                        () -> jfrConnectionToolkit,
                        () -> agentConnectionFactory,
                        platformClient,
                        Runnable::run,
                        Scheduler.disabledScheduler(),
                        Duration.ofNanos(1),
                        -1,
                        10,
                        logger);
        ConnectionDescriptor desc = new ConnectionDescriptor(url);
        JFRConnection conn = mgr.executeConnectedTask(desc, a -> a);
        MatcherAssert.assertThat(conn, Matchers.sameInstance(agentConn));
    }
}
