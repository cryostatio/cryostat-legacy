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

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.net.reports.SubprocessReportGenerator.ExitStatus;
import com.redhat.rhjmc.containerjfr.net.reports.SubprocessReportGenerator.ReportGenerationException;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ArchivedRecordingReportCacheTest {

    ArchivedRecordingReportCache cache;
    @Mock Path savedRecordingsPath;
    @Mock Path webServerTempPath;
    @Mock CompletableFuture<Path> pathFuture;
    @Mock Path destinationFile;
    @Mock FileSystem fs;
    @Mock SubprocessReportGenerator subprocessReportGenerator;
    @Mock ReentrantLock generationLock;
    @Mock Logger logger;

    @BeforeEach
    void setup() {
        this.cache =
                new ArchivedRecordingReportCache(
                        savedRecordingsPath,
                        webServerTempPath,
                        fs,
                        () -> subprocessReportGenerator,
                        generationLock,
                        logger);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void deleteShouldDelegateToFileSystem(boolean deleted) throws IOException {
        Mockito.when(fs.deleteIfExists(Mockito.any())).thenReturn(deleted);
        Mockito.when(webServerTempPath.resolve(Mockito.anyString())).thenReturn(destinationFile);
        Mockito.when(destinationFile.toAbsolutePath()).thenReturn(destinationFile);

        MatcherAssert.assertThat(cache.delete("foo"), Matchers.equalTo(deleted));

        Mockito.verify(fs).deleteIfExists(destinationFile);
        Mockito.verify(webServerTempPath).resolve("foo.report.html");
    }

    @Test
    void deleteShouldReturnFalseIfFileSystemThrows() throws IOException {
        Mockito.when(fs.deleteIfExists(Mockito.any())).thenThrow(IOException.class);
        Mockito.when(webServerTempPath.resolve(Mockito.anyString())).thenReturn(destinationFile);
        Mockito.when(destinationFile.toAbsolutePath()).thenReturn(destinationFile);

        MatcherAssert.assertThat(cache.delete("foo"), Matchers.equalTo(false));

        Mockito.verify(fs).deleteIfExists(destinationFile);
        Mockito.verify(webServerTempPath).resolve("foo.report.html");
    }

    @Test
    void getShouldThrowIfNoCacheAndNoRecording() throws IOException {
        Mockito.when(webServerTempPath.resolve(Mockito.anyString())).thenReturn(destinationFile);
        Mockito.when(destinationFile.toAbsolutePath()).thenReturn(destinationFile);
        Mockito.when(fs.isReadable(Mockito.any())).thenReturn(false);

        Mockito.when(fs.listDirectoryChildren(Mockito.any())).thenReturn(List.of());

        Assertions.assertThrows(ExecutionException.class, () -> cache.get("foo").get());

        Mockito.verify(webServerTempPath).resolve("foo.report.html");
        Mockito.verify(fs, Mockito.atLeastOnce()).isReadable(destinationFile);
        InOrder lockOrder = Mockito.inOrder(generationLock);
        lockOrder.verify(generationLock).lock();
        lockOrder.verify(generationLock).unlock();
    }

    @Test
    void getShouldGenerateAndCacheReport() throws Exception {
        Path recording = Mockito.mock(Path.class);
        Mockito.when(savedRecordingsPath.resolve(Mockito.anyString())).thenReturn(recording);
        Mockito.when(webServerTempPath.resolve(Mockito.anyString())).thenReturn(destinationFile);
        Mockito.when(destinationFile.toAbsolutePath()).thenReturn(destinationFile);
        Mockito.when(fs.isReadable(Mockito.any())).thenReturn(false);

        Mockito.when(fs.listDirectoryChildren(Mockito.any())).thenReturn(List.of("foo"));

        Mockito.when(pathFuture.get()).thenReturn(destinationFile);
        Mockito.when(
                        subprocessReportGenerator.exec(
                                Mockito.any(Path.class),
                                Mockito.any(Path.class),
                                Mockito.any(Duration.class)))
                .thenReturn(pathFuture);

        Future<Path> res = cache.get("foo");

        MatcherAssert.assertThat(res.get(), Matchers.sameInstance(destinationFile));
        Mockito.verify(webServerTempPath).resolve("foo.report.html");
        Mockito.verify(fs, Mockito.atLeastOnce()).isReadable(destinationFile);
        InOrder lockOrder = Mockito.inOrder(generationLock);
        lockOrder.verify(generationLock).lock();
        lockOrder.verify(generationLock).unlock();
    }

    @Test
    void getShouldReturnCachedFileIfAvailable() throws Exception {
        Path dest = Mockito.mock(Path.class);
        Mockito.when(webServerTempPath.resolve(Mockito.anyString())).thenReturn(dest);
        Mockito.when(dest.toAbsolutePath()).thenReturn(dest);
        Mockito.when(fs.isReadable(Mockito.any())).thenReturn(true);
        Mockito.when(fs.isRegularFile(Mockito.any())).thenReturn(true);

        Future<Path> res = cache.get("foo");

        MatcherAssert.assertThat(res.get(), Matchers.sameInstance(dest));
        Mockito.verify(webServerTempPath).resolve("foo.report.html");
        Mockito.verify(fs).isReadable(dest);
        Mockito.verify(fs).isRegularFile(dest);
        Mockito.verifyNoInteractions(generationLock);
    }

    @Test
    void shouldThrowErrorIfReportGenerationFails() throws Exception {
        Path recording = Mockito.mock(Path.class);
        Mockito.when(savedRecordingsPath.resolve(Mockito.anyString())).thenReturn(recording);
        Mockito.when(webServerTempPath.resolve(Mockito.anyString())).thenReturn(destinationFile);
        Mockito.when(destinationFile.toAbsolutePath()).thenReturn(destinationFile);
        Mockito.when(fs.isReadable(Mockito.any())).thenReturn(false);

        Mockito.when(fs.listDirectoryChildren(Mockito.any())).thenReturn(List.of("foo"));

        Mockito.when(
                        subprocessReportGenerator.exec(
                                Mockito.any(Path.class),
                                Mockito.any(Path.class),
                                Mockito.any(Duration.class)))
                .thenThrow(
                        new CompletionException(
                                new ReportGenerationException(ExitStatus.OUT_OF_MEMORY)));

        Assertions.assertThrows(ExecutionException.class, () -> cache.get("foo").get());

        Mockito.verify(webServerTempPath).resolve("foo.report.html");
        Mockito.verify(fs, Mockito.atLeastOnce()).isReadable(destinationFile);
        InOrder lockOrder = Mockito.inOrder(generationLock);
        lockOrder.verify(generationLock).lock();
        lockOrder.verify(generationLock).unlock();
    }
}
