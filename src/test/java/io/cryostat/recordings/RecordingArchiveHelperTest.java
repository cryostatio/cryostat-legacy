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
package io.cryostat.recordings;

import static org.mockito.Mockito.lenient;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import javax.management.remote.JMXServiceURL;

import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.core.log.Logger;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.core.sys.Clock;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.messaging.notifications.Notification;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.platform.PlatformClient;
import io.cryostat.platform.ServiceRef;
import io.cryostat.recordings.RecordingArchiveHelper.ArchiveDirectory;
import io.cryostat.recordings.RecordingMetadataManager.Metadata;
import io.cryostat.rules.ArchivedRecordingInfo;
import io.cryostat.util.URIUtil;

import org.apache.commons.codec.binary.Base32;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
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
class RecordingArchiveHelperTest {

    RecordingArchiveHelper recordingArchiveHelper;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock RecordingMetadataManager recordingMetadataManager;
    @Mock FileSystem fs;
    @Mock WebServer webServer;
    @Mock Logger logger;
    @Mock Path destinationFile;
    @Mock Path archivedRecordingsPath;
    @Mock Path archivedRecordingsReportPath;
    @Mock Clock clock;
    @Mock PlatformClient platformClient;
    @Mock NotificationFactory notificationFactory;
    @Mock JvmIdHelper jvmIdHelper;
    @Mock Base32 base32;
    @Mock Notification notification;
    @Mock Notification.Builder notificationBuilder;
    @Mock JFRConnection connection;
    @Mock IFlightRecorderService service;

    String targetId = "fooTarget";
    String recordingName = "someRecording";

    @BeforeEach
    void setup() {
        lenient().when(notificationFactory.createBuilder()).thenReturn(notificationBuilder);
        lenient()
                .when(notificationBuilder.metaCategory(Mockito.any()))
                .thenReturn(notificationBuilder);
        lenient()
                .when(notificationBuilder.metaType(Mockito.any(Notification.MetaType.class)))
                .thenReturn(notificationBuilder);
        lenient()
                .when(notificationBuilder.metaType(Mockito.any(HttpMimeType.class)))
                .thenReturn(notificationBuilder);
        lenient().when(notificationBuilder.message(Mockito.any())).thenReturn(notificationBuilder);
        lenient().when(notificationBuilder.build()).thenReturn(notification);
        lenient()
                .when(jvmIdHelper.jvmIdToSubdirectoryName(Mockito.anyString()))
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                return invocation.getArgument(0);
                            }
                        });
        lenient()
                .when(jvmIdHelper.subdirectoryNameToJvmId(Mockito.anyString()))
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                return invocation.getArgument(0);
                            }
                        });

        this.recordingArchiveHelper =
                new RecordingArchiveHelper(
                        fs,
                        () -> webServer,
                        logger,
                        archivedRecordingsPath,
                        archivedRecordingsReportPath,
                        targetConnectionManager,
                        recordingMetadataManager,
                        clock,
                        platformClient,
                        notificationFactory,
                        jvmIdHelper,
                        base32);
    }

    @Test
    void saveRecordingShouldThrowIfNoMatchingRecordingFound() throws Exception {
        Mockito.when(
                        targetConnectionManager.executeConnectedTask(
                                Mockito.any(),
                                Mockito.any(TargetConnectionManager.ConnectedTask.class)))
                .thenAnswer(
                        new Answer<>() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                TargetConnectionManager.ConnectedTask task =
                                        (TargetConnectionManager.ConnectedTask)
                                                invocation.getArgument(1);
                                return task.execute(connection);
                            }
                        });
        Mockito.when(connection.getService()).thenReturn(service);
        Mockito.when(service.getAvailableRecordings()).thenReturn(List.of());

        Assertions.assertThrows(
                ExecutionException.class,
                () -> {
                    try {
                        recordingArchiveHelper
                                .saveRecording(new ConnectionDescriptor(targetId), recordingName)
                                .get();
                    } catch (ExecutionException ee) {
                        Assertions.assertTrue(ee.getCause() instanceof RecordingNotFoundException);
                        throw ee;
                    }
                });
    }

    @Test
    void shouldSaveRecordingWithAlias() throws Exception {
        Mockito.when(jvmIdHelper.getJvmId(Mockito.anyString())).thenReturn("mockId");
        Mockito.when(
                        targetConnectionManager.executeConnectedTask(
                                Mockito.any(),
                                Mockito.any(TargetConnectionManager.ConnectedTask.class)))
                .thenAnswer(
                        new Answer<>() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                TargetConnectionManager.ConnectedTask task =
                                        (TargetConnectionManager.ConnectedTask)
                                                invocation.getArgument(1);
                                return task.execute(connection);
                            }
                        });
        Mockito.when(connection.getService()).thenReturn(service);
        IRecordingDescriptor descriptor = Mockito.mock(IRecordingDescriptor.class);
        Mockito.when(descriptor.getName()).thenReturn(recordingName);
        Mockito.when(service.getAvailableRecordings()).thenReturn(List.of(descriptor));

        ServiceRef serviceRef1 =
                new ServiceRef(
                        "id1",
                        URIUtil.convert(
                                new JMXServiceURL(
                                        "service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi")),
                        "some.Alias.1");
        ServiceRef serviceRef2 =
                new ServiceRef(
                        "id2",
                        URIUtil.convert(
                                new JMXServiceURL(
                                        "service:jmx:rmi:///jndi/rmi://cryostat:9092/jmxrmi")),
                        "some.Alias.2");
        ServiceRef serviceRef3 =
                new ServiceRef(
                        "id3",
                        URIUtil.convert(
                                new JMXServiceURL(
                                        "service:jmx:rmi:///jndi/rmi://cryostat:9093/jmxrmi")),
                        "some.Alias.3");

        Mockito.when(platformClient.listDiscoverableServices())
                .thenReturn(List.of(serviceRef1, serviceRef2, serviceRef3));
        Mockito.when(connection.getJMXURL())
                .thenReturn(
                        (new JMXServiceURL("service:jmx:rmi:///jndi/rmi://cryostat:9092/jmxrmi")));

        Instant now = Instant.now();
        Mockito.when(clock.now()).thenReturn(now);
        Mockito.when(fs.exists(Mockito.any())).thenReturn(true).thenReturn(false);
        InputStream stream = new ByteArrayInputStream("someRecordingData".getBytes());
        Mockito.when(service.openStream(descriptor, false)).thenReturn(stream);
        Path specificRecordingsPath = Mockito.mock(Path.class);
        Mockito.when(archivedRecordingsPath.resolve(Mockito.anyString()))
                .thenReturn(specificRecordingsPath);
        Path destination = Mockito.mock(Path.class);
        Mockito.when(specificRecordingsPath.resolve(Mockito.anyString())).thenReturn(destination);
        String timestamp = now.truncatedTo(ChronoUnit.SECONDS).toString().replaceAll("[-:]+", "");
        String savedName = "some-hostname-local_someRecording_" + timestamp + ".jfr";
        Path filenamePath = Mockito.mock(Path.class);
        Path parentPath = Path.of("some", "storage");
        Mockito.when(destination.getParent()).thenReturn(parentPath);
        Mockito.when(filenamePath.toString()).thenReturn(savedName);
        Mockito.when(destination.getFileName()).thenReturn(filenamePath);
        Mockito.when(
                        recordingMetadataManager.copyMetadataToArchives(
                                Mockito.any(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(CompletableFuture.completedFuture(new Metadata()));

        ArchivedRecordingInfo info =
                recordingArchiveHelper
                        .saveRecording(new ConnectionDescriptor(targetId), recordingName)
                        .get();

        MatcherAssert.assertThat(info.getName(), Matchers.equalTo(savedName));
        Mockito.verify(fs).copy(Mockito.isA(BufferedInputStream.class), Mockito.eq(destination));
        Mockito.verify(notificationFactory).createBuilder();
        Mockito.verify(notificationBuilder).metaCategory("ActiveRecordingSaved");
        Mockito.verify(notificationBuilder).metaType(HttpMimeType.JSON);
        Mockito.verify(notificationBuilder).message(Map.of("recording", info, "target", targetId));
        Mockito.verify(notificationBuilder).build();
        Mockito.verify(notification).send();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "with/fs/separators",
                "/deployment/quarkus-run.jar",
                "with spaces",
            })
    void shouldSaveRecordingWithNonFilesystemSafeAlias(String alias) throws Exception {
        Mockito.when(jvmIdHelper.getJvmId(Mockito.anyString())).thenReturn("mockId");
        Mockito.when(
                        targetConnectionManager.executeConnectedTask(
                                Mockito.any(),
                                Mockito.any(TargetConnectionManager.ConnectedTask.class)))
                .thenAnswer(
                        new Answer<>() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                TargetConnectionManager.ConnectedTask task =
                                        (TargetConnectionManager.ConnectedTask)
                                                invocation.getArgument(1);
                                return task.execute(connection);
                            }
                        });
        Mockito.when(connection.getService()).thenReturn(service);
        IRecordingDescriptor descriptor = Mockito.mock(IRecordingDescriptor.class);
        Mockito.when(descriptor.getName()).thenReturn(recordingName);
        Mockito.when(service.getAvailableRecordings()).thenReturn(List.of(descriptor));

        ServiceRef serviceRef1 =
                new ServiceRef(
                        "id1",
                        URIUtil.convert(
                                new JMXServiceURL(
                                        "service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi")),
                        alias);

        Mockito.when(platformClient.listDiscoverableServices()).thenReturn(List.of(serviceRef1));
        Mockito.when(connection.getJMXURL())
                .thenReturn(
                        (new JMXServiceURL("service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi")));

        Instant now = Instant.now();
        Mockito.when(clock.now()).thenReturn(now);
        Mockito.when(fs.exists(Mockito.any())).thenReturn(true).thenReturn(false);
        InputStream stream = new ByteArrayInputStream("someRecordingData".getBytes());
        Mockito.when(service.openStream(descriptor, false)).thenReturn(stream);
        Path specificRecordingsPath = Mockito.mock(Path.class);
        Mockito.when(archivedRecordingsPath.resolve(Mockito.anyString()))
                .thenReturn(specificRecordingsPath);
        Path destination = Mockito.mock(Path.class);
        Mockito.when(specificRecordingsPath.resolve(Mockito.anyString())).thenReturn(destination);
        String timestamp = now.truncatedTo(ChronoUnit.SECONDS).toString().replaceAll("[-:]+", "");
        Path filenamePath = Mockito.mock(Path.class);
        Path parentPath = Path.of("some", "storage");
        Mockito.when(destination.getParent()).thenReturn(parentPath);
        String savedName =
                URLEncoder.encode(alias.replaceAll("[\\._]+", "-"), StandardCharsets.UTF_8)
                        + "_someRecording_"
                        + timestamp
                        + ".jfr";
        Mockito.when(filenamePath.toString()).thenReturn(savedName);
        Mockito.when(destination.getFileName()).thenReturn(filenamePath);
        Mockito.when(
                        recordingMetadataManager.copyMetadataToArchives(
                                Mockito.any(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(CompletableFuture.completedFuture(new Metadata()));

        ArchivedRecordingInfo info =
                recordingArchiveHelper
                        .saveRecording(new ConnectionDescriptor(serviceRef1), recordingName)
                        .get();

        MatcherAssert.assertThat(info.getName(), Matchers.equalTo(savedName));
        Mockito.verify(fs).copy(Mockito.isA(BufferedInputStream.class), Mockito.eq(destination));
        Mockito.verify(notificationFactory).createBuilder();
        Mockito.verify(notificationBuilder).metaCategory("ActiveRecordingSaved");
        Mockito.verify(notificationBuilder).metaType(HttpMimeType.JSON);
        Mockito.verify(notificationBuilder)
                .message(
                        Map.of(
                                "recording",
                                info,
                                "target",
                                serviceRef1.getServiceUri().toString()));
        Mockito.verify(notificationBuilder).build();
        Mockito.verify(notification).send();
    }

    @Test
    void shouldSaveRecordingWithoutAlias() throws Exception {
        Mockito.when(jvmIdHelper.getJvmId(Mockito.anyString())).thenReturn("mockId");
        Mockito.when(
                        targetConnectionManager.executeConnectedTask(
                                Mockito.any(),
                                Mockito.any(TargetConnectionManager.ConnectedTask.class)))
                .thenAnswer(
                        new Answer<>() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                TargetConnectionManager.ConnectedTask task =
                                        (TargetConnectionManager.ConnectedTask)
                                                invocation.getArgument(1);
                                return task.execute(connection);
                            }
                        });
        Mockito.when(connection.getService()).thenReturn(service);
        IRecordingDescriptor descriptor = Mockito.mock(IRecordingDescriptor.class);
        Mockito.when(descriptor.getName()).thenReturn(recordingName);
        Mockito.when(service.getAvailableRecordings()).thenReturn(List.of(descriptor));

        ServiceRef serviceRef1 =
                new ServiceRef(
                        "id1",
                        URIUtil.convert(
                                new JMXServiceURL(
                                        "service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi")),
                        "some.Alias.1");
        ServiceRef serviceRef2 =
                new ServiceRef(
                        "id2",
                        URIUtil.convert(
                                new JMXServiceURL(
                                        "service:jmx:rmi:///jndi/rmi://cryostat:9092/jmxrmi")),
                        null);
        ServiceRef serviceRef3 =
                new ServiceRef(
                        "id3",
                        URIUtil.convert(
                                new JMXServiceURL(
                                        "service:jmx:rmi:///jndi/rmi://cryostat:9093/jmxrmi")),
                        "some.Alias.3");

        Mockito.when(platformClient.listDiscoverableServices())
                .thenReturn(List.of(serviceRef1, serviceRef2, serviceRef3));
        Mockito.when(connection.getJMXURL())
                .thenReturn(
                        (new JMXServiceURL("service:jmx:rmi:///jndi/rmi://cryostat:9092/jmxrmi")));
        Mockito.when(connection.getHost()).thenReturn("some-hostname.local");

        Instant now = Instant.now();
        Mockito.when(clock.now()).thenReturn(now);
        Mockito.when(fs.exists(Mockito.any())).thenReturn(true).thenReturn(false);
        InputStream stream = new ByteArrayInputStream("someRecordingData".getBytes());
        Mockito.when(service.openStream(descriptor, false)).thenReturn(stream);
        Path specificRecordingsPath = Mockito.mock(Path.class);
        Mockito.when(archivedRecordingsPath.resolve(Mockito.anyString()))
                .thenReturn(specificRecordingsPath);
        Path destination = Mockito.mock(Path.class);
        Mockito.when(specificRecordingsPath.resolve(Mockito.anyString())).thenReturn(destination);
        String timestamp = now.truncatedTo(ChronoUnit.SECONDS).toString().replaceAll("[-:]+", "");
        String savedName = "some-hostname-local_someRecording_" + timestamp + ".jfr";
        Path filenamePath = Mockito.mock(Path.class);
        Path parentPath = Path.of("some", "storage");
        Mockito.when(destination.getParent()).thenReturn(parentPath);
        Mockito.when(filenamePath.toString()).thenReturn(savedName);
        Mockito.when(destination.getFileName()).thenReturn(filenamePath);
        Mockito.when(
                        recordingMetadataManager.copyMetadataToArchives(
                                Mockito.any(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(CompletableFuture.completedFuture(new Metadata()));

        ArchivedRecordingInfo info =
                recordingArchiveHelper
                        .saveRecording(new ConnectionDescriptor(targetId), recordingName)
                        .get();

        MatcherAssert.assertThat(info.getName(), Matchers.equalTo(savedName));
        Mockito.verify(fs).copy(Mockito.isA(BufferedInputStream.class), Mockito.eq(destination));
        Mockito.verify(notificationFactory).createBuilder();
        Mockito.verify(notificationBuilder).metaCategory("ActiveRecordingSaved");
        Mockito.verify(notificationBuilder).metaType(HttpMimeType.JSON);
        Mockito.verify(notificationBuilder).message(Map.of("recording", info, "target", targetId));
        Mockito.verify(notificationBuilder).build();
        Mockito.verify(notification).send();
    }

    @Test
    void shouldSaveRecordingWithoutServiceRef() throws Exception {
        Mockito.when(jvmIdHelper.getJvmId(Mockito.anyString())).thenReturn("mockId");
        Mockito.when(
                        targetConnectionManager.executeConnectedTask(
                                Mockito.any(),
                                Mockito.any(TargetConnectionManager.ConnectedTask.class)))
                .thenAnswer(
                        new Answer<>() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                TargetConnectionManager.ConnectedTask task =
                                        (TargetConnectionManager.ConnectedTask)
                                                invocation.getArgument(1);
                                return task.execute(connection);
                            }
                        });
        Mockito.when(connection.getService()).thenReturn(service);
        IRecordingDescriptor descriptor = Mockito.mock(IRecordingDescriptor.class);
        Mockito.when(descriptor.getName()).thenReturn(recordingName);
        Mockito.when(service.getAvailableRecordings()).thenReturn(List.of(descriptor));

        ServiceRef serviceRef1 =
                new ServiceRef(
                        "id1",
                        URIUtil.convert(
                                new JMXServiceURL(
                                        "service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi")),
                        "some.Alias.1");
        ServiceRef serviceRef3 =
                new ServiceRef(
                        "id3",
                        URIUtil.convert(
                                new JMXServiceURL(
                                        "service:jmx:rmi:///jndi/rmi://cryostat:9093/jmxrmi")),
                        "some.Alias.3");

        Mockito.when(platformClient.listDiscoverableServices())
                .thenReturn(List.of(serviceRef1, serviceRef3));
        Mockito.when(connection.getJMXURL())
                .thenReturn(
                        (new JMXServiceURL("service:jmx:rmi:///jndi/rmi://cryostat:9092/jmxrmi")));
        Mockito.when(connection.getHost()).thenReturn("some-hostname.local");

        Instant now = Instant.now();
        Mockito.when(clock.now()).thenReturn(now);
        Mockito.when(fs.exists(Mockito.any())).thenReturn(true).thenReturn(false);
        InputStream stream = new ByteArrayInputStream("someRecordingData".getBytes());
        Mockito.when(service.openStream(descriptor, false)).thenReturn(stream);
        Path specificRecordingsPath = Mockito.mock(Path.class);
        Mockito.when(archivedRecordingsPath.resolve(Mockito.anyString()))
                .thenReturn(specificRecordingsPath);
        String timestamp = now.truncatedTo(ChronoUnit.SECONDS).toString().replaceAll("[-:]+", "");
        String savedName = "some-hostname-local_someRecording_" + timestamp + ".jfr";
        Path destination = Mockito.mock(Path.class);
        Mockito.when(specificRecordingsPath.resolve(Mockito.anyString())).thenReturn(destination);
        Path filenamePath = Mockito.mock(Path.class);
        Path parentPath = Path.of("some", "storage");
        Mockito.when(destination.getParent()).thenReturn(parentPath);
        Mockito.when(filenamePath.toString()).thenReturn(savedName);
        Mockito.when(destination.getFileName()).thenReturn(filenamePath);
        Mockito.when(
                        recordingMetadataManager.copyMetadataToArchives(
                                Mockito.any(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(CompletableFuture.completedFuture(new Metadata()));

        ArchivedRecordingInfo info =
                recordingArchiveHelper
                        .saveRecording(new ConnectionDescriptor(targetId), recordingName)
                        .get();

        MatcherAssert.assertThat(info.getName(), Matchers.equalTo(savedName));
        Mockito.verify(fs).copy(Mockito.isA(BufferedInputStream.class), Mockito.eq(destination));
        Mockito.verify(notificationFactory).createBuilder();
        Mockito.verify(notificationBuilder).metaCategory("ActiveRecordingSaved");
        Mockito.verify(notificationBuilder).metaType(HttpMimeType.JSON);
        Mockito.verify(notificationBuilder).message(Map.of("recording", info, "target", targetId));
        Mockito.verify(notificationBuilder).build();
        Mockito.verify(notification).send();
    }

    @Test
    void shouldSaveRecordingThatEndsWithJfr() throws Exception {
        Mockito.when(jvmIdHelper.getJvmId(Mockito.anyString())).thenReturn("mockId");
        String recordingName = "someRecording.jfr";
        Mockito.when(
                        targetConnectionManager.executeConnectedTask(
                                Mockito.any(),
                                Mockito.any(TargetConnectionManager.ConnectedTask.class)))
                .thenAnswer(
                        new Answer<>() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                TargetConnectionManager.ConnectedTask task =
                                        (TargetConnectionManager.ConnectedTask)
                                                invocation.getArgument(1);
                                return task.execute(connection);
                            }
                        });
        Mockito.when(connection.getService()).thenReturn(service);
        IRecordingDescriptor descriptor = Mockito.mock(IRecordingDescriptor.class);
        Mockito.when(descriptor.getName()).thenReturn(recordingName);
        Mockito.when(service.getAvailableRecordings()).thenReturn(List.of(descriptor));

        ServiceRef serviceRef1 =
                new ServiceRef(
                        "id1",
                        URIUtil.convert(
                                new JMXServiceURL(
                                        "service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi")),
                        "some.Alias.1");
        ServiceRef serviceRef2 =
                new ServiceRef(
                        "id2",
                        URIUtil.convert(
                                new JMXServiceURL(
                                        "service:jmx:rmi:///jndi/rmi://cryostat:9092/jmxrmi")),
                        "some.Alias.2");
        ServiceRef serviceRef3 =
                new ServiceRef(
                        "id3",
                        URIUtil.convert(
                                new JMXServiceURL(
                                        "service:jmx:rmi:///jndi/rmi://cryostat:9093/jmxrmi")),
                        "some.Alias.3");

        Mockito.when(platformClient.listDiscoverableServices())
                .thenReturn(List.of(serviceRef1, serviceRef2, serviceRef3));
        Mockito.when(connection.getJMXURL())
                .thenReturn(
                        (new JMXServiceURL("service:jmx:rmi:///jndi/rmi://cryostat:9092/jmxrmi")));

        Instant now = Instant.now();
        Mockito.when(clock.now()).thenReturn(now);
        Mockito.when(fs.exists(Mockito.any())).thenReturn(true).thenReturn(false);
        InputStream stream = new ByteArrayInputStream("someRecordingData".getBytes());
        Mockito.when(service.openStream(descriptor, false)).thenReturn(stream);
        Path specificRecordingsPath = Mockito.mock(Path.class);
        Mockito.when(archivedRecordingsPath.resolve(Mockito.anyString()))
                .thenReturn(specificRecordingsPath);
        Path destination = Mockito.mock(Path.class);
        Mockito.when(specificRecordingsPath.resolve(Mockito.anyString())).thenReturn(destination);
        String timestamp = now.truncatedTo(ChronoUnit.SECONDS).toString().replaceAll("[-:]+", "");
        String savedName = "some-hostname-local_someRecording_" + timestamp + ".jfr";
        Path filenamePath = Mockito.mock(Path.class);
        Path parentPath = Path.of("some", "storage");
        Mockito.when(destination.getParent()).thenReturn(parentPath);
        Mockito.when(filenamePath.toString()).thenReturn(savedName);
        Mockito.when(destination.getFileName()).thenReturn(filenamePath);
        Mockito.when(
                        recordingMetadataManager.copyMetadataToArchives(
                                Mockito.any(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(CompletableFuture.completedFuture(new Metadata()));

        ArchivedRecordingInfo info =
                recordingArchiveHelper
                        .saveRecording(new ConnectionDescriptor(targetId), recordingName)
                        .get();

        MatcherAssert.assertThat(info.getName(), Matchers.equalTo(savedName));
        Mockito.verify(fs).copy(Mockito.isA(BufferedInputStream.class), Mockito.eq(destination));
        Mockito.verify(notificationFactory).createBuilder();
        Mockito.verify(notificationBuilder).metaCategory("ActiveRecordingSaved");
        Mockito.verify(notificationBuilder).metaType(HttpMimeType.JSON);
        Mockito.verify(notificationBuilder).message(Map.of("recording", info, "target", targetId));
        Mockito.verify(notificationBuilder).build();
        Mockito.verify(notification).send();
    }

    @Test
    void shouldNotSaveEmptyRecording() throws Exception {
        Mockito.when(jvmIdHelper.getJvmId(Mockito.anyString())).thenReturn("mockId");
        Mockito.when(connection.getService()).thenReturn(service);
        IRecordingDescriptor descriptor = Mockito.mock(IRecordingDescriptor.class);
        Mockito.when(descriptor.getName()).thenReturn(recordingName);

        ServiceRef serviceRef1 =
                new ServiceRef(
                        "id1",
                        URIUtil.convert(
                                new JMXServiceURL(
                                        "service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi")),
                        "some.Alias.1");
        ServiceRef serviceRef2 =
                new ServiceRef(
                        "id2",
                        URIUtil.convert(
                                new JMXServiceURL(
                                        "service:jmx:rmi:///jndi/rmi://cryostat:9092/jmxrmi")),
                        "some.Alias.2");
        ServiceRef serviceRef3 =
                new ServiceRef(
                        "id3",
                        URIUtil.convert(
                                new JMXServiceURL(
                                        "service:jmx:rmi:///jndi/rmi://cryostat:9093/jmxrmi")),
                        "some.Alias.3");

        Mockito.when(platformClient.listDiscoverableServices())
                .thenReturn(List.of(serviceRef1, serviceRef2, serviceRef3));
        Mockito.when(connection.getJMXURL())
                .thenReturn(
                        (new JMXServiceURL("service:jmx:rmi:///jndi/rmi://cryostat:9092/jmxrmi")));

        Instant now = Instant.now();
        Mockito.when(clock.now()).thenReturn(now);
        Mockito.when(fs.exists(Mockito.any())).thenReturn(true).thenReturn(false);
        InputStream stream =
                new ByteArrayInputStream("".getBytes()); // intentionally empty recording stream
        Mockito.when(service.openStream(descriptor, false)).thenReturn(stream);
        Path specificRecordingsPath = Mockito.mock(Path.class);
        Mockito.when(archivedRecordingsPath.resolve(Mockito.anyString()))
                .thenReturn(specificRecordingsPath);
        Path destination = Mockito.mock(Path.class);
        Mockito.when(specificRecordingsPath.resolve(Mockito.anyString())).thenReturn(destination);

        Assertions.assertThrows(
                EmptyRecordingException.class,
                () -> recordingArchiveHelper.writeRecordingToDestination(connection, descriptor));

        Mockito.verify(fs).deleteIfExists(Mockito.any());
    }

    @Test
    void shouldSaveRecordingNumberedCopy() throws Exception {
        Mockito.when(jvmIdHelper.getJvmId(Mockito.anyString())).thenReturn("mockId");
        Mockito.when(
                        targetConnectionManager.executeConnectedTask(
                                Mockito.any(),
                                Mockito.any(TargetConnectionManager.ConnectedTask.class)))
                .thenAnswer(
                        new Answer<>() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                TargetConnectionManager.ConnectedTask task =
                                        (TargetConnectionManager.ConnectedTask)
                                                invocation.getArgument(1);
                                return task.execute(connection);
                            }
                        });
        Mockito.when(connection.getService()).thenReturn(service);
        IRecordingDescriptor descriptor = Mockito.mock(IRecordingDescriptor.class);
        Mockito.when(descriptor.getName()).thenReturn(recordingName);
        Mockito.when(service.getAvailableRecordings()).thenReturn(List.of(descriptor));

        ServiceRef serviceRef1 =
                new ServiceRef(
                        "id1",
                        URIUtil.convert(
                                new JMXServiceURL(
                                        "service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi")),
                        "some.Alias.1");
        ServiceRef serviceRef2 =
                new ServiceRef(
                        "id2",
                        URIUtil.convert(
                                new JMXServiceURL(
                                        "service:jmx:rmi:///jndi/rmi://cryostat:9092/jmxrmi")),
                        "some.Alias.2");
        ServiceRef serviceRef3 =
                new ServiceRef(
                        "id3",
                        URIUtil.convert(
                                new JMXServiceURL(
                                        "service:jmx:rmi:///jndi/rmi://cryostat:9093/jmxrmi")),
                        "some.Alias.3");

        Mockito.when(platformClient.listDiscoverableServices())
                .thenReturn(List.of(serviceRef1, serviceRef2, serviceRef3));
        Mockito.when(connection.getJMXURL())
                .thenReturn(
                        (new JMXServiceURL("service:jmx:rmi:///jndi/rmi://cryostat:9092/jmxrmi")));

        Instant now = Instant.now();
        Mockito.when(clock.now()).thenReturn(now);
        Mockito.when(fs.exists(Mockito.any())).thenReturn(true).thenReturn(true).thenReturn(false);
        InputStream stream = new ByteArrayInputStream("someRecordingData".getBytes());
        Mockito.when(service.openStream(descriptor, false)).thenReturn(stream);
        Path specificRecordingsPath = Mockito.mock(Path.class);
        Mockito.when(archivedRecordingsPath.resolve(Mockito.anyString()))
                .thenReturn(specificRecordingsPath);
        Path destination = Mockito.mock(Path.class);
        Mockito.when(specificRecordingsPath.resolve(Mockito.anyString())).thenReturn(destination);
        String timestamp = now.truncatedTo(ChronoUnit.SECONDS).toString().replaceAll("[-:]+", "");
        String savedName = "some-hostname-local_someRecording_" + timestamp + ".jfr";
        Path filenamePath = Mockito.mock(Path.class);
        Path parentPath = Path.of("some", "storage");
        Mockito.when(destination.getParent()).thenReturn(parentPath);
        Mockito.when(filenamePath.toString()).thenReturn(savedName);
        Mockito.when(destination.getFileName()).thenReturn(filenamePath);
        Mockito.when(
                        recordingMetadataManager.copyMetadataToArchives(
                                Mockito.any(), Mockito.anyString(), Mockito.anyString()))
                .thenReturn(CompletableFuture.completedFuture(new Metadata()));

        ArchivedRecordingInfo info =
                recordingArchiveHelper
                        .saveRecording(new ConnectionDescriptor(targetId), recordingName)
                        .get();

        MatcherAssert.assertThat(info.getName(), Matchers.equalTo(savedName));
        Mockito.verify(fs).copy(Mockito.isA(BufferedInputStream.class), Mockito.eq(destination));
        Mockito.verify(notificationFactory).createBuilder();
        Mockito.verify(notificationBuilder).metaCategory("ActiveRecordingSaved");
        Mockito.verify(notificationBuilder).metaType(HttpMimeType.JSON);
        Mockito.verify(notificationBuilder).message(Map.of("recording", info, "target", targetId));
        Mockito.verify(notificationBuilder).build();
        Mockito.verify(notification).send();
    }

    @Test
    void shouldDeleteRecording() throws Exception {
        lenient().when(jvmIdHelper.getJvmId(Mockito.anyString())).thenReturn("mockId");
        String recordingName = "123recording";

        String jvmIdA = "encodedJvmIdA";
        String jvmId123 = "encodedJvmId123";

        Mockito.when(fs.exists(Mockito.any())).thenReturn(true);
        Mockito.when(fs.isRegularFile(Mockito.any())).thenReturn(true);
        Mockito.when(fs.isReadable(Mockito.any())).thenReturn(true);

        List<String> subdirectories = List.of(jvmIdA, jvmId123);
        Mockito.when(fs.listDirectoryChildren(archivedRecordingsPath)).thenReturn(subdirectories);

        Mockito.when(archivedRecordingsPath.resolve(jvmIdA)).thenReturn(Path.of(jvmIdA));
        Mockito.when(fs.listDirectoryChildren(Path.of(jvmIdA)))
                .thenReturn(List.of("recordingA", "connectUrl"));

        Mockito.when(archivedRecordingsPath.resolve(jvmId123)).thenReturn(Path.of(jvmId123));
        Mockito.when(fs.listDirectoryChildren(Path.of(jvmId123)))
                .thenReturn(List.of("123recording", "connectUrl"));

        Mockito.when(fs.listDirectoryChildren(Path.of(jvmId123).normalize().toAbsolutePath()))
                .thenReturn(List.of("123recording", "connectUrl"));

        Mockito.when(webServer.getArchivedReportURL(Mockito.anyString(), Mockito.anyString()))
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                String name = invocation.getArgument(1);
                                return "/some/path/archive/" + name;
                            }
                        });
        Mockito.when(webServer.getArchivedDownloadURL(Mockito.anyString(), Mockito.anyString()))
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                String name = invocation.getArgument(1);
                                return "/some/path/download/" + name;
                            }
                        });

        Mockito.when(
                        recordingMetadataManager.deleteRecordingMetadataIfExists(
                                Mockito.any(ConnectionDescriptor.class), Mockito.anyString()))
                .thenReturn(new Metadata());

        Path tempSubdirectory = Mockito.mock(Path.class);
        Mockito.when(tempSubdirectory.resolve(Mockito.anyString())).thenReturn(destinationFile);
        Mockito.when(destinationFile.toAbsolutePath()).thenReturn(destinationFile);
        Mockito.when(archivedRecordingsReportPath.resolve(Mockito.any(String.class)))
                .thenReturn(tempSubdirectory);

        Mockito.when(fs.exists(Mockito.any(Path.class))).thenReturn(true);
        Mockito.lenient()
                .when(fs.createDirectory(Mockito.any(Path.class)))
                .thenReturn(tempSubdirectory);
        Mockito.when(fs.deleteIfExists(Mockito.any())).thenReturn(true);

        ArchivedRecordingInfo deleted = recordingArchiveHelper.deleteRecording(recordingName).get();

        ArgumentCaptor<Map<String, Object>> messageCaptor = ArgumentCaptor.forClass(Map.class);

        Mockito.verify(fs)
                .deleteIfExists(
                        archivedRecordingsPath
                                .resolve(subdirectories.get(1))
                                .resolve(recordingName)
                                .toAbsolutePath());

        Mockito.verify(fs).deleteIfExists(destinationFile);
        Mockito.verify(notificationFactory).createBuilder();
        Mockito.verify(notificationBuilder).metaCategory("ArchivedRecordingDeleted");
        Mockito.verify(notificationBuilder).metaType(HttpMimeType.JSON);
        Mockito.verify(notificationBuilder).message(messageCaptor.capture());
        Mockito.verify(notificationBuilder).build();
        Mockito.verify(notification).send();

        ArchivedRecordingInfo matcher =
                new ArchivedRecordingInfo(
                        "uploads",
                        recordingName,
                        "/some/path/download/" + recordingName,
                        "/some/path/archive/" + recordingName,
                        new Metadata(),
                        0);

        MatcherAssert.assertThat(deleted, Matchers.equalTo(matcher));

        MatcherAssert.assertThat(
                messageCaptor.getValue(),
                Matchers.equalTo(Map.of("recording", matcher, "target", "uploads")));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void deleteReportShouldDelegateToFileSystem(boolean existToDelete) throws IOException {
        lenient().when(jvmIdHelper.getJvmId(Mockito.anyString())).thenReturn("mockId");
        Path tempSubdirectory = Mockito.mock(Path.class);
        Mockito.when(tempSubdirectory.resolve(Mockito.anyString())).thenReturn(destinationFile);
        Mockito.when(destinationFile.toAbsolutePath()).thenReturn(destinationFile);
        Mockito.when(archivedRecordingsReportPath.resolve(Mockito.any(String.class)))
                .thenReturn(tempSubdirectory);

        Mockito.when(fs.exists(Mockito.any(Path.class))).thenReturn(existToDelete);
        Mockito.lenient()
                .when(fs.createDirectory(Mockito.any(Path.class)))
                .thenReturn(tempSubdirectory);
        Mockito.when(fs.deleteIfExists(Mockito.any())).thenReturn(existToDelete);

        String sourceTarget = null;
        MatcherAssert.assertThat(
                recordingArchiveHelper.deleteReport(sourceTarget, "foo"),
                Matchers.equalTo(existToDelete));

        Mockito.verify(fs).deleteIfExists(destinationFile);
        Mockito.verify(tempSubdirectory).resolve("foo.report.html");
        Mockito.verify(destinationFile).toAbsolutePath();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void deleteReportShouldReturnFalseIfFileSystemThrows(boolean existToDelete) throws IOException {
        Path tempSubdirectory = Mockito.mock(Path.class);
        Mockito.when(tempSubdirectory.resolve(Mockito.anyString())).thenReturn(destinationFile);
        Mockito.when(destinationFile.toAbsolutePath()).thenReturn(destinationFile);
        Mockito.when(archivedRecordingsReportPath.resolve(Mockito.any(String.class)))
                .thenReturn(tempSubdirectory);

        Mockito.when(fs.exists(Mockito.any(Path.class))).thenReturn(existToDelete);
        Mockito.lenient()
                .when(fs.createDirectory(Mockito.any(Path.class)))
                .thenReturn(tempSubdirectory);
        Mockito.when(fs.deleteIfExists(Mockito.any())).thenThrow(IOException.class);

        String sourceTarget = null;
        MatcherAssert.assertThat(
                recordingArchiveHelper.deleteReport(sourceTarget, "foo"), Matchers.equalTo(false));

        Mockito.verify(fs).deleteIfExists(destinationFile);
        Mockito.verify(tempSubdirectory).resolve("foo.report.html");
    }

    @Test
    void shouldGetRecordings() throws Exception {
        lenient().when(jvmIdHelper.getJvmId(Mockito.anyString())).thenReturn("mockId");
        Mockito.when(fs.exists(Mockito.any())).thenReturn(true);
        Mockito.when(fs.isReadable(Mockito.any())).thenReturn(true);
        Mockito.when(fs.isDirectory(Mockito.any())).thenReturn(true);

        List<String> subdirectories = List.of("encodedJvmIdA", "encodedJvmId123");
        Mockito.when(fs.listDirectoryChildren(archivedRecordingsPath)).thenReturn(subdirectories);

        Mockito.when(archivedRecordingsPath.resolve(subdirectories.get(0)))
                .thenReturn(Path.of(subdirectories.get(0)));
        Mockito.when(fs.listDirectoryChildren(Path.of(subdirectories.get(0))))
                .thenReturn(List.of("recordingA", "connectUrl"));

        Mockito.when(archivedRecordingsPath.resolve(subdirectories.get(1)))
                .thenReturn(Path.of(subdirectories.get(1)));
        Mockito.when(fs.listDirectoryChildren(Path.of(subdirectories.get(1))))
                .thenReturn(List.of("123recording", "connectUrl"));

        BufferedReader reader = Mockito.mock(BufferedReader.class);
        Mockito.when(fs.readFile(Mockito.any(Path.class))).thenReturn(reader);
        Mockito.when(reader.readLine()).thenReturn("connectUrlA").thenReturn("connectUrl123");

        Mockito.when(webServer.getArchivedReportURL(Mockito.anyString(), Mockito.anyString()))
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                String name = invocation.getArgument(1);
                                return "/some/path/archive/" + name;
                            }
                        });
        Mockito.when(webServer.getArchivedDownloadURL(Mockito.anyString(), Mockito.anyString()))
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                String name = invocation.getArgument(1);
                                return "/some/path/download/" + name;
                            }
                        });

        Mockito.when(recordingMetadataManager.getMetadata(Mockito.any(), Mockito.anyString()))
                .thenReturn(new Metadata());

        List<ArchivedRecordingInfo> result = recordingArchiveHelper.getRecordings().get();
        List<ArchivedRecordingInfo> expected =
                List.of(
                        new ArchivedRecordingInfo(
                                "connectUrlA",
                                "recordingA",
                                "/some/path/download/recordingA",
                                "/some/path/archive/recordingA",
                                new Metadata(),
                                0),
                        new ArchivedRecordingInfo(
                                "connectUrl123",
                                "123recording",
                                "/some/path/download/123recording",
                                "/some/path/archive/123recording",
                                new Metadata(),
                                0));
        MatcherAssert.assertThat(result, Matchers.equalTo(expected));
    }

    @Test
    void getRecordingsShouldHandleIOException() throws Exception {
        Mockito.when(fs.exists(Mockito.any())).thenReturn(true);
        Mockito.when(fs.isReadable(Mockito.any())).thenReturn(true);
        Mockito.when(fs.isDirectory(Mockito.any())).thenReturn(true);
        Mockito.when(fs.listDirectoryChildren(Mockito.any())).thenThrow(IOException.class);

        Assertions.assertThrows(
                ExecutionException.class,
                () -> {
                    try {
                        recordingArchiveHelper.getRecordings().get();
                    } catch (ExecutionException ee) {
                        Assertions.assertTrue(ee.getCause() instanceof IOException);
                        throw ee;
                    }
                });
    }

    @Test
    void getRecordingsShouldDifferentiateBetweenUploadsAndTarget() throws Exception {
        Mockito.when(fs.exists(Mockito.any())).thenReturn(true);
        Mockito.when(fs.isReadable(Mockito.any())).thenReturn(true);
        Mockito.when(fs.isDirectory(Mockito.any())).thenReturn(true);

        String targetIdUploads = "uploads";
        String targetIdTarget = "someServiceUri";
        lenient()
                .when(jvmIdHelper.getJvmId(Mockito.eq(targetIdUploads)))
                .thenReturn(targetIdUploads);
        lenient().when(jvmIdHelper.getJvmId(Mockito.eq(targetIdTarget))).thenReturn(targetIdTarget);
        Path specificRecordingsPath = Path.of("/some/path/");
        Mockito.when(archivedRecordingsPath.resolve(Mockito.anyString()))
                .thenReturn(specificRecordingsPath);
        Mockito.when(fs.listDirectoryChildren(Mockito.any(Path.class)))
                .thenReturn(List.of("foo_recording"));

        Mockito.when(webServer.getArchivedReportURL(Mockito.anyString(), Mockito.anyString()))
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                String name = invocation.getArgument(1);
                                return "/some/path/archive/" + name;
                            }
                        });
        Mockito.when(webServer.getArchivedDownloadURL(Mockito.anyString(), Mockito.anyString()))
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                String name = invocation.getArgument(1);
                                return "/some/path/download/" + name;
                            }
                        });

        Mockito.when(recordingMetadataManager.getMetadata(Mockito.any(), Mockito.anyString()))
                .thenReturn(new Metadata());

        Mockito.when(jvmIdHelper.jvmIdToSubdirectoryName(Mockito.anyString()))
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                return invocation.getArgument(0);
                            }
                        });

        // Test get recordings from uploads
        List<ArchivedRecordingInfo> result =
                recordingArchiveHelper.getRecordings(targetIdUploads).get();

        Mockito.verify(archivedRecordingsPath).resolve(targetIdUploads);

        List<ArchivedRecordingInfo> expected =
                List.of(
                        new ArchivedRecordingInfo(
                                targetIdUploads,
                                "foo_recording",
                                "/some/path/download/foo_recording",
                                "/some/path/archive/foo_recording",
                                new Metadata(),
                                0));
        MatcherAssert.assertThat(result, Matchers.equalTo(expected));

        // Test get recordings from target
        result = recordingArchiveHelper.getRecordings(targetIdTarget).get();
        Mockito.verify(archivedRecordingsPath).resolve(targetIdTarget);

        expected =
                List.of(
                        new ArchivedRecordingInfo(
                                targetIdTarget,
                                "foo_recording",
                                "/some/path/download/foo_recording",
                                "/some/path/archive/foo_recording",
                                new Metadata(),
                                0));
    }

    @Test
    void shouldGetRecordingsAndDirectories() throws Exception {
        Mockito.when(fs.exists(Mockito.any())).thenReturn(true);
        Mockito.when(fs.isReadable(Mockito.any())).thenReturn(true);
        Mockito.when(fs.isDirectory(Mockito.any())).thenReturn(true);

        List<String> subdirectories = List.of("encodedJvmIdA", "encodedJvmId123");
        Mockito.when(fs.listDirectoryChildren(archivedRecordingsPath)).thenReturn(subdirectories);

        Mockito.when(archivedRecordingsPath.resolve(subdirectories.get(0)))
                .thenReturn(Path.of(subdirectories.get(0)));
        Mockito.when(fs.listDirectoryChildren(Path.of(subdirectories.get(0))))
                .thenReturn(List.of("recordingA", "connectUrl"));

        Mockito.when(archivedRecordingsPath.resolve(subdirectories.get(1)))
                .thenReturn(Path.of(subdirectories.get(1)));
        Mockito.when(fs.listDirectoryChildren(Path.of(subdirectories.get(1))))
                .thenReturn(List.of("123recording", "connectUrl"));

        BufferedReader reader = Mockito.mock(BufferedReader.class);
        Mockito.when(fs.readFile(Mockito.any(Path.class))).thenReturn(reader);
        Mockito.when(reader.readLine()).thenReturn("connectUrlA").thenReturn("connectUrl123");

        Mockito.when(webServer.getArchivedReportURL(Mockito.anyString(), Mockito.anyString()))
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                String name = invocation.getArgument(1);
                                return "/some/path/archive/" + name;
                            }
                        });
        Mockito.when(webServer.getArchivedDownloadURL(Mockito.anyString(), Mockito.anyString()))
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                String name = invocation.getArgument(1);
                                return "/some/path/download/" + name;
                            }
                        });

        Mockito.when(
                        recordingMetadataManager.getMetadataFromPathIfExists(
                                Mockito.any(), Mockito.anyString()))
                .thenReturn(new Metadata());

        List<ArchiveDirectory> result = recordingArchiveHelper.getRecordingsAndDirectories().get();

        List<ArchiveDirectory> expected =
                List.of(
                        new ArchiveDirectory(
                                "connectUrlA",
                                "encodedJvmIdA",
                                List.of(
                                        new ArchivedRecordingInfo(
                                                "connectUrlA",
                                                "recordingA",
                                                "/some/path/download/recordingA",
                                                "/some/path/archive/recordingA",
                                                new Metadata(),
                                                0))),
                        new ArchiveDirectory(
                                "connectUrl123",
                                "encodedJvmId123",
                                List.of(
                                        new ArchivedRecordingInfo(
                                                "connectUrl123",
                                                "123recording",
                                                "/some/path/download/123recording",
                                                "/some/path/archive/123recording",
                                                new Metadata(),
                                                0))));

        MatcherAssert.assertThat(result, Matchers.hasSize(2));
        MatcherAssert.assertThat(
                result.get(0).getConnectUrl(), Matchers.equalTo(expected.get(0).getConnectUrl()));
        MatcherAssert.assertThat(
                result.get(0).getJvmId(), Matchers.equalTo(expected.get(0).getJvmId()));
        MatcherAssert.assertThat(
                result.get(0).getRecordings(), Matchers.equalTo(expected.get(0).getRecordings()));
        MatcherAssert.assertThat(
                result.get(1).getConnectUrl(), Matchers.equalTo(expected.get(1).getConnectUrl()));
        MatcherAssert.assertThat(
                result.get(1).getJvmId(), Matchers.equalTo(expected.get(1).getJvmId()));
        MatcherAssert.assertThat(
                result.get(1).getRecordings(), Matchers.equalTo(expected.get(1).getRecordings()));

        recordingArchiveHelper.getRecordingsAndDirectories().get();
    }
}
