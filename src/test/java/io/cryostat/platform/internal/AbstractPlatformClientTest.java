/*-
 * #%L
 * Cryostat
 * %%
 * Copyright (C) 2020 - 2021 Cryostat
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
package io.cryostat.platform.internal;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.management.remote.JMXServiceURL;

import io.cryostat.core.net.discovery.JvmDiscoveryClient.EventKind;
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
class AbstractPlatformClientTest {

    @Mock NotificationFactory notificationFactory;
    @Mock Notification.Builder builder;
    @Mock Notification notification;
    TestPlatformClient platformClient;

    @BeforeEach
    void setup() {
        Mockito.lenient().when(notificationFactory.createBuilder()).thenReturn(builder);
        Mockito.lenient().when(builder.meta(Mockito.any())).thenReturn(builder);
        Mockito.lenient().when(builder.message(Mockito.any())).thenReturn(builder);
        Mockito.lenient()
                .when(builder.metaType(Mockito.any(Notification.MetaType.class)))
                .thenReturn(builder);
        Mockito.lenient()
                .when(builder.metaType(Mockito.any(HttpMimeType.class)))
                .thenReturn(builder);
        Mockito.lenient().when(builder.metaCategory(Mockito.anyString())).thenReturn(builder);
        Mockito.lenient().when(builder.build()).thenReturn(notification);

        this.platformClient = new TestPlatformClient(notificationFactory);
    }

    @Test
    void shouldEmitNotification() throws Exception {
        Mockito.verifyNoInteractions(notificationFactory);

        JMXServiceURL serviceUrl = Mockito.mock(JMXServiceURL.class);
        String alias = "com.example.Foo";
        ServiceRef serviceRef = new ServiceRef(serviceUrl, alias);
        EventKind kind = EventKind.FOUND;

        this.platformClient.notifyAsyncTargetDiscovery(kind, serviceRef);

        InOrder inOrder = Mockito.inOrder(notificationFactory, builder, notification);
        inOrder.verify(notificationFactory).createBuilder();
        inOrder.verify(builder).build();
        inOrder.verify(notification).send();

        Mockito.verify(builder).metaCategory("TargetJvmDiscovery");
        Mockito.verify(builder)
                .message(Map.of("event", Map.of("kind", kind, "serviceRef", serviceRef)));
    }

    static class TestPlatformClient extends AbstractPlatformClient {
        TestPlatformClient(NotificationFactory notificationFactory) {
            super(notificationFactory);
        }

        @Override
        public void start() throws IOException {}

        @Override
        public List<ServiceRef> listDiscoverableServices() {
            return Collections.emptyList();
        }
    }
}
