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

public class SubprocessReportGenerator {

    static String ENV_USERNAME = "TARGET_USERNAME";
    static String ENV_PASSWORD = "TARGET_PASSWORD";

    enum ExitStatus {
        OK(0, ""),
        TARGET_CONNECTION_FAILURE(1, "Connection to target JVM failed."),
        NO_SUCH_RECORDING(2, "No such recording was found."),
        RECORDING_EXCEPTION(3, "An unspecified exception occurred while retrieving the recording."),
        IO_EXCEPTION(4, "An unspecified IO exception occurred while writing the report file."),
        OTHER(5, "An unspecified unexpected exception occurred."),
        TERMINATED(-1, "The subprocess timed out and was terminated."),
        ;

        final int code;
        final String message;

        ExitStatus(int code, String message) {
            this.code = code;
            this.message = message;
        }
    }

    public static Map<String, String> createEnv(ConnectionDescriptor connectionDescriptor)
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

    public static List<String> createJvmArgs(int maxHeapMegabytes) throws IOException {
        // These JVM flags must be kept in-sync with the flags set on the parent process in
        // entrypoint.sh in order to keep the auth and certs setup consistent
        var fs = new FileSystem();
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
                "-Djavax.net.ssl.trustStore=/tmp/truststore.p12",
                "-Djavax.net.ssl.trustStorePassword="
                        + fs.readString(fs.pathOf("/tmp/truststore.pass")));
    }

    public static List<String> createProcessArgs(
            RecordingDescriptor recordingDescriptor, Path saveFile) {
        return List.of(
                recordingDescriptor.connectionDescriptor.getTargetId(),
                recordingDescriptor.recordingName,
                saveFile.toAbsolutePath().toString());
    }

    public static String serializeTransformersSet(Set<ReportTransformer> set) {
        var sb = new StringBuilder();
        for (var rt : set) {
            sb.append(rt.getClass().getCanonicalName());
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    public static Set<ReportTransformer> deserializeTransformers(String serial)
            throws InstantiationException, IllegalAccessException, IllegalArgumentException,
                    InvocationTargetException, NoSuchMethodException, SecurityException,
                    ClassNotFoundException {
        var st = new StringTokenizer(serial);
        var res = new HashSet<ReportTransformer>();
        while (st.hasMoreTokens()) {
            res.add(
                    (ReportTransformer)
                            Class.forName(st.nextToken()).getDeclaredConstructor().newInstance());
        }
        return res;
    }

    public static void main(String[] args) {
        Runtime.getRuntime()
                .addShutdownHook(
                        new Thread(
                                () -> {
                                    Logger.INSTANCE.info(
                                            SubprocessReportGenerator.class.getName()
                                                    + " shutting down...");
                                }));

        try {
            ContainerJfrCore.initialize();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(ExitStatus.OTHER.code);
        }

        Logger.INSTANCE.info(SubprocessReportGenerator.class.getName() + " starting");

        if (args.length != 3) {
            throw new IllegalArgumentException(Arrays.asList(args).toString());
        }
        var targetId = args[0];
        var recordingName = args[1];
        var saveFile = Paths.get(args[2]);

        var env = new Environment();
        String username = env.getEnv(ENV_USERNAME);
        String password = env.getEnv(ENV_PASSWORD);

        var fs = new FileSystem();
        var tk =
                new JFRConnectionToolkit(
                        Logger.INSTANCE::info, new FileSystem(), new Environment());
        ConnectionDescriptor cd;
        if (username == null) {
            cd = new ConnectionDescriptor(targetId);
        } else {
            cd = new ConnectionDescriptor(targetId, new Credentials(username, password));
        }
        try {
            var report =
                    new TargetConnectionManager(Logger.INSTANCE, () -> tk)
                            .executeConnectedTask(
                                    cd,
                                    conn -> {
                                        var f = new CompletableFuture<String>();
                                        conn.getService().getAvailableRecordings().stream()
                                                .filter(
                                                        recording ->
                                                                recordingName.equals(
                                                                        recording.getName()))
                                                .findFirst()
                                                .ifPresentOrElse(
                                                        d -> {
                                                            try {
                                                                var transformers =
                                                                        deserializeTransformers(
                                                                                fs.readString(
                                                                                        saveFile));
                                                                var generator =
                                                                        new ReportGenerator(
                                                                                Logger.INSTANCE,
                                                                                transformers);
                                                                InputStream stream =
                                                                        conn.getService()
                                                                                .openStream(
                                                                                        d, false);
                                                                f.complete(
                                                                        generator.generateReport(
                                                                                stream));
                                                            } catch (Exception e) {
                                                                e.printStackTrace();
                                                                f.completeExceptionally(e);
                                                            }
                                                        },
                                                        () ->
                                                                System.exit(
                                                                        ExitStatus.NO_SUCH_RECORDING
                                                                                .code));
                                        return f.get();
                                    });

            Files.writeString(
                    saveFile,
                    report,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.DSYNC,
                    StandardOpenOption.WRITE);
            System.exit(ExitStatus.OK.code);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(ExitStatus.TARGET_CONNECTION_FAILURE.code);
        }
    }

    public static class ReportGenerationException extends Exception {
        ReportGenerationException(ExitStatus status) {
            this(status.code, status.message);
        }

        ReportGenerationException(int code, String message) {
            super(String.format("[%d] %s", code, message));
        }
    }
}
