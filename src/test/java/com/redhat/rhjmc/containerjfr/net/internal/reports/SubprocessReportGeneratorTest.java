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
package com.redhat.rhjmc.containerjfr.net.internal.reports;

import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.net.Credentials;
import com.redhat.rhjmc.containerjfr.core.reports.ReportTransformer;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.net.ConnectionDescriptor;
import com.redhat.rhjmc.containerjfr.net.internal.reports.ActiveRecordingReportCache.RecordingDescriptor;
import com.redhat.rhjmc.containerjfr.net.internal.reports.SubprocessReportGenerator.ExitStatus;
import com.redhat.rhjmc.containerjfr.util.JavaProcess;

@ExtendWith(MockitoExtension.class)
class SubprocessReportGeneratorTest {

    @Mock Environment env;
    @Mock FileSystem fs;
    @Mock JavaProcess.Builder javaProcessBuilder;
    @Mock Logger logger;
    @Mock Process proc;
    ConnectionDescriptor connectionDescriptor;
    RecordingDescriptor recordingDescriptor;
    SubprocessReportGenerator generator;

    @BeforeEach
    void setup() throws Exception {
        connectionDescriptor =
                new ConnectionDescriptor("fooHost:1234", new Credentials("someUser", "somePass"));
        recordingDescriptor = new RecordingDescriptor(connectionDescriptor, "testRecording");

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
                .when(env.getEnv(SubprocessReportGenerator.SUBPROCESS_MAX_HEAP_ENV, "200"))
                .thenReturn("200");
        this.generator =
                new SubprocessReportGenerator(
                        env,
                        fs,
                        Set.of(new TestReportTransformer()),
                        () -> javaProcessBuilder,
                        logger);
    }

    @Test
    void shouldThrowIfRecordingDescriptorIsNull() {
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> generator.exec(null, Mockito.mock(Path.class), Duration.ofSeconds(10)));
    }

    @Test
    void shouldThrowIfDestinationFileIsNull() {
        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> generator.exec(recordingDescriptor, null, Duration.ofSeconds(10)));
    }

    @Test
    void shouldWriteSerializedTransformersToFile() throws Exception {
        Path dest = Mockito.mock(Path.class);
        Mockito.when(dest.toAbsolutePath()).thenReturn(dest);
        Mockito.when(dest.toString()).thenReturn("/dest/somefile.tmp");

        generator.exec(recordingDescriptor, dest, Duration.ofSeconds(10));

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

        generator.exec(recordingDescriptor, dest, Duration.ofSeconds(10));

        Mockito.verify(javaProcessBuilder).klazz(SubprocessReportGenerator.class);
    }

    @Test
    void shouldSetEnvVarsForCredentials() throws Exception {
        Path dest = Mockito.mock(Path.class);
        Mockito.when(dest.toAbsolutePath()).thenReturn(dest);
        Mockito.when(dest.toString()).thenReturn("/dest/somefile.tmp");

        generator.exec(recordingDescriptor, dest, Duration.ofSeconds(10));

        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(javaProcessBuilder).env(captor.capture());
        MatcherAssert.assertThat(
                captor.getValue(),
                Matchers.equalTo(
                        Map.of(
                                SubprocessReportGenerator.ENV_USERNAME,
                                "someUser",
                                SubprocessReportGenerator.ENV_PASSWORD,
                                "somePass")));
    }

    @Test
    void shouldSetJvmArgs() throws Exception {
        Path dest = Mockito.mock(Path.class);
        Mockito.when(dest.toAbsolutePath()).thenReturn(dest);
        Mockito.when(dest.toString()).thenReturn("/dest/somefile.tmp");

        Mockito.when(env.getEnv("SSL_TRUSTSTORE")).thenReturn("/some/truststore.p12");
        Mockito.when(env.getEnv("SSL_TRUSTSTORE_PASS")).thenReturn("THEPASSWORD");

        generator.exec(recordingDescriptor, dest, Duration.ofSeconds(10));

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(javaProcessBuilder).jvmArgs(captor.capture());

        List<String> expected =
                List.of(
                        "-Xmx200M",
                        "-XX:+ExitOnOutOfMemoryError",
                        "-XX:+UnlockExperimentalVMOptions",
                        "-XX:+UseEpsilonGC",
                        "-XX:+AlwaysPreTouch",
                        "-Djavax.net.ssl.trustStore=/some/truststore.p12",
                        "-Djavax.net.ssl.trustStorePassword=THEPASSWORD");
        MatcherAssert.assertThat(captor.getValue(), Matchers.equalTo(expected));
    }

    @Test
    void shouldSetProcessArgs() throws Exception {
        Path dest = Mockito.mock(Path.class);
        Mockito.when(dest.toAbsolutePath()).thenReturn(dest);
        Mockito.when(dest.toString()).thenReturn("/dest/somefile.tmp");

        generator.exec(recordingDescriptor, dest, Duration.ofSeconds(10));

        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        Mockito.verify(javaProcessBuilder).processArgs(captor.capture());

        List<String> expected = List.of("fooHost:1234", "testRecording", "/dest/somefile.tmp");
        MatcherAssert.assertThat(captor.getValue(), Matchers.equalTo(expected));
    }

    @Test
    void shouldExecuteProcessAndReturnPathOnOkExit() throws Exception {
        Path dest = Mockito.mock(Path.class);
        Mockito.when(dest.toAbsolutePath()).thenReturn(dest);
        Mockito.when(dest.toString()).thenReturn("/dest/somefile.tmp");
        Mockito.when(proc.waitFor(10_000, TimeUnit.MILLISECONDS)).thenReturn(true);

        Assertions.assertTimeoutPreemptively(
                Duration.ofSeconds(2),
                () -> {
                    Future<Path> path =
                            generator.exec(recordingDescriptor, dest, Duration.ofSeconds(10));
                    MatcherAssert.assertThat(path.get(), Matchers.sameInstance(dest));
                });
    }

    @Test
    void shouldExecuteProcessAndThrowExceptionOnNonOkExit() throws Exception {
        Path dest = Mockito.mock(Path.class);
        Mockito.when(dest.toAbsolutePath()).thenReturn(dest);
        Mockito.when(dest.toString()).thenReturn("/dest/somefile.tmp");
        Mockito.when(proc.waitFor(10_000, TimeUnit.MILLISECONDS)).thenReturn(false);
        Mockito.when(proc.exitValue()).thenReturn(ExitStatus.NO_SUCH_RECORDING.code);

        Assertions.assertTimeoutPreemptively(
                Duration.ofSeconds(2),
                () -> {
                    ExecutionException ex =
                            Assertions.assertThrows(
                                    ExecutionException.class,
                                    () ->
                                            generator
                                                    .exec(
                                                            recordingDescriptor,
                                                            dest,
                                                            Duration.ofSeconds(10))
                                                    .get());
                    MatcherAssert.assertThat(
                            ex.getMessage(),
                            Matchers.containsString(
                                    "Recording testRecording not found in target fooHost:1234"));
                });
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
