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
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import javax.inject.Named;
import javax.inject.Provider;

import org.openjdk.jmc.flightrecorder.rules.IRule;
import org.openjdk.jmc.rjmx.ConnectionException;

import io.cryostat.configuration.Variables;
import io.cryostat.core.CryostatCore;
import io.cryostat.core.log.Logger;
import io.cryostat.core.reports.InterruptibleReportGenerator;
import io.cryostat.core.reports.InterruptibleReportGenerator.AnalysisResult;
import io.cryostat.core.sys.Environment;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.core.util.RuleFilterParser;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.recordings.RecordingNotFoundException;
import io.cryostat.util.JavaProcess;

import com.google.gson.Gson;
import org.apache.commons.lang3.tuple.Pair;

public class SubprocessReportGenerator extends AbstractReportGeneratorService {

    private final Environment env;
    private final Provider<JavaProcess.Builder> javaProcessBuilderProvider;
    private final long generationTimeoutSeconds;

    SubprocessReportGenerator(
            Environment env,
            FileSystem fs,
            TargetConnectionManager targetConnectionManager,
            Provider<JavaProcess.Builder> javaProcessBuilderProvider,
            @Named(ReportsModule.REPORT_GENERATION_TIMEOUT_SECONDS) long generationTimeoutSeconds,
            Logger logger) {
        super(targetConnectionManager, fs, logger);
        this.env = env;
        this.javaProcessBuilderProvider = javaProcessBuilderProvider;
        this.generationTimeoutSeconds = generationTimeoutSeconds;
    }

    @Override
    public synchronized CompletableFuture<Path> exec(Path recording, Path saveFile, String filter)
            throws NoSuchMethodException,
                    SecurityException,
                    IllegalAccessException,
                    IllegalArgumentException,
                    InvocationTargetException,
                    IOException,
                    InterruptedException,
                    ReportGenerationException {
        if (recording == null) {
            throw new IllegalArgumentException("Recording may not be null");
        }
        if (saveFile == null) {
            throw new IllegalArgumentException("Destination may not be null");
        }
        if (filter == null) {
            throw new IllegalArgumentException("Filter may not be null");
        }
        JavaProcess.Builder procBuilder =
                javaProcessBuilderProvider
                        .get()
                        .klazz(SubprocessReportGenerator.class)
                        .jvmArgs(
                                createJvmArgs(
                                        Integer.parseInt(
                                                env.getEnv(
                                                        Variables.SUBPROCESS_MAX_HEAP_ENV, "0"))))
                        .processArgs(createProcessArgs(recording, saveFile, filter));
        return CompletableFuture.supplyAsync(
                () -> {
                    Process proc = null;
                    try {
                        proc = procBuilder.exec();
                        proc.waitFor(generationTimeoutSeconds - 1, TimeUnit.SECONDS);

                        ExitStatus status =
                                proc.isAlive()
                                        ? ExitStatus.TIMED_OUT
                                        : ExitStatus.byExitCode(proc.exitValue());

                        switch (status) {
                            case OK:
                                return saveFile;
                            case NO_SUCH_RECORDING:
                                throw new RecordingNotFoundException(
                                        "archives", recording.toString());
                            default:
                                throw new SubprocessReportGenerationException(status);
                        }
                    } catch (InterruptedException e) {
                        logger.error(e);
                        throw new CompletionException(
                                new SubprocessReportGenerationException(ExitStatus.TERMINATED));
                    } catch (IOException
                            | ReportGenerationException
                            | RecordingNotFoundException
                            | IllegalThreadStateException e) {
                        logger.error(e);
                        throw new CompletionException(e);
                    } finally {
                        if (proc != null) {
                            proc.destroyForcibly();
                        }
                    }
                });
    }

    private List<String> createJvmArgs(int maxHeapMegabytes) throws IOException {
        List<String> args = new ArrayList<>();
        if (maxHeapMegabytes > 0) {
            args.add(String.format("-Xms%dM", maxHeapMegabytes));
            args.add(String.format("-Xmx%dM", maxHeapMegabytes));
        }
        args.add("-XX:+ExitOnOutOfMemoryError");
        // use Serial GC since we have a small heap and likely little garbage to clean,
        // and low GC overhead is more important here than minimizing pause time since the
        // result will end up cached for subsequent user accesses so long as the process
        // succeeds in the end
        args.add("-XX:+UseSerialGC");
        return args;
    }

    private List<String> createProcessArgs(Path recording, Path saveFile, String filter) {
        return List.of(
                recording.toAbsolutePath().toString(),
                saveFile.toAbsolutePath().toString(),
                filter);
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
        var gson = new Gson();

        try {
            CryostatCore.initialize();
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

        if (args.length != 3) {
            throw new IllegalArgumentException(Arrays.asList(args).toString());
        }
        var recording = Paths.get(args[0]);
        var saveFile = Paths.get(args[1]);
        String filter = args[2];

        try {
            Logger.INSTANCE.info(SubprocessReportGenerator.class.getName() + " processing report");
            Map<String, AnalysisResult> evalMapResult = generateEvalMapFromFile(recording, filter);
            fs.writeString(
                    saveFile,
                    gson.toJson(evalMapResult),
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

    static Map<String, AnalysisResult> generateEvalMapFromFile(Path recording, String filter)
            throws Exception {
        Pair<Predicate<IRule>, FileSystem> hPair = generateHelper(recording, filter);
        try (InputStream stream = hPair.getRight().newInputStream(recording)) {
            return new InterruptibleReportGenerator(ForkJoinPool.commonPool(), Logger.INSTANCE)
                    .generateEvalMapInterruptibly(stream, hPair.getLeft())
                    .get();
        } catch (IOException ioe) {
            ioe.printStackTrace();
            throw new SubprocessReportGenerationException(ExitStatus.IO_EXCEPTION);
        }
    }

    static Pair<Predicate<IRule>, FileSystem> generateHelper(Path recording, String filter)
            throws Exception {
        var fs = new FileSystem();
        if (!fs.isRegularFile(recording)) {
            throw new SubprocessReportGenerationException(ExitStatus.NO_SUCH_RECORDING);
        }
        RuleFilterParser rfp = new RuleFilterParser();
        return Pair.of(rfp.parse(filter), fs);
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
        TIMED_OUT(-2, "The subprocess did not complete its work within the allotted time.");

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

    public static class SubprocessReportGenerationException extends ReportGenerationException {
        private final ExitStatus status;

        public SubprocessReportGenerationException(ExitStatus status) {
            super(String.format("[%d] %s", status.code, status.message));
            this.status = status;
        }

        public ExitStatus getStatus() {
            return status;
        }
    }
}
