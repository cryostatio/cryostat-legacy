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

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Future;

import javax.inject.Provider;

import org.openjdk.jmc.rjmx.ConnectionException;

import com.redhat.rhjmc.containerjfr.core.ContainerJfrCore;
import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.net.Credentials;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnectionToolkit;
import com.redhat.rhjmc.containerjfr.core.reports.ReportGenerator;
import com.redhat.rhjmc.containerjfr.core.reports.ReportTransformer;
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.net.ConnectionDescriptor;
import com.redhat.rhjmc.containerjfr.net.TargetConnectionManager;
import com.redhat.rhjmc.containerjfr.net.internal.reports.ActiveRecordingReportCache.RecordingDescriptor;
import com.redhat.rhjmc.containerjfr.net.internal.reports.ReportService.RecordingNotFoundException;
import com.redhat.rhjmc.containerjfr.util.JavaProcess;

class SubprocessReportGenerator {

    static String ENV_USERNAME = "TARGET_USERNAME";
    static String ENV_PASSWORD = "TARGET_PASSWORD";

    private final Environment env;
    private final FileSystem fs;
    private final Set<ReportTransformer> reportTransformers;
    private final Provider<JavaProcess.Builder> javaProcessBuilderProvider;
    private final Logger logger;

    SubprocessReportGenerator(
            Environment env,
            FileSystem fs,
            Set<ReportTransformer> reportTransformers,
            Provider<JavaProcess.Builder> javaProcessBuilderProvider,
            Logger logger) {
        this.env = env;
        this.fs = fs;
        this.reportTransformers = reportTransformers;
        this.javaProcessBuilderProvider = javaProcessBuilderProvider;
        this.logger = logger;
    }

    Future<Path> exec(RecordingDescriptor recordingDescriptor, Path destinationFile)
            throws NoSuchMethodException, SecurityException, IllegalAccessException,
                    IllegalArgumentException, InvocationTargetException, IOException,
                    InterruptedException, ReportGenerationException {
        if (recordingDescriptor == null) {
            throw new IllegalArgumentException("Recording may not be null");
        }
        if (destinationFile == null) {
            throw new IllegalArgumentException("Destination may not be null");
        }
        fs.writeString(
                destinationFile,
                serializeTransformersSet(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.DSYNC,
                StandardOpenOption.WRITE);
        Process proc =
                javaProcessBuilderProvider
                        .get()
                        .klazz(SubprocessReportGenerator.class)
                        .env(createEnv(recordingDescriptor.connectionDescriptor))
                        // FIXME the heap size should be determined by some heuristics, not
                        // hard-coded. See https://github.com/rh-jmc-team/container-jfr/issues/287
                        .jvmArgs(createJvmArgs(200))
                        .processArgs(createProcessArgs(recordingDescriptor, destinationFile))
                        .exec();
        return CompletableFuture.supplyAsync(
                () -> {
                    try {
                        ExitStatus status = ExitStatus.byExitCode(proc.waitFor());
                        switch (status) {
                            case OK:
                                return destinationFile;
                            case NO_SUCH_RECORDING:
                                throw new RecordingNotFoundException(
                                        recordingDescriptor.connectionDescriptor.getTargetId(),
                                        recordingDescriptor.recordingName);
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
                    }
                });
    }

    Future<Path> exec(RecordingDescriptor recordingDescriptor)
            throws NoSuchMethodException, SecurityException, IllegalAccessException,
                    IllegalArgumentException, InvocationTargetException, IOException,
                    InterruptedException, ReportGenerationException {
        // TODO add a FileSystem abstraction around Files.createTemp*
        return exec(recordingDescriptor, Files.createTempFile(null, null));
    }

    private Map<String, String> createEnv(ConnectionDescriptor connectionDescriptor)
            throws NoSuchMethodException, SecurityException, IllegalAccessException,
                    IllegalArgumentException, InvocationTargetException {
        if (connectionDescriptor.getCredentials().isEmpty()) {
            return Collections.emptyMap();
        }
        // FIXME don't use reflection for this
        Credentials c = connectionDescriptor.getCredentials().get();
        Method mtdUsername = c.getClass().getDeclaredMethod("getUsername");
        mtdUsername.trySetAccessible();
        Method mtdPassword = c.getClass().getDeclaredMethod("getPassword");
        mtdPassword.trySetAccessible();

        String username = (String) mtdUsername.invoke(c);
        String password = (String) mtdPassword.invoke(c);
        return Map.of(ENV_USERNAME, username, ENV_PASSWORD, password);
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
                "-XX:+AlwaysPreTouch",
                // reuse same truststore as parent process
                "-Djavax.net.ssl.trustStore=" + env.getEnv("SSL_TRUSTSTORE"),
                "-Djavax.net.ssl.trustStorePassword=" + env.getEnv("SSL_TRUSTSTORE_PASS"));
    }

    private List<String> createProcessArgs(RecordingDescriptor recordingDescriptor, Path saveFile) {
        return List.of(
                recordingDescriptor.connectionDescriptor.getTargetId(),
                recordingDescriptor.recordingName,
                saveFile.toAbsolutePath().toString());
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
        Logger.INSTANCE.info(SubprocessReportGenerator.class.getName() + " starting");
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    Logger.INSTANCE.info(
                                            SubprocessReportGenerator.class.getName()
                                                    + " shutting down...");
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
                                + "Adjusting subprocess OOM score");
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

        if (args.length != 3) {
            throw new IllegalArgumentException(Arrays.asList(args).toString());
        }
        var targetId = args[0];
        var recordingName = args[1];
        var saveFile = Paths.get(args[2]);

        var env = new Environment();
        String username = env.getEnv(ENV_USERNAME);
        String password = env.getEnv(ENV_PASSWORD);

        ConnectionDescriptor cd;
        if (username == null) {
            cd = new ConnectionDescriptor(targetId);
        } else {
            cd = new ConnectionDescriptor(targetId, new Credentials(username, password));
        }
        try {
            Logger.INSTANCE.info(SubprocessReportGenerator.class.getName() + " processing report");
            String report;
            URI uri = new URI(targetId);
            if ("file".equals(uri.getScheme())) {
                report = getReportFromArchivedRecording(Paths.get(uri.getPath()), saveFile);
            } else {
                report = getReportFromLiveTarget(recordingName, cd, saveFile);
            }
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

    static String getReportFromArchivedRecording(Path recording, Path saveFile) throws Exception {
        var fs = new FileSystem();
        if (!fs.isRegularFile(recording)) {
            throw new ReportGenerationException(ExitStatus.NO_SUCH_RECORDING);
        }
        try (InputStream stream = fs.newInputStream(recording)) {
            var transformers = deserializeTransformers(fs.readString(saveFile));
            return new ReportGenerator(Logger.INSTANCE, transformers).generateReport(stream);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            throw new ReportGenerationException(ExitStatus.IO_EXCEPTION);
        }
    }

    static String getReportFromLiveTarget(
            String recordingName, ConnectionDescriptor cd, Path saveFile) throws Exception {
        var fs = new FileSystem();
        var tk = new JFRConnectionToolkit(Logger.INSTANCE::info, fs, new Environment());
        return new TargetConnectionManager(Logger.INSTANCE, () -> tk)
                .executeConnectedTask(
                        cd,
                        conn -> {
                            var f = new CompletableFuture<String>();
                            conn.getService().getAvailableRecordings().stream()
                                    .filter(recording -> recordingName.equals(recording.getName()))
                                    .findFirst()
                                    .ifPresentOrElse(
                                            d -> {
                                                try (InputStream stream =
                                                        conn.getService().openStream(d, false)) {
                                                    var transformers =
                                                            deserializeTransformers(
                                                                    fs.readString(saveFile));
                                                    var generator =
                                                            new ReportGenerator(
                                                                    Logger.INSTANCE, transformers);
                                                    f.complete(generator.generateReport(stream));
                                                } catch (Exception e) {
                                                    e.printStackTrace();
                                                    f.completeExceptionally(e);
                                                }
                                            },
                                            () -> System.exit(ExitStatus.NO_SUCH_RECORDING.code));
                            return f.get();
                        });
    }

    enum ExitStatus {
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

    static class ReportGenerationException extends Exception {
        private final ExitStatus status;

        ReportGenerationException(ExitStatus status) {
            super(String.format("[%d] %s", status.code, status.message));
            this.status = status;
        }

        ExitStatus getStatus() {
            return status;
        }
    }
}
