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
package io.cryostat.platform.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.MalformedURLException;
import java.util.List;
import java.util.Map;

import javax.management.remote.JMXServiceURL;

import io.cryostat.core.log.Logger;
import io.cryostat.core.net.discovery.DiscoveredJvmDescriptor;
import io.cryostat.core.net.discovery.JvmDiscoveryClient;
import io.cryostat.core.net.discovery.JvmDiscoveryClient.EventKind;
import io.cryostat.core.net.discovery.JvmDiscoveryClient.JvmDiscoveryEvent;
import io.cryostat.messaging.notifications.Notification;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.platform.ServiceRef;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultPlatformClientTest {

    @Mock Logger logger;
    @Mock JvmDiscoveryClient discoveryClient;
    @Mock NotificationFactory notificationFactory;
    DefaultPlatformClient client;

    @BeforeEach
    void setup() {
        this.client = new DefaultPlatformClient(logger, discoveryClient, notificationFactory);
    }

    @Test
    void testShouldAddListenerAndStartDiscovery() throws Exception {
        verifyNoInteractions(discoveryClient);
        verifyNoInteractions(notificationFactory);

        client.start();

        InOrder inOrder = Mockito.inOrder(discoveryClient);
        inOrder.verify(discoveryClient).addListener(client);
        inOrder.verify(discoveryClient).start();
    }

    @Test
    void testDiscoverableServiceMapping() throws Exception {
        DiscoveredJvmDescriptor desc1 = mock(DiscoveredJvmDescriptor.class);
        JMXServiceURL url1 = mock(JMXServiceURL.class);
        when(desc1.getMainClass()).thenReturn("com.example.Main");
        when(desc1.getJmxServiceUrl()).thenReturn(url1);

        DiscoveredJvmDescriptor desc2 = mock(DiscoveredJvmDescriptor.class);
        when(desc2.getJmxServiceUrl()).thenThrow(MalformedURLException.class);

        DiscoveredJvmDescriptor desc3 = mock(DiscoveredJvmDescriptor.class);
        JMXServiceURL url3 = mock(JMXServiceURL.class);
        when(desc3.getMainClass()).thenReturn("io.cryostat.Cryostat");
        when(desc3.getJmxServiceUrl()).thenReturn(url3);

        when(discoveryClient.getDiscoveredJvmDescriptors())
                .thenReturn(List.of(desc1, desc2, desc3));

        List<ServiceRef> results = client.listDiscoverableServices();

        ServiceRef exp1 = new ServiceRef(desc1.getJmxServiceUrl(), desc1.getMainClass());
        ServiceRef exp2 = new ServiceRef(desc3.getJmxServiceUrl(), desc3.getMainClass());

        assertThat(results, equalTo(List.of(exp1, exp2)));
    }

    @Test
    void testAcceptDiscoveryEvent() throws Exception {
        JMXServiceURL url = mock(JMXServiceURL.class);
        String mainClass = "com.example.Main";
        DiscoveredJvmDescriptor desc = mock(DiscoveredJvmDescriptor.class);
        when(desc.getMainClass()).thenReturn(mainClass);
        when(desc.getJmxServiceUrl()).thenReturn(url);
        JvmDiscoveryEvent evt = mock(JvmDiscoveryEvent.class);
        when(evt.getEventKind()).thenReturn(EventKind.FOUND);
        when(evt.getJvmDescriptor()).thenReturn(desc);

        Notification notification = mock(Notification.class);

        Notification.Builder builder = mock(Notification.Builder.class);
        lenient().when(builder.meta(Mockito.any())).thenReturn(builder);
        lenient().when(builder.metaCategory(Mockito.any())).thenReturn(builder);
        lenient()
                .when(builder.metaType(Mockito.any(Notification.MetaType.class)))
                .thenReturn(builder);
        lenient().when(builder.metaType(Mockito.any(HttpMimeType.class))).thenReturn(builder);
        lenient().when(builder.message(Mockito.any())).thenReturn(builder);
        lenient().when(builder.build()).thenReturn(notification);

        when(notificationFactory.createBuilder()).thenReturn(builder);

        verifyNoInteractions(notificationFactory);

        try {
            client.accept(evt);
        } catch (Exception e) {
            e.printStackTrace();
        }

        verifyNoInteractions(discoveryClient);

        verify(notificationFactory).createBuilder();
        verify(builder).metaCategory("TargetJvmDiscovery");
        verify(builder)
                .message(
                        Map.of(
                                "event",
                                Map.of(
                                        "kind",
                                        EventKind.FOUND,
                                        "serviceRef",
                                        new ServiceRef(url, mainClass))));
        verify(builder).build();
        verify(notification).send();
    }
}
