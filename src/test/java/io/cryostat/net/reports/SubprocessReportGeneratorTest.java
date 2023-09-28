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

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.cryostat.core.log.Logger;
import io.cryostat.core.net.Credentials;
import io.cryostat.core.sys.Environment;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.util.JavaProcess;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
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
class SubprocessReportGeneratorTest {

    @Mock Environment env;
    @Mock FileSystem fs;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock JavaProcess.Builder javaProcessBuilder;
    @Mock Logger logger;
    @Mock Process proc;
    ConnectionDescriptor connectionDescriptor;
    RecordingDescriptor recordingDescriptor;
    @Mock Path recordingFile;
    @Mock Path tempFile1;
    @Mock Path tempFile2;
    SubprocessReportGenerator generator;

    @BeforeEach
    void setup() throws Exception {
        connectionDescriptor =
                new ConnectionDescriptor("fooHost:1234", new Credentials("someUser", "somePass"));
        recordingDescriptor = new RecordingDescriptor(connectionDescriptor, "testRecording");

        Mockito.lenient()
                .when(fs.createTempFile(null, null))
                .thenReturn(tempFile1)
                .thenReturn(tempFile2);
        Mockito.lenient().when(tempFile1.toAbsolutePath()).thenReturn(tempFile1);
        Mockito.lenient().when(tempFile1.toString()).thenReturn("/tmp/file1.tmp");
        Mockito.lenient().when(tempFile2.toAbsolutePath()).thenReturn(tempFile2);
        Mockito.lenient().when(tempFile2.toString()).thenReturn("/tmp/file2.tmp");
        Mockito.lenient().when(recordingFile.toAbsolutePath()).thenReturn(recordingFile);
        Mockito.lenient().when(recordingFile.toString()).thenReturn("/dest/recording.tmp");

        Mockito.lenient()
                .when(javaProcessBuilder.env(Mockito.anyMap()))
                .thenReturn(javaProcessBuilder);
        Mockito.lenient()
                .when(javaProcessBuilder.jvmArgs(Mockito.anyList()))
                .thenReturn(javaProcessBuilder);
        Mockito.lenient()
                .when(javaProcessBuilder.klazz(Mockito.any(Class.class)))
                .thenReturn(javaProcessBuilder);
        Mockito.lenient()
                .when(javaProcessBuilder.processArgs(Mockito.anyList()))
                .thenReturn(javaProcessBuilder);
        Mockito.lenient().when(javaProcessBuilder.exec()).thenReturn(proc);
        Mockito.lenient()
                .when(
                        env.getEnv(
                                Mockito.eq("CRYOSTAT_REPORT_GENERATION_MAX_HEAP"),
                                Mockito.anyString()))
                .thenReturn("200");
        this.generator =
                new SubprocessReportGenerator(
                        env, fs, targetConnectionManager, () -> javaProcessBuilder, 30, logger);
    }

    @Test
    void shouldThrowIfRecordingPathIsNull() {
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> generator.exec(null, Mockito.mock(Path.class), ""));
    }

    @Test
    void shouldThrowIfDestinationFileIsNull() {
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> generator.exec(recordingFile, null, ""));
    }

    @Test
    void shouldThrowIfFilterIsNull() {
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> generator.exec(recordingFile, Mockito.mock(Path.class), null));
    }

    @Test
    void shouldUseSelfAsForkedProcess() throws Exception {
        Path dest = Mockito.mock(Path.class);
        Mockito.when(dest.toAbsolutePath()).thenReturn(dest);
        Mockito.when(dest.toString()).thenReturn("/dest/somefile.tmp");

        generator.exec(recordingFile, dest, "");

        Mockito.verify(javaProcessBuilder).klazz(SubprocessReportGenerator.class);
    }

    @Test
    void shouldSetJvmArgs() throws Exception {
        Path dest = Mockito.mock(Path.class);
        Mockito.when(dest.toAbsolutePath()).thenReturn(dest);
        Mockito.when(dest.toString()).thenReturn("/dest/somefile.tmp");

        generator.exec(recordingFile, dest, "");

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(javaProcessBuilder).jvmArgs(captor.capture());

        List<String> expected =
                List.of("-Xms200M", "-Xmx200M", "-XX:+ExitOnOutOfMemoryError", "-XX:+UseSerialGC");
        MatcherAssert.assertThat(captor.getValue(), Matchers.equalTo(expected));
    }

    @Test
    void shouldSetJvmArgsWithoutReportMaxHeapEnvVar() throws Exception {
        Path dest = Mockito.mock(Path.class);
        Mockito.when(dest.toAbsolutePath()).thenReturn(dest);
        Mockito.when(dest.toString()).thenReturn("/dest/somefile.tmp");
        Mockito.when(
                        env.getEnv(
                                Mockito.eq("CRYOSTAT_REPORT_GENERATION_MAX_HEAP"),
                                Mockito.anyString()))
                .thenReturn("0");

        generator.exec(recordingFile, dest, "");

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(javaProcessBuilder).jvmArgs(captor.capture());

        List<String> expected = List.of("-XX:+ExitOnOutOfMemoryError", "-XX:+UseSerialGC");
        MatcherAssert.assertThat(captor.getValue(), Matchers.equalTo(expected));
    }

    @Test
    void shouldSetProcessArgs() throws Exception {
        Path dest = Mockito.mock(Path.class);
        Mockito.when(dest.toAbsolutePath()).thenReturn(dest);
        Mockito.when(dest.toString()).thenReturn("/dest/somefile.tmp");

        generator.exec(recordingFile, dest, "");

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(javaProcessBuilder).processArgs(captor.capture());

        List<String> expected = List.of("/dest/recording.tmp", "/dest/somefile.tmp", "");
        MatcherAssert.assertThat(captor.getValue(), Matchers.equalTo(expected));
    }

    @Test
    void shouldSetProcessArgsFilteredUnformatted() throws Exception {
        Path dest = Mockito.mock(Path.class);
        Mockito.when(dest.toAbsolutePath()).thenReturn(dest);
        Mockito.when(dest.toString()).thenReturn("/dest/somefile.tmp");

        generator.exec(recordingFile, dest, "someFilter");

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(javaProcessBuilder).processArgs(captor.capture());

        List<String> expected = List.of("/dest/recording.tmp", "/dest/somefile.tmp", "someFilter");
        MatcherAssert.assertThat(captor.getValue(), Matchers.equalTo(expected));
    }

    @Test
    void shouldExecuteProcessAndReturnPathOnOkExit() throws Exception {
        Path dest = Mockito.mock(Path.class);
        Mockito.when(dest.toAbsolutePath()).thenReturn(dest);
        Mockito.when(dest.toString()).thenReturn("/dest/somefile.tmp");
        Mockito.when(proc.waitFor(29, TimeUnit.SECONDS)).thenReturn(true);

        Assertions.assertTimeoutPreemptively(
                Duration.ofSeconds(2),
                () -> {
                    Future<Path> path = generator.exec(recordingFile, dest, "");
                    MatcherAssert.assertThat(path.get(), Matchers.sameInstance(dest));
                });
    }

    @Test
    void shouldExecuteProcessAndThrowExceptionOnNonOkExit() throws Exception {
        Path dest = Mockito.mock(Path.class);
        Mockito.when(dest.toAbsolutePath()).thenReturn(dest);
        Mockito.when(dest.toString()).thenReturn("/dest/somefile.tmp");
        Mockito.when(proc.waitFor(29, TimeUnit.SECONDS)).thenReturn(false);
        Mockito.when(proc.exitValue())
                .thenReturn(SubprocessReportGenerator.ExitStatus.NO_SUCH_RECORDING.code);
        Assertions.assertTimeoutPreemptively(
                Duration.ofSeconds(2),
                () -> {
                    ExecutionException ex =
                            Assertions.assertThrows(
                                    ExecutionException.class,
                                    () -> generator.exec(recordingFile, dest, "").get());
                    MatcherAssert.assertThat(
                            ex.getMessage(),
                            Matchers.containsString(
                                    "Recording /dest/recording.tmp was not found in the target"
                                            + " [archives]."));
                });
    }

    @Test
    void shouldExecuteProcessAndDeleteRecordingOnCompletion() throws Exception {
        Mockito.when(proc.waitFor(29, TimeUnit.SECONDS)).thenReturn(true);
        Mockito.when(proc.exitValue()).thenReturn(SubprocessReportGenerator.ExitStatus.OK.code);

        Mockito.when(targetConnectionManager.executeConnectedTask(Mockito.any(), Mockito.any()))
                .then(
                        new Answer<Path>() {
                            @Override
                            public Path answer(InvocationOnMock invocation) throws Throwable {
                                return fs.createTempFile(null, null);
                            }
                        });

        Path result = generator.exec(recordingDescriptor, "").get();

        MatcherAssert.assertThat(result, Matchers.sameInstance(tempFile2));
        Mockito.verify(fs).deleteIfExists(tempFile1);
    }

    @Test
    void shouldExecuteProcessAndDeleteRecordingOnFailure() throws Exception {
        Mockito.when(proc.waitFor(29, TimeUnit.SECONDS)).thenReturn(true);
        Mockito.when(proc.exitValue())
                .thenReturn(SubprocessReportGenerator.ExitStatus.NO_SUCH_RECORDING.code);

        Mockito.when(targetConnectionManager.executeConnectedTask(Mockito.any(), Mockito.any()))
                .then(
                        new Answer<Path>() {
                            @Override
                            public Path answer(InvocationOnMock invocation) throws Throwable {
                                return fs.createTempFile(null, null);
                            }
                        });

        Assertions.assertThrows(
                ExecutionException.class,
                () -> {
                    generator.exec(recordingDescriptor, "").get();
                });

        Mockito.verify(fs).deleteIfExists(tempFile1);
    }
}
