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
package io.cryostat.net.reports;

import static org.mockito.ArgumentMatchers.anyBoolean;
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
        Assertions.assertFalse(cache.delete(new ConnectionDescriptor("foo"), "bar", false));
    }

    @Test
    void shouldReturnTrueWhenDeletingReport() throws Exception {
        Mockito.when(pathFuture.get(Mockito.anyLong(), Mockito.any())).thenReturn(destinationFile);
        Mockito.when(
                        subprocessReportGenerator.exec(
                                Mockito.any(RecordingDescriptor.class),
                                anyString(),
                                Mockito.anyBoolean()))
                .thenReturn(pathFuture);
        Mockito.when(fs.readString(destinationFile)).thenReturn(REPORT_DOC);

        String targetId = "foo";
        String recordingName = "bar";

        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor(targetId);
        cache.get(connectionDescriptor, recordingName, "", true);
        Assertions.assertTrue(cache.delete(connectionDescriptor, recordingName, true));
    }

    @Test
    void shouldReturnGeneratedReportResult() throws Exception {
        Mockito.when(pathFuture.get(Mockito.anyLong(), Mockito.any())).thenReturn(destinationFile);
        Mockito.when(
                        subprocessReportGenerator.exec(
                                Mockito.any(RecordingDescriptor.class), anyString(), anyBoolean()))
                .thenReturn(pathFuture);
        Mockito.when(fs.readString(destinationFile)).thenReturn(REPORT_DOC);

        String targetId = "foo";

        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor(targetId);
        Future<String> report = cache.get(connectionDescriptor, "foo", "", true);
        MatcherAssert.assertThat(report.get(), Matchers.equalTo(REPORT_DOC));

        Mockito.verify(subprocessReportGenerator)
                .exec(Mockito.any(RecordingDescriptor.class), Mockito.eq(""), Mockito.eq(true));
        Mockito.verify(fs).readString(destinationFile);
    }

    @Test
    void shouldReturnGeneratedReportResultFiltered() throws Exception {
        Mockito.when(pathFuture.get(Mockito.anyLong(), Mockito.any())).thenReturn(destinationFile);
        Mockito.when(
                        subprocessReportGenerator.exec(
                                Mockito.any(RecordingDescriptor.class), anyString(), anyBoolean()))
                .thenReturn(pathFuture);
        Mockito.when(fs.readString(destinationFile)).thenReturn(REPORT_DOC);

        String targetId = "foo";

        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor(targetId);
        Future<String> report = cache.get(connectionDescriptor, "foo", "non-null", true);
        MatcherAssert.assertThat(report.get(), Matchers.equalTo(REPORT_DOC));

        Mockito.verify(subprocessReportGenerator)
                .exec(
                        Mockito.any(RecordingDescriptor.class),
                        Mockito.eq("non-null"),
                        Mockito.eq(true));
        Mockito.verify(fs).readString(destinationFile);
    }

    @Test
    void shouldReturnGeneratedReportResultUnformatted() throws Exception {
        Mockito.when(pathFuture.get(Mockito.anyLong(), Mockito.any())).thenReturn(destinationFile);
        Mockito.when(
                        subprocessReportGenerator.exec(
                                Mockito.any(RecordingDescriptor.class), anyString(), anyBoolean()))
                .thenReturn(pathFuture);
        Mockito.when(fs.readString(destinationFile)).thenReturn(REPORT_JSON);

        String targetId = "foo";

        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor(targetId);
        Future<String> report = cache.get(connectionDescriptor, "foo", "non-null", false);
        MatcherAssert.assertThat(report.get(), Matchers.equalTo(REPORT_JSON));

        Mockito.verify(subprocessReportGenerator)
                .exec(
                        Mockito.any(RecordingDescriptor.class),
                        Mockito.eq("non-null"),
                        Mockito.eq(false));
        Mockito.verify(fs).readString(destinationFile);
    }

    @Test
    void shouldReturnCachedReportResultOnSecondRequest() throws Exception {
        Mockito.when(pathFuture.get(Mockito.anyLong(), Mockito.any())).thenReturn(destinationFile);
        Mockito.when(
                        subprocessReportGenerator.exec(
                                Mockito.any(RecordingDescriptor.class), anyString(), anyBoolean()))
                .thenReturn(pathFuture);
        Mockito.when(fs.readString(destinationFile)).thenReturn(REPORT_DOC);

        String targetId = "foo";
        String recordingName = "bar";

        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor(targetId);
        String report1 = cache.get(connectionDescriptor, recordingName, "", true).get();
        MatcherAssert.assertThat(report1, Matchers.equalTo(REPORT_DOC));
        String report2 = cache.get(connectionDescriptor, recordingName, "", true).get();
        MatcherAssert.assertThat(report2, Matchers.equalTo(report1));

        Mockito.verify(subprocessReportGenerator, Mockito.times(1))
                .exec(Mockito.any(RecordingDescriptor.class), anyString(), anyBoolean());
    }

    @SuppressWarnings("rawtypes")
    @Test
    void shouldReturnUncachedReportWhenRecordingStopped() throws Exception {
        Mockito.when(pathFuture.get(Mockito.anyLong(), Mockito.any())).thenReturn(destinationFile);
        Mockito.when(
                        subprocessReportGenerator.exec(
                                Mockito.any(RecordingDescriptor.class), anyString(), anyBoolean()))
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
        String report1 = cache.get(connectionDescriptor, recordingName, "", true).get();
        MatcherAssert.assertThat(report1, Matchers.equalTo(REPORT_DOC));
        cache.onNotification(notification);
        String report2 = cache.get(connectionDescriptor, recordingName, "", true).get();
        MatcherAssert.assertThat(report2, Matchers.equalTo(report1));

        Mockito.verify(subprocessReportGenerator, Mockito.times(2))
                .exec(Mockito.any(RecordingDescriptor.class), anyString(), anyBoolean());
    }

    @Test
    void shouldThrowExceptionIfRecordingNotFound() throws Exception {
        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor("foo");
        Mockito.when(
                        subprocessReportGenerator.exec(
                                Mockito.any(RecordingDescriptor.class), anyString(), anyBoolean()))
                .thenThrow(new CompletionException(new RecordingNotFoundException("", "")));
        Assertions.assertThrows(
                ExecutionException.class,
                () -> cache.get(connectionDescriptor, "bar", "", true).get());
    }

    @Test
    void shouldThrowExceptionIfSubprocessExitsNonCleanly() throws Exception {
        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor("foo");
        Mockito.when(
                        subprocessReportGenerator.exec(
                                Mockito.any(RecordingDescriptor.class), anyString(), anyBoolean()))
                .thenThrow(
                        new CompletionException(
                                new SubprocessReportGenerator.SubprocessReportGenerationException(
                                        SubprocessReportGenerator.ExitStatus.OTHER)));
        Assertions.assertThrows(
                ExecutionException.class,
                () -> cache.get(connectionDescriptor, "bar", "", true).get());
    }
}
