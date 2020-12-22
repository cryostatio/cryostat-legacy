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
package com.redhat.rhjmc.containerjfr.net.reports;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

import javax.inject.Provider;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.reports.ReportTransformer;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.net.ConnectionDescriptor;
import com.redhat.rhjmc.containerjfr.net.TargetConnectionManager;
import com.redhat.rhjmc.containerjfr.net.reports.ReportService.RecordingNotFoundException;
import com.redhat.rhjmc.containerjfr.net.reports.SubprocessReportGenerator.ExitStatus;
import com.redhat.rhjmc.containerjfr.net.reports.SubprocessReportGenerator.RecordingDescriptor;
import com.redhat.rhjmc.containerjfr.util.JavaProcess;

@ExtendWith(MockitoExtension.class)
class ActiveRecordingReportCacheTest {

    ActiveRecordingReportCache cache;
    @Mock SubprocessReportGenerator subprocessReportGenerator;
    @Mock Environment env;
    @Mock FileSystem fs;
    @Mock ReentrantLock lock;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock Logger logger;
    @Mock Future<Path> pathFuture;
    @Mock Path destinationFile;
    @Mock JavaProcess.Builder javaProcessBuilder;
    Provider<JavaProcess.Builder> javaProcessBuilderProvider = () -> javaProcessBuilder;
    Provider<Path> tempFileProvider = () -> destinationFile;
    final String REPORT_DOC = "<html><body><p>This is a report</p></body></html>";

    class TestSubprocessReportGenerator extends SubprocessReportGenerator {
        TestSubprocessReportGenerator(FileSystem fs, Set<ReportTransformer> reportTransformers) {
            super(
                    env,
                    fs,
                    targetConnectionManager,
                    reportTransformers,
                    javaProcessBuilderProvider,
                    tempFileProvider,
                    logger);
        }
    }

    @BeforeEach
    void setup() {
        this.cache =
                new ActiveRecordingReportCache(
                        () -> subprocessReportGenerator, fs, lock, targetConnectionManager, logger);
    }

    @Test
    void shouldReturnFalseWhenDeletingNonExistentReport() {
        Assertions.assertFalse(cache.delete(new ConnectionDescriptor("foo"), "bar"));
    }

    @Test
    void shouldReturnTrueWhenDeletingReport() throws Exception {
        Mockito.when(pathFuture.get()).thenReturn(destinationFile);
        Mockito.when(
                        subprocessReportGenerator.exec(
                                Mockito.any(RecordingDescriptor.class),
                                Mockito.any(Duration.class)))
                .thenReturn(pathFuture);
        Mockito.when(fs.readString(destinationFile)).thenReturn(REPORT_DOC);

        String targetId = "foo";
        String recordingName = "bar";

        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor(targetId);
        cache.get(connectionDescriptor, recordingName);
        Assertions.assertTrue(cache.delete(connectionDescriptor, recordingName));
    }

    @Test
    void shouldReturnGeneratedReportResult() throws Exception {
        Mockito.when(pathFuture.get()).thenReturn(destinationFile);
        Mockito.when(
                        subprocessReportGenerator.exec(
                                Mockito.any(RecordingDescriptor.class),
                                Mockito.any(Duration.class)))
                .thenReturn(pathFuture);
        Mockito.when(fs.readString(destinationFile)).thenReturn(REPORT_DOC);

        String targetId = "foo";

        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor(targetId);
        Future<String> report = cache.get(connectionDescriptor, "foo");
        MatcherAssert.assertThat(report.get(), Matchers.equalTo(REPORT_DOC));

        InOrder inOrder = Mockito.inOrder(lock, subprocessReportGenerator, fs);
        inOrder.verify(lock).lock();

        inOrder.verify(subprocessReportGenerator)
                .exec(Mockito.any(RecordingDescriptor.class), Mockito.any(Duration.class));

        inOrder.verify(fs).readString(destinationFile);

        inOrder.verify(lock).unlock();
    }

    @Test
    void shouldReturnCachedReportResultOnSecondRequest() throws Exception {
        Mockito.when(pathFuture.get()).thenReturn(destinationFile);
        Mockito.when(
                        subprocessReportGenerator.exec(
                                Mockito.any(RecordingDescriptor.class),
                                Mockito.any(Duration.class)))
                .thenReturn(pathFuture);
        Mockito.when(fs.readString(destinationFile)).thenReturn(REPORT_DOC);

        String targetId = "foo";
        String recordingName = "bar";

        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor(targetId);
        String report1 = cache.get(connectionDescriptor, recordingName).get();
        MatcherAssert.assertThat(report1, Matchers.equalTo(REPORT_DOC));
        String report2 = cache.get(connectionDescriptor, recordingName).get();
        MatcherAssert.assertThat(report2, Matchers.equalTo(report1));

        InOrder inOrder = Mockito.inOrder(lock, subprocessReportGenerator);
        inOrder.verify(lock, Mockito.times(1)).lock();

        inOrder.verify(subprocessReportGenerator, Mockito.times(1))
                .exec(Mockito.any(RecordingDescriptor.class), Mockito.any(Duration.class));

        inOrder.verify(lock, Mockito.times(1)).unlock();
    }

    @Test
    void shouldThrowExceptionIfRecordingNotFound() throws Exception {
        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor("foo");
        Mockito.when(
                        subprocessReportGenerator.exec(
                                Mockito.any(RecordingDescriptor.class),
                                Mockito.any(Duration.class)))
                .thenThrow(new CompletionException(new RecordingNotFoundException("", "")));
        Assertions.assertThrows(
                ExecutionException.class, () -> cache.get(connectionDescriptor, "bar").get());
    }

    @Test
    void shouldThrowExceptionIfSubprocessExitsNonCleanly() throws Exception {
        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor("foo");
        Mockito.when(
                        subprocessReportGenerator.exec(
                                Mockito.any(RecordingDescriptor.class),
                                Mockito.any(Duration.class)))
                .thenThrow(
                        new CompletionException(
                                new SubprocessReportGenerator.ReportGenerationException(
                                        ExitStatus.OTHER)));
        Assertions.assertThrows(
                ExecutionException.class, () -> cache.get(connectionDescriptor, "bar").get());
    }
}
