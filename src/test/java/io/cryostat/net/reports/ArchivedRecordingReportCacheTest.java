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

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingNotFoundException;
import io.cryostat.rules.ArchivePathException;

import org.apache.commons.lang3.exception.ExceptionUtils;
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
class ArchivedRecordingReportCacheTest {

    ArchivedRecordingReportCache cache;
    String sourceTarget;
    String recordingName;

    @Mock CompletableFuture<Path> pathFuture;
    @Mock Path destinationFile;
    @Mock FileSystem fs;
    @Mock SubprocessReportGenerator subprocessReportGenerator;
    @Mock Logger logger;
    @Mock RecordingArchiveHelper recordingArchiveHelper;

    @BeforeEach
    void setup() {
        this.cache =
                new ArchivedRecordingReportCache(
                        fs, () -> subprocessReportGenerator, recordingArchiveHelper, 30, logger);
        this.sourceTarget = "service:jmx:rmi://localhost:9091/jndi/rmi://fooHost:9091/jmxrmi";
        this.recordingName = "foo";
    }

    @Test
    void getShouldThrowIfNoCacheAndNoRecording() throws Exception {
        CompletableFuture<Path> future1 = Mockito.mock(CompletableFuture.class);
        Mockito.when(future1.get()).thenReturn(destinationFile);

        Mockito.when(recordingArchiveHelper.getCachedReportPath(sourceTarget, recordingName, ""))
                .thenReturn(future1);

        Mockito.when(fs.isReadable(Mockito.any(Path.class))).thenReturn(false);
        Mockito.when(fs.deleteIfExists(Mockito.any(Path.class))).thenReturn(false);

        CompletableFuture<Path> future2 = Mockito.mock(CompletableFuture.class);
        Mockito.when(future2.get())
                .thenThrow(
                        new ExecutionException(
                                new RecordingNotFoundException(
                                        RecordingArchiveHelper.ARCHIVES, recordingName)));

        Mockito.when(
                        recordingArchiveHelper.getRecordingPath(
                                Mockito.nullable(String.class), Mockito.eq(recordingName)))
                .thenReturn(future2);

        ExecutionException ee =
                Assertions.assertThrows(
                        ExecutionException.class,
                        () -> cache.get(sourceTarget, recordingName, "").get());

        MatcherAssert.assertThat(
                ExceptionUtils.getRootCause(ee),
                Matchers.instanceOf(RecordingNotFoundException.class));
        Mockito.verify(fs, Mockito.atLeastOnce()).isReadable(destinationFile);
        Mockito.verify(fs, Mockito.atLeastOnce()).deleteIfExists(destinationFile);
    }

    @Test
    void getShouldGenerateAndCacheReport() throws Exception {
        CompletableFuture<Path> future1 = Mockito.mock(CompletableFuture.class);
        Mockito.when(future1.get()).thenReturn(destinationFile);

        Mockito.when(recordingArchiveHelper.getCachedReportPath(sourceTarget, recordingName, ""))
                .thenReturn(future1);

        Mockito.when(fs.isReadable(Mockito.any(Path.class))).thenReturn(false);

        CompletableFuture<Path> future2 = Mockito.mock(CompletableFuture.class);
        Path recording = Mockito.mock(Path.class);
        Mockito.when(future2.get()).thenReturn(recording);

        Mockito.when(
                        recordingArchiveHelper.getRecordingPath(
                                Mockito.nullable(String.class), Mockito.anyString()))
                .thenReturn(future2);

        Mockito.when(pathFuture.get(Mockito.anyLong(), Mockito.any())).thenReturn(destinationFile);

        Mockito.when(
                        subprocessReportGenerator.exec(
                                Mockito.any(Path.class),
                                Mockito.any(Path.class),
                                Mockito.anyString()))
                .thenReturn(pathFuture);

        Future<Path> res = cache.get(sourceTarget, recordingName, "");

        MatcherAssert.assertThat(res.get(), Matchers.sameInstance(destinationFile));
        Mockito.verify(subprocessReportGenerator).exec(recording, destinationFile, "");
        Mockito.verify(fs, Mockito.atLeastOnce()).isReadable(destinationFile);
    }

    @Test
    void getShouldGenerateAndCacheReportFiltered() throws Exception {
        CompletableFuture<Path> future1 = Mockito.mock(CompletableFuture.class);
        Mockito.when(future1.get()).thenReturn(destinationFile);

        Mockito.when(
                        recordingArchiveHelper.getCachedReportPath(
                                sourceTarget, recordingName, "someFilter"))
                .thenReturn(future1);

        Mockito.when(fs.isReadable(Mockito.any(Path.class))).thenReturn(false);

        CompletableFuture<Path> future2 = Mockito.mock(CompletableFuture.class);
        Path recording = Mockito.mock(Path.class);
        Mockito.when(future2.get()).thenReturn(recording);

        Mockito.when(
                        recordingArchiveHelper.getRecordingPath(
                                Mockito.nullable(String.class), Mockito.anyString()))
                .thenReturn(future2);

        Mockito.when(pathFuture.get(Mockito.anyLong(), Mockito.any())).thenReturn(destinationFile);

        Mockito.when(
                        subprocessReportGenerator.exec(
                                Mockito.any(Path.class),
                                Mockito.any(Path.class),
                                Mockito.anyString()))
                .thenReturn(pathFuture);

        Future<Path> res = cache.get(sourceTarget, recordingName, "someFilter");

        MatcherAssert.assertThat(res.get(), Matchers.sameInstance(destinationFile));
        Mockito.verify(subprocessReportGenerator).exec(recording, destinationFile, "someFilter");
        Mockito.verify(fs, Mockito.atLeastOnce()).isReadable(destinationFile);
    }

    @Test
    void getShouldGenerateReportUnformatted() throws Exception {
        CompletableFuture<Path> future1 = Mockito.mock(CompletableFuture.class);
        Mockito.when(future1.get()).thenReturn(destinationFile);

        Mockito.when(
                        recordingArchiveHelper.getCachedReportPath(
                                sourceTarget, recordingName, "someFilter"))
                .thenReturn(future1);

        Mockito.when(fs.isReadable(Mockito.any(Path.class))).thenReturn(false);

        CompletableFuture<Path> future2 = Mockito.mock(CompletableFuture.class);
        Path recording = Mockito.mock(Path.class);
        Mockito.when(future2.get()).thenReturn(recording);

        Mockito.when(
                        recordingArchiveHelper.getRecordingPath(
                                Mockito.nullable(String.class), Mockito.anyString()))
                .thenReturn(future2);

        Mockito.when(pathFuture.get(Mockito.anyLong(), Mockito.any())).thenReturn(destinationFile);

        Mockito.when(
                        subprocessReportGenerator.exec(
                                Mockito.any(Path.class),
                                Mockito.any(Path.class),
                                Mockito.anyString()))
                .thenReturn(pathFuture);

        Future<Path> res = cache.get(sourceTarget, recordingName, "someFilter");

        MatcherAssert.assertThat(res.get(), Matchers.sameInstance(destinationFile));
        Mockito.verify(subprocessReportGenerator).exec(recording, destinationFile, "someFilter");
        Mockito.verify(fs, Mockito.atLeastOnce()).isReadable(destinationFile);
    }

    @Test
    void getShouldReturnCachedFileIfAvailable() throws Exception {
        CompletableFuture<Path> future = Mockito.mock(CompletableFuture.class);
        Mockito.when(future.get()).thenReturn(destinationFile);

        Mockito.when(recordingArchiveHelper.getCachedReportPath(sourceTarget, recordingName, ""))
                .thenReturn(future);

        Mockito.when(fs.isReadable(Mockito.any(Path.class))).thenReturn(true);
        Mockito.when(fs.isRegularFile(Mockito.any(Path.class))).thenReturn(true);

        Future<Path> res = cache.get(sourceTarget, recordingName, "");

        MatcherAssert.assertThat(res.get(), Matchers.sameInstance(destinationFile));
        Mockito.verifyNoInteractions(subprocessReportGenerator);
        Mockito.verify(fs).isReadable(destinationFile);
        Mockito.verify(fs).isRegularFile(destinationFile);
    }

    @Test
    void shouldThrowErrorIfReportGenerationFails() throws Exception {
        CompletableFuture<Path> future1 = Mockito.mock(CompletableFuture.class);
        Mockito.when(future1.get()).thenReturn(destinationFile);

        Mockito.when(recordingArchiveHelper.getCachedReportPath(sourceTarget, recordingName, ""))
                .thenReturn(future1);

        Mockito.when(fs.isReadable(Mockito.any(Path.class))).thenReturn(false);

        CompletableFuture<Path> future2 = Mockito.mock(CompletableFuture.class);
        Path recording = Mockito.mock(Path.class);
        Mockito.when(future2.get()).thenReturn(recording);

        Mockito.when(
                        recordingArchiveHelper.getRecordingPath(
                                Mockito.nullable(String.class), Mockito.anyString()))
                .thenReturn(future2);

        Mockito.when(
                        subprocessReportGenerator.exec(
                                Mockito.any(Path.class),
                                Mockito.any(Path.class),
                                Mockito.anyString()))
                .thenThrow(
                        new CompletionException(
                                new SubprocessReportGenerator.SubprocessReportGenerationException(
                                        SubprocessReportGenerator.ExitStatus.OUT_OF_MEMORY)));

        ExecutionException ee =
                Assertions.assertThrows(
                        ExecutionException.class, () -> cache.get(sourceTarget, "foo", "").get());

        MatcherAssert.assertThat(
                ExceptionUtils.getRootCause(ee),
                Matchers.instanceOf(
                        SubprocessReportGenerator.SubprocessReportGenerationException.class));
        Mockito.verify(fs, Mockito.atLeastOnce()).isReadable(destinationFile);
    }

    @Test
    void shouldThrowIfCachedPathResolutionFails() throws Exception {
        CompletableFuture<Path> future1 = Mockito.mock(CompletableFuture.class);
        Mockito.when(future1.get()).thenThrow(new CompletionException(new IOException()));

        Mockito.when(recordingArchiveHelper.getCachedReportPath(sourceTarget, recordingName, ""))
                .thenReturn(future1);

        Mockito.when(fs.deleteIfExists(Mockito.nullable(Path.class))).thenReturn(false);

        ExecutionException ee =
                Assertions.assertThrows(
                        ExecutionException.class, () -> cache.get(sourceTarget, "foo", "").get());
        MatcherAssert.assertThat(
                ExceptionUtils.getRootCause(ee), Matchers.instanceOf(IOException.class));

        Mockito.verify(fs, Mockito.atLeastOnce()).deleteIfExists(null);
    }

    @Test
    void shouldThrowIfRecordingPathResolutionFails() throws Exception {
        CompletableFuture<Path> future1 = Mockito.mock(CompletableFuture.class);
        Mockito.when(future1.get()).thenReturn(destinationFile);

        Mockito.when(recordingArchiveHelper.getCachedReportPath(sourceTarget, recordingName, ""))
                .thenReturn(future1);

        CompletableFuture<Path> future2 = Mockito.mock(CompletableFuture.class);
        Mockito.when(future2.get())
                .thenThrow(
                        new CompletionException(
                                new ArchivePathException("/path/to/foo", "does not exist")));

        Mockito.when(recordingArchiveHelper.getRecordingPath(sourceTarget, recordingName))
                .thenReturn(future2);

        Mockito.when(fs.isReadable(Mockito.any(Path.class))).thenReturn(true);
        Mockito.when(fs.deleteIfExists(Mockito.nullable(Path.class))).thenReturn(false);

        ExecutionException ee =
                Assertions.assertThrows(
                        ExecutionException.class, () -> cache.get(sourceTarget, "foo", "").get());
        MatcherAssert.assertThat(
                ExceptionUtils.getRootCause(ee), Matchers.instanceOf(ArchivePathException.class));

        Mockito.verify(fs, Mockito.atLeastOnce()).deleteIfExists(destinationFile);
    }
}
