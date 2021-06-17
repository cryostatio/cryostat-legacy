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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;

import javax.management.remote.JMXServiceURL;

import io.cryostat.MainModule;
import io.cryostat.core.net.discovery.JvmDiscoveryClient.EventKind;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.messaging.notifications.Notification;
import io.cryostat.messaging.notifications.Notification.MetaType;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.platform.ServiceRef;

import com.google.gson.Gson;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomTargetPlatformClientTest {

    CustomTargetPlatformClient client;
    @Mock NotificationFactory notificationFactory;
    @Mock Path confDir;
    @Mock Path saveFile;
    @Mock FileSystem fs;
    @Mock Notification notification;
    @Mock Notification.Builder notificationBuilder;
    Gson gson = MainModule.provideGson(null);

    static final ServiceRef SERVICE_REF;

    static {
        try {
            SERVICE_REF =
                    new ServiceRef(
                            new JMXServiceURL("service:jmx:rmi:///jndi/rmi://cryostat:9099/jmxrmi"),
                            "TestTarget");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @BeforeEach
    void setup() {
        Mockito.when(confDir.resolve(Mockito.anyString())).thenReturn(saveFile);

        Mockito.lenient().when(notificationFactory.createBuilder()).thenReturn(notificationBuilder);
        Mockito.lenient()
                .when(notificationBuilder.meta(Mockito.any()))
                .thenReturn(notificationBuilder);
        Mockito.lenient()
                .when(notificationBuilder.metaType(Mockito.any(MetaType.class)))
                .thenReturn(notificationBuilder);
        Mockito.lenient()
                .when(notificationBuilder.metaType(Mockito.any(HttpMimeType.class)))
                .thenReturn(notificationBuilder);
        Mockito.lenient()
                .when(notificationBuilder.metaCategory(Mockito.anyString()))
                .thenReturn(notificationBuilder);
        Mockito.lenient()
                .when(notificationBuilder.message(Mockito.any()))
                .thenReturn(notificationBuilder);
        Mockito.lenient().when(notificationBuilder.build()).thenReturn(notification);

        this.client = new CustomTargetPlatformClient(notificationFactory, confDir, fs, gson);
    }

    @Test
    void shouldReadSaveFileIfPresent() throws IOException {
        Mockito.verifyNoInteractions(fs);
        Mockito.when(fs.isRegularFile(saveFile)).thenReturn(true);
        Mockito.when(fs.isReadable(saveFile)).thenReturn(true);
        BufferedReader reader = new BufferedReader(new StringReader("[]"));
        Mockito.when(fs.readFile(saveFile)).thenReturn(reader);

        client.start();

        Mockito.verify(fs).readFile(saveFile);
        Mockito.verifyNoMoreInteractions(fs);
    }

    @Test
    void shouldPopulateTargetsOnStartup() throws IOException {
        Mockito.verifyNoInteractions(fs);
        Mockito.when(fs.isRegularFile(saveFile)).thenReturn(true);
        Mockito.when(fs.isReadable(saveFile)).thenReturn(true);

        BufferedReader reader =
                new BufferedReader(new StringReader(gson.toJson(List.of(SERVICE_REF))));
        Mockito.when(fs.readFile(saveFile)).thenReturn(reader);

        client.start();
        List<ServiceRef> result = client.listDiscoverableServices();
        MatcherAssert.assertThat(result, Matchers.equalTo(List.of(SERVICE_REF)));
    }

    @Test
    void shouldSkipReadSaveFileIfNotPresent() throws IOException {
        Mockito.verifyNoInteractions(fs);

        client.start();

        Mockito.verify(fs).isRegularFile(saveFile);
        Mockito.verifyNoMoreInteractions(fs);
    }

    @Test
    void testAddNewTarget() throws IOException {
        Assertions.assertTrue(client.addTarget(SERVICE_REF));

        Mockito.verify(fs)
                .writeString(
                        saveFile,
                        gson.toJson(List.of(SERVICE_REF)),
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.CREATE);
        Mockito.verify(notificationFactory).createBuilder();
        Mockito.verify(notificationBuilder).metaCategory("TargetJvmDiscovery");
        ArgumentCaptor<Map<String, String>> messageCaptor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(notificationBuilder).message(messageCaptor.capture());
        Map<String, String> message = messageCaptor.getValue();
        MatcherAssert.assertThat(
                message,
                Matchers.equalTo(
                        Map.of(
                                "event",
                                Map.of("kind", EventKind.FOUND, "serviceRef", SERVICE_REF))));
        Mockito.verify(notificationBuilder).build();
        Mockito.verify(notification).send();
    }

    @Test
    void testAddDuplicateTarget() throws IOException {
        Mockito.when(fs.isRegularFile(saveFile)).thenReturn(true);
        Mockito.when(fs.isReadable(saveFile)).thenReturn(true);

        BufferedReader reader =
                new BufferedReader(new StringReader(gson.toJson(List.of(SERVICE_REF))));
        Mockito.when(fs.readFile(saveFile)).thenReturn(reader);

        client.start();
        Assertions.assertFalse(client.addTarget(SERVICE_REF));

        Mockito.verify(fs, Mockito.never())
                .writeString(
                        Mockito.any(), Mockito.anyString(), ArgumentMatchers.<OpenOption>any());
        Mockito.verifyNoInteractions(notificationFactory);
        Mockito.verifyNoInteractions(notificationBuilder);
        Mockito.verifyNoInteractions(notification);
    }

    @Test
    void testRemoveTarget() throws IOException {
        Mockito.when(fs.isRegularFile(saveFile)).thenReturn(true);
        Mockito.when(fs.isReadable(saveFile)).thenReturn(true);

        BufferedReader reader =
                new BufferedReader(new StringReader(gson.toJson(List.of(SERVICE_REF))));
        Mockito.when(fs.readFile(saveFile)).thenReturn(reader);

        client.start();
        Assertions.assertTrue(client.removeTarget(SERVICE_REF));

        Mockito.verify(fs)
                .writeString(
                        saveFile,
                        gson.toJson(List.of()),
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.CREATE);
        Mockito.verify(notificationFactory).createBuilder();
        Mockito.verify(notificationBuilder).metaCategory("TargetJvmDiscovery");
        ArgumentCaptor<Map<String, String>> messageCaptor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(notificationBuilder).message(messageCaptor.capture());
        Map<String, String> message = messageCaptor.getValue();
        MatcherAssert.assertThat(
                message,
                Matchers.equalTo(
                        Map.of(
                                "event",
                                Map.of("kind", EventKind.LOST, "serviceRef", SERVICE_REF))));
        Mockito.verify(notificationBuilder).build();
        Mockito.verify(notification).send();
    }

    @Test
    void testRemoveNonexistentTarget() throws IOException {
        Assertions.assertFalse(client.removeTarget(SERVICE_REF));

        Mockito.verify(fs, Mockito.never())
                .writeString(
                        Mockito.any(), Mockito.anyString(), ArgumentMatchers.<OpenOption>any());
        Mockito.verifyNoInteractions(notificationFactory);
        Mockito.verifyNoInteractions(notificationBuilder);
        Mockito.verifyNoInteractions(notification);
    }

    @Test
    void testRemoveTargetByUrl() throws IOException {
        Mockito.when(fs.isRegularFile(saveFile)).thenReturn(true);
        Mockito.when(fs.isReadable(saveFile)).thenReturn(true);

        BufferedReader reader =
                new BufferedReader(new StringReader(gson.toJson(List.of(SERVICE_REF))));
        Mockito.when(fs.readFile(saveFile)).thenReturn(reader);

        client.start();
        Assertions.assertTrue(client.removeTarget(SERVICE_REF.getServiceUri()));

        Mockito.verify(fs)
                .writeString(
                        saveFile,
                        gson.toJson(List.of()),
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.CREATE);
        Mockito.verify(notificationFactory).createBuilder();
        Mockito.verify(notificationBuilder).metaCategory("TargetJvmDiscovery");
        ArgumentCaptor<Map<String, String>> messageCaptor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(notificationBuilder).message(messageCaptor.capture());
        Map<String, String> message = messageCaptor.getValue();
        MatcherAssert.assertThat(
                message,
                Matchers.equalTo(
                        Map.of(
                                "event",
                                Map.of("kind", EventKind.LOST, "serviceRef", SERVICE_REF))));
        Mockito.verify(notificationBuilder).build();
        Mockito.verify(notification).send();
    }

    @Test
    void testRemoveNonexistentTargetByUrl() throws IOException {
        Assertions.assertFalse(client.removeTarget(SERVICE_REF.getServiceUri()));

        Mockito.verify(fs, Mockito.never())
                .writeString(
                        Mockito.any(), Mockito.anyString(), ArgumentMatchers.<OpenOption>any());
        Mockito.verifyNoInteractions(notificationFactory);
        Mockito.verifyNoInteractions(notificationBuilder);
        Mockito.verifyNoInteractions(notification);
    }
}
