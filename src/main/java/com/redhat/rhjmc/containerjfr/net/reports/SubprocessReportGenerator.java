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
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.inject.Provider;

import org.openjdk.jmc.rjmx.ConnectionException;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import com.redhat.rhjmc.containerjfr.core.ContainerJfrCore;
import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.reports.ReportGenerator;
import com.redhat.rhjmc.containerjfr.core.reports.ReportTransformer;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.net.ConnectionDescriptor;
import com.redhat.rhjmc.containerjfr.net.TargetConnectionManager;
import com.redhat.rhjmc.containerjfr.net.reports.ReportService.RecordingNotFoundException;
import com.redhat.rhjmc.containerjfr.util.JavaProcess;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class SubprocessReportGenerator {

    static final String SUBPROCESS_MAX_HEAP_ENV = "CONTAINER_JFR_REPORT_GENERATION_MAX_HEAP";
    static String ENV_USERNAME = "TARGET_USERNAME";
    static String ENV_PASSWORD = "TARGET_PASSWORD";

    private final Environment env;
    private final FileSystem fs;
    private final TargetConnectionManager targetConnectionManager;
    private final Set<ReportTransformer> reportTransformers;
    private final Provider<JavaProcess.Builder> javaProcessBuilderProvider;
    // FIXME extract TempFileProvider to FileSystem
    private final Provider<Path> tempFileProvider;
    private final Logger logger;

    SubprocessReportGenerator(
            Environment env,
            FileSystem fs,
            TargetConnectionManager targetConnectionManager,
            Set<ReportTransformer> reportTransformers,
            Provider<JavaProcess.Builder> javaProcessBuilderProvider,
            Provider<Path> tempFileProvider,
            Logger logger) {
        this.env = env;
        this.fs = fs;
        this.targetConnectionManager = targetConnectionManager;
        this.reportTransformers = reportTransformers;
        this.javaProcessBuilderProvider = javaProcessBuilderProvider;
        this.tempFileProvider = tempFileProvider;
        this.logger = logger;
    }

    CompletableFuture<Path> exec(Path recording, Path saveFile, Duration timeout)
            throws NoSuchMethodException, SecurityException, IllegalAccessException,
                    IllegalArgumentException, InvocationTargetException, IOException,
                    InterruptedException, ReportGenerationException {
        if (recording == null) {
            throw new IllegalArgumentException("Recording may not be null");
        }
        if (saveFile == null) {
            throw new IllegalArgumentException("Destination may not be null");
        }
        fs.writeString(
                saveFile,
                serializeTransformersSet(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.DSYNC,
                StandardOpenOption.WRITE);
        Process proc =
                javaProcessBuilderProvider
                        .get()
                        .klazz(SubprocessReportGenerator.class)
                        // FIXME the heap size should be determined by some heuristics if not
                        // defined in env.
                        // See https://github.com/rh-jmc-team/container-jfr/issues/287
                        .jvmArgs(
                                createJvmArgs(
                                        Integer.parseInt(
                                                env.getEnv(SUBPROCESS_MAX_HEAP_ENV, "200"))))
                        .processArgs(createProcessArgs(recording, saveFile))
                        .exec();
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        proc.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
                        ExitStatus status = ExitStatus.byExitCode(proc.exitValue());
                        switch (status) {
                            case OK:
                                return saveFile;
                            case NO_SUCH_RECORDING:
                                throw new RecordingNotFoundException(
                                        "archives", recording.toString());
                            default:
                                throw new ReportGenerationException(status);
                        }
                    } catch (InterruptedException e) {
                        logger.error(e);
                        proc.destroyForcibly();
                        throw new CompletionException(
                                new ReportGenerationException(ExitStatus.TERMINATED));
                    } catch (ReportGenerationException | RecordingNotFoundException e) {
                        logger.error(e);
                        proc.destroyForcibly();
                        throw new CompletionException(e);
                    } finally {
                        proc.destroyForcibly();
                    }
                });
    }

    Future<Path> exec(RecordingDescriptor recordingDescriptor, Duration timeout) throws Exception {
        Path recording =
                getRecordingFromLiveTarget(
                        recordingDescriptor.recordingName,
                        recordingDescriptor.connectionDescriptor);
        Path saveFile = tempFileProvider.get();
        CompletableFuture<Path> cf = exec(recording, saveFile, timeout);
        return cf.whenComplete(
                (p, t) -> {
                    try {
                        fs.deleteIfExists(recording);
                    } catch (IOException e) {
                        logger.warn(e);
                    }
                });
    }

    Path getRecordingFromLiveTarget(String recordingName, ConnectionDescriptor cd)
            throws Exception {
        return this.targetConnectionManager.executeConnectedTask(
                cd, conn -> copyRecordingToFile(conn, recordingName, tempFileProvider.get()));
    }

    Path copyRecordingToFile(JFRConnection conn, String recordingName, Path path) throws Exception {
        for (IRecordingDescriptor rec : conn.getService().getAvailableRecordings()) {
            if (!Objects.equals(rec.getName(), recordingName)) {
                continue;
            }
            try (InputStream stream = conn.getService().openStream(rec, false)) {
                this.fs.copy(stream, path, StandardCopyOption.REPLACE_EXISTING);
                return path;
            }
        }
        throw new ReportGenerationException(ExitStatus.NO_SUCH_RECORDING);
    }

    private List<String> createJvmArgs(int maxHeapMegabytes) throws IOException {
        // These JVM flags must be kept in-sync with the flags set on the parent process in
        // entrypoint.sh in order to keep the auth and certs setup consistent
        return List.of(
                String.format("-Xmx%dM", maxHeapMegabytes),
                "-XX:+ExitOnOutOfMemoryError",
                // use EpsilonGC since we're a one-shot process and report generation doesn't
                // allocate that much memory beyond the initial recording load - no point in
                // over-complicating memory allocation. Preferable to just complete the request
                // quickly, or if we're running up against the memory limit, fail early
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+UseEpsilonGC",
                "-XX:+AlwaysPreTouch");
    }

    private List<String> createProcessArgs(Path recording, Path saveFile) {
        return List.of(recording.toAbsolutePath().toString(), saveFile.toAbsolutePath().toString());
    }

    private String serializeTransformersSet() {
        var sb = new StringBuilder();
        for (var rt : reportTransformers) {
            sb.append(rt.getClass().getCanonicalName());
            sb.append(System.lineSeparator());
        }
        return sb.toString().trim();
    }

    static Set<ReportTransformer> deserializeTransformers(String serial)
            throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                    InvocationTargetException, NoSuchMethodException, SecurityException,
                    ClassNotFoundException {
        var st = new StringTokenizer(serial);
        var res = new HashSet<ReportTransformer>();
        while (st.hasMoreTokens()) {
            // TODO does it ever make sense that a ReportTransformer would have constructor
            // arguments, or otherwise require state? How would we handle that here if so?
            res.add(
                    (ReportTransformer)
                            Class.forName(st.nextToken()).getDeclaredConstructor().newInstance());
        }
        return res;
    }

    public static void main(String[] args) {
        long startTime = System.nanoTime();
        Logger.INSTANCE.info(SubprocessReportGenerator.class.getName() + " starting");
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    long elapsedTime = System.nanoTime() - startTime;
                                    Logger.INSTANCE.info(
                                            "{} shutting down after {}ms",
                                            SubprocessReportGenerator.class.getName(),
                                            TimeUnit.NANOSECONDS.toMillis(elapsedTime));
                                }));

        var fs = new FileSystem();
        try {
            ContainerJfrCore.initialize();
            // If we're on a system that supports it, set our own OOM score adjustment to
            // +1000 to ensure we're killed first if memory runs out
            Path selfProc = fs.pathOf("/proc/self");
            if (fs.isDirectory(selfProc)) {
                Logger.INSTANCE.info(
                        SubprocessReportGenerator.class.getName()
                                + " adjusting subprocess OOM score");
                Path oomScoreAdj = selfProc.resolve("oom_score_adj");
                fs.writeString(oomScoreAdj, "1000");
            } else {
                Logger.INSTANCE.info(
                        SubprocessReportGenerator.class.getName()
                                + "/proc/self does not exist; ignoring OOM score adjustment");
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(ExitStatus.OTHER.code);
        }

        if (args.length != 2) {
            throw new IllegalArgumentException(Arrays.asList(args).toString());
        }
        var recording = Paths.get(args[0]);
        Set<ReportTransformer> transformers = Collections.emptySet();
        var saveFile = Paths.get(args[1]);
        try {
            transformers = deserializeTransformers(fs.readString(saveFile));
        } catch (Exception e) {
            Logger.INSTANCE.error(e);
            System.exit(ExitStatus.OTHER.code);
        }

        try {
            Logger.INSTANCE.info(SubprocessReportGenerator.class.getName() + " processing report");
            String report = generateReportFromFile(recording, transformers);
            Logger.INSTANCE.info(
                    SubprocessReportGenerator.class.getName() + " writing report to file");

            fs.writeString(
                    saveFile,
                    report,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.DSYNC,
                    StandardOpenOption.WRITE);
            System.exit(ExitStatus.OK.code);
        } catch (ConnectionException e) {
            e.printStackTrace();
            System.exit(ExitStatus.TARGET_CONNECTION_FAILURE.code);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(ExitStatus.IO_EXCEPTION.code);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(ExitStatus.OTHER.code);
        }
    }

    static String generateReportFromFile(Path recording, Set<ReportTransformer> transformers)
            throws Exception {
        var fs = new FileSystem();
        if (!fs.isRegularFile(recording)) {
            throw new ReportGenerationException(ExitStatus.NO_SUCH_RECORDING);
        }
        try (InputStream stream = fs.newInputStream(recording)) {
            return new ReportGenerator(Logger.INSTANCE, transformers).generateReport(stream);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            throw new ReportGenerationException(ExitStatus.IO_EXCEPTION);
        }
    }

    static class RecordingDescriptor {
        final ConnectionDescriptor connectionDescriptor;
        final String recordingName;

        RecordingDescriptor(ConnectionDescriptor connectionDescriptor, String recordingName) {
            this.connectionDescriptor = Objects.requireNonNull(connectionDescriptor);
            this.recordingName = Objects.requireNonNull(recordingName);
        }

        @Override
        public boolean equals(Object other) {
            if (other == null) {
                return false;
            }
            if (other == this) {
                return true;
            }
            if (!(other instanceof RecordingDescriptor)) {
                return false;
            }
            RecordingDescriptor rd = (RecordingDescriptor) other;
            return new EqualsBuilder()
                    .append(connectionDescriptor, rd.connectionDescriptor)
                    .append(recordingName, rd.recordingName)
                    .isEquals();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder()
                    .append(connectionDescriptor)
                    .append(recordingName)
                    .hashCode();
        }
    }

    public enum ExitStatus {
        OK(0, ""),
        TARGET_CONNECTION_FAILURE(1, "Connection to target JVM failed."),
        NO_SUCH_RECORDING(2, "No such recording was found."),
        OUT_OF_MEMORY(
                3, "The report generation process consumed too much memory and was terminated"),
        RECORDING_EXCEPTION(4, "An unspecified exception occurred while retrieving the recording."),
        IO_EXCEPTION(5, "An unspecified IO exception occurred while writing the report file."),
        OTHER(6, "An unspecified unexpected exception occurred."),
        TERMINATED(-1, "The subprocess timed out and was terminated."),
        ;

        final int code;
        final String message;

        ExitStatus(int code, String message) {
            this.code = code;
            this.message = message;
        }

        static ExitStatus byExitCode(int code) {
            for (ExitStatus e : ExitStatus.values()) {
                if (e.code == code) {
                    return e;
                }
            }
            return OTHER;
        }
    }

    public static class ReportGenerationException extends Exception {
        private final ExitStatus status;

        public ReportGenerationException(ExitStatus status) {
            super(String.format("[%d] %s", status.code, status.message));
            this.status = status;
        }

        public ExitStatus getStatus() {
            return status;
        }
    }
}
