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
package io.cryostat.net.reports;

import static org.mockito.ArgumentMatchers.anyString;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.inject.Provider;

import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Environment;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.jmc.serialization.HyperlinkedSerializableRecordingDescriptor;
import io.cryostat.messaging.notifications.Notification;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.recordings.RecordingNotFoundException;
import io.cryostat.recordings.RecordingTargetHelper;
import io.cryostat.util.JavaProcess;

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
class ActiveRecordingReportCacheTest {

    ActiveRecordingReportCache cache;
    @Mock SubprocessReportGenerator subprocessReportGenerator;
    @Mock Environment env;
    @Mock FileSystem fs;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock Logger logger;
    @Mock CompletableFuture<Path> pathFuture;
    @Mock Path destinationFile;
    @Mock JavaProcess.Builder javaProcessBuilder;
    Provider<JavaProcess.Builder> javaProcessBuilderProvider = () -> javaProcessBuilder;
    final String REPORT_DOC = "<html><body><p>This is a report</p></body></html>";
    final String REPORT_JSON = "{\"report\": \"This is an unformatted report\"";

    @BeforeEach
    void setup() {
        this.cache =
                new ActiveRecordingReportCache(
                        () -> subprocessReportGenerator,
                        fs,
                        targetConnectionManager,
                        30,
                        30,
                        30,
                        logger);
    }

    @Test
    void shouldReturnFalseWhenDeletingNonExistentReport() {
        Assertions.assertFalse(cache.delete(new ConnectionDescriptor("foo"), "bar"));
    }

    @Test
    void shouldReturnTrueWhenDeletingReport() throws Exception {
        Mockito.when(pathFuture.get(Mockito.anyLong(), Mockito.any())).thenReturn(destinationFile);
        Mockito.when(
                        subprocessReportGenerator.exec(
                                Mockito.any(RecordingDescriptor.class), anyString()))
                .thenReturn(pathFuture);
        Mockito.when(fs.readString(destinationFile)).thenReturn(REPORT_DOC);

        String targetId = "foo";
        String recordingName = "bar";

        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor(targetId);
        cache.get(connectionDescriptor, recordingName, "");
        Assertions.assertTrue(cache.delete(connectionDescriptor, recordingName));
    }

    @Test
    void shouldReturnGeneratedReportResult() throws Exception {
        Mockito.when(pathFuture.get(Mockito.anyLong(), Mockito.any())).thenReturn(destinationFile);
        Mockito.when(
                        subprocessReportGenerator.exec(
                                Mockito.any(RecordingDescriptor.class), anyString()))
                .thenReturn(pathFuture);
        Mockito.when(fs.readString(destinationFile)).thenReturn(REPORT_DOC);

        String targetId = "foo";

        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor(targetId);
        Future<String> report = cache.get(connectionDescriptor, "foo", "");
        MatcherAssert.assertThat(report.get(), Matchers.equalTo(REPORT_DOC));

        Mockito.verify(subprocessReportGenerator)
                .exec(Mockito.any(RecordingDescriptor.class), Mockito.eq(""));
        Mockito.verify(fs).readString(destinationFile);
    }

    @Test
    void shouldReturnGeneratedReportResultFiltered() throws Exception {
        Mockito.when(pathFuture.get(Mockito.anyLong(), Mockito.any())).thenReturn(destinationFile);
        Mockito.when(
                        subprocessReportGenerator.exec(
                                Mockito.any(RecordingDescriptor.class), anyString()))
                .thenReturn(pathFuture);
        Mockito.when(fs.readString(destinationFile)).thenReturn(REPORT_DOC);

        String targetId = "foo";

        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor(targetId);
        Future<String> report = cache.get(connectionDescriptor, "foo", "non-null");
        MatcherAssert.assertThat(report.get(), Matchers.equalTo(REPORT_DOC));

        Mockito.verify(subprocessReportGenerator)
                .exec(Mockito.any(RecordingDescriptor.class), Mockito.eq("non-null"));
        Mockito.verify(fs).readString(destinationFile);
    }

    @Test
    void shouldReturnGeneratedReportResultUnformatted() throws Exception {
        Mockito.when(pathFuture.get(Mockito.anyLong(), Mockito.any())).thenReturn(destinationFile);
        Mockito.when(
                        subprocessReportGenerator.exec(
                                Mockito.any(RecordingDescriptor.class), anyString()))
                .thenReturn(pathFuture);
        Mockito.when(fs.readString(destinationFile)).thenReturn(REPORT_JSON);

        String targetId = "foo";

        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor(targetId);
        Future<String> report = cache.get(connectionDescriptor, "foo", "non-null");
        MatcherAssert.assertThat(report.get(), Matchers.equalTo(REPORT_JSON));

        Mockito.verify(subprocessReportGenerator)
                .exec(Mockito.any(RecordingDescriptor.class), Mockito.eq("non-null"));
        Mockito.verify(fs).readString(destinationFile);
    }

    @Test
    void shouldReturnCachedReportResultOnSecondRequest() throws Exception {
        Mockito.when(pathFuture.get(Mockito.anyLong(), Mockito.any())).thenReturn(destinationFile);
        Mockito.when(
                        subprocessReportGenerator.exec(
                                Mockito.any(RecordingDescriptor.class), anyString()))
                .thenReturn(pathFuture);
        Mockito.when(fs.readString(destinationFile)).thenReturn(REPORT_DOC);

        String targetId = "foo";
        String recordingName = "bar";

        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor(targetId);
        String report1 = cache.get(connectionDescriptor, recordingName, "").get();
        MatcherAssert.assertThat(report1, Matchers.equalTo(REPORT_DOC));
        String report2 = cache.get(connectionDescriptor, recordingName, "").get();
        MatcherAssert.assertThat(report2, Matchers.equalTo(report1));

        Mockito.verify(subprocessReportGenerator, Mockito.times(1))
                .exec(Mockito.any(RecordingDescriptor.class), anyString());
    }

    @SuppressWarnings("rawtypes")
    @Test
    void shouldReturnUncachedReportWhenRecordingStopped() throws Exception {
        Mockito.when(pathFuture.get(Mockito.anyLong(), Mockito.any())).thenReturn(destinationFile);
        Mockito.when(
                        subprocessReportGenerator.exec(
                                Mockito.any(RecordingDescriptor.class), anyString()))
                .thenReturn(pathFuture);
        Mockito.when(fs.readString(destinationFile)).thenReturn(REPORT_DOC);

        String targetId = "foo";
        String recordingName = "bar";

        Notification notification = Mockito.mock(Notification.class);
        HyperlinkedSerializableRecordingDescriptor hsrd =
                Mockito.mock(HyperlinkedSerializableRecordingDescriptor.class);
        Mockito.when(hsrd.getName()).thenReturn(recordingName);
        Mockito.when(notification.getCategory())
                .thenReturn(RecordingTargetHelper.STOP_NOTIFICATION_CATEGORY);
        Mockito.when(notification.getMessage())
                .thenReturn(Map.of("target", targetId, "recording", hsrd));

        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor(targetId);
        String report1 = cache.get(connectionDescriptor, recordingName, "").get();
        MatcherAssert.assertThat(report1, Matchers.equalTo(REPORT_DOC));
        cache.onNotification(notification);
        String report2 = cache.get(connectionDescriptor, recordingName, "").get();
        MatcherAssert.assertThat(report2, Matchers.equalTo(report1));

        Mockito.verify(subprocessReportGenerator, Mockito.times(2))
                .exec(Mockito.any(RecordingDescriptor.class), anyString());
    }

    @Test
    void shouldThrowExceptionIfRecordingNotFound() throws Exception {
        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor("foo");
        Mockito.when(
                        subprocessReportGenerator.exec(
                                Mockito.any(RecordingDescriptor.class), anyString()))
                .thenThrow(new CompletionException(new RecordingNotFoundException("", "")));
        Assertions.assertThrows(
                ExecutionException.class, () -> cache.get(connectionDescriptor, "bar", "").get());
    }

    @Test
    void shouldThrowExceptionIfSubprocessExitsNonCleanly() throws Exception {
        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor("foo");
        Mockito.when(
                        subprocessReportGenerator.exec(
                                Mockito.any(RecordingDescriptor.class), anyString()))
                .thenThrow(
                        new CompletionException(
                                new SubprocessReportGenerator.SubprocessReportGenerationException(
                                        SubprocessReportGenerator.ExitStatus.OTHER)));
        Assertions.assertThrows(
                ExecutionException.class, () -> cache.get(connectionDescriptor, "bar", "").get());
    }
}
