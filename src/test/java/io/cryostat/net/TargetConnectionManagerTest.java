/*
 * Copyright The Cryostat Authors
 *
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
    @Mock AgentConnectionFactory agentConnectionFactory;
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
        ConnectionDescriptor descriptor = new ConnectionDescriptor("foo");
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
        ConnectionDescriptor desc = new ConnectionDescriptor("foo");
        JFRConnection conn1 = mgr.executeConnectedTask(desc, a -> a);
        JFRConnection conn2 = mgr.executeConnectedTask(desc, a -> a);
        MatcherAssert.assertThat(conn1, Matchers.sameInstance(conn2));
    }

    @Test
    void shouldCreateNewConnectionIfPreviousExplicitlyClosed() throws Exception {
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
        ConnectionDescriptor desc = new ConnectionDescriptor("foo");
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
                        logger);
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
        ConnectionDescriptor desc = new ConnectionDescriptor("foo");
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
                        logger);
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
        ConnectionDescriptor desc1 = new ConnectionDescriptor("foo");
        ConnectionDescriptor desc2 = new ConnectionDescriptor("bar");
        JFRConnection conn1 = mgr.executeConnectedTask(desc1, a -> a);
        JFRConnection conn2 = mgr.executeConnectedTask(desc2, a -> a);
        MatcherAssert.assertThat(conn1, Matchers.not(Matchers.sameInstance(conn2)));
    }
}
