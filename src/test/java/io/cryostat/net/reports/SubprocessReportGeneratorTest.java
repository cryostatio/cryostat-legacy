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

import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import io.cryostat.configuration.Variables;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.Credentials;
import io.cryostat.core.reports.ReportTransformer;
import io.cryostat.core.sys.Environment;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.util.JavaProcess;

import com.google.gson.Gson;
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
class SubprocessReportGeneratorTest {

    @Mock Environment env;
    @Mock Gson gson;
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
                        env,
                        gson,
                        fs,
                        targetConnectionManager,
                        Set.of(new TestReportTransformer()),
                        () -> javaProcessBuilder,
                        30,
                        logger);
    }

    @Test
    void shouldThrowIfRecordingPathIsNull() {
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> generator.exec(null, Mockito.mock(Path.class), "", true));
    }

    @Test
    void shouldThrowIfDestinationFileIsNull() {
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> generator.exec(recordingFile, null, "", true));
    }

    @Test
    void shouldThrowIfFilterIsNull() {
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> generator.exec(recordingFile, Mockito.mock(Path.class), null, true));
    }

    @Test
    void shouldWriteSerializedTransformersToFile() throws Exception {
        Path dest = Mockito.mock(Path.class);
        Mockito.when(dest.toAbsolutePath()).thenReturn(dest);
        Mockito.when(dest.toString()).thenReturn("/dest/somefile.tmp");

        generator.exec(recordingFile, dest, "", true);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(fs)
                .writeString(
                        Mockito.same(dest),
                        captor.capture(),
                        Mockito.same(StandardOpenOption.CREATE),
                        Mockito.same(StandardOpenOption.TRUNCATE_EXISTING),
                        Mockito.same(StandardOpenOption.DSYNC),
                        Mockito.same(StandardOpenOption.WRITE));
        String serialized = captor.getValue();
        MatcherAssert.assertThat(
                serialized, Matchers.equalTo(TestReportTransformer.class.getCanonicalName()));
    }

    @Test
    void shouldUseSelfAsForkedProcess() throws Exception {
        Path dest = Mockito.mock(Path.class);
        Mockito.when(dest.toAbsolutePath()).thenReturn(dest);
        Mockito.when(dest.toString()).thenReturn("/dest/somefile.tmp");

        generator.exec(recordingFile, dest, "", true);

        Mockito.verify(javaProcessBuilder).klazz(SubprocessReportGenerator.class);
    }

    @Test
    void shouldSetJvmArgs() throws Exception {
        Path dest = Mockito.mock(Path.class);
        Mockito.when(dest.toAbsolutePath()).thenReturn(dest);
        Mockito.when(dest.toString()).thenReturn("/dest/somefile.tmp");

        generator.exec(recordingFile, dest, "", true);

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

        generator.exec(recordingFile, dest, "", true);

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

        generator.exec(recordingFile, dest, "", true);

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(javaProcessBuilder).processArgs(captor.capture());

        List<String> expected = List.of("/dest/recording.tmp", "/dest/somefile.tmp", "", "true");
        MatcherAssert.assertThat(captor.getValue(), Matchers.equalTo(expected));
    }

    @Test
    void shouldSetProcessArgsFilteredUnformatted() throws Exception {
        Path dest = Mockito.mock(Path.class);
        Mockito.when(dest.toAbsolutePath()).thenReturn(dest);
        Mockito.when(dest.toString()).thenReturn("/dest/somefile.tmp");

        generator.exec(recordingFile, dest, "someFilter", false);

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(javaProcessBuilder).processArgs(captor.capture());

        List<String> expected =
                List.of("/dest/recording.tmp", "/dest/somefile.tmp", "someFilter", "false");
        MatcherAssert.assertThat(captor.getValue(), Matchers.equalTo(expected));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldExecuteProcessAndReturnPathOnOkExit(boolean formatted) throws Exception {
        Path dest = Mockito.mock(Path.class);
        Mockito.when(dest.toAbsolutePath()).thenReturn(dest);
        Mockito.when(dest.toString()).thenReturn("/dest/somefile.tmp");
        Mockito.when(proc.waitFor(29, TimeUnit.SECONDS)).thenReturn(true);

        Assertions.assertTimeoutPreemptively(
                Duration.ofSeconds(2),
                () -> {
                    Future<Path> path = generator.exec(recordingFile, dest, "", formatted);
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
                                    () -> generator.exec(recordingFile, dest, "", true).get());
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

        Path result = generator.exec(recordingDescriptor, "", true).get();

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
                    generator.exec(recordingDescriptor, "", true).get();
                });

        Mockito.verify(fs).deleteIfExists(tempFile1);
    }

    @Test
    void shouldExecuteProcessAndDeleteTempReportStatsFileOnFailure() throws Exception {
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
                    generator.exec(recordingDescriptor, "", true).get();
                });

        Mockito.verify(fs).deleteIfExists(Path.of(Variables.REPORT_STATS_PATH));
    }

    static class TestReportTransformer implements ReportTransformer {
        @Override
        public int priority() {
            return 0;
        }

        @Override
        public String selector() {
            return ".test";
        }

        @Override
        public String innerHtml(String innerHtml) {
            return "Hello World " + innerHtml;
        }
    }
}
