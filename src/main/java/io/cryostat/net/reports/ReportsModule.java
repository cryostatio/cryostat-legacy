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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import io.cryostat.core.log.Logger;
import io.cryostat.core.reports.ReportTransformer;
import io.cryostat.core.sys.Environment;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.util.JavaProcess;

import dagger.Module;
import dagger.Provides;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;

@Module(
        includes = {
            ReportTransformerModule.class,
        })
public abstract class ReportsModule {

    static final String REPORT_GENERATION_LOCK = "REPORT_GENERATION_LOCK";

    @Provides
    @Singleton
    @Named(REPORT_GENERATION_LOCK)
    /** Used to ensure that only one report is generated at a time */
    static ReentrantLock provideReportGenerationLock() {
        return new ReentrantLock(true);
    }

    @Provides
    @Singleton
    static ActiveRecordingReportCache provideActiveRecordingReportCache(
            Provider<ReportGeneratorService> reportGeneratorServiceProvider,
            FileSystem fs,
            @Named(REPORT_GENERATION_LOCK) ReentrantLock generationLock,
            TargetConnectionManager targetConnectionManager,
            Logger logger) {
        return new ActiveRecordingReportCache(
                reportGeneratorServiceProvider,
                fs,
                generationLock,
                targetConnectionManager,
                logger);
    }

    @Provides
    @Singleton
    static ArchivedRecordingReportCache provideArchivedRecordingReportCache(
            FileSystem fs,
            Provider<ReportGeneratorService> reportGeneratorServiceProvider,
            @Named(REPORT_GENERATION_LOCK) ReentrantLock generationLock,
            Logger logger,
            RecordingArchiveHelper recordingArchiveHelper) {
        return new ArchivedRecordingReportCache(
                fs, reportGeneratorServiceProvider, generationLock, logger, recordingArchiveHelper);
    }

    @Provides
    static JavaProcess.Builder provideJavaProcessBuilder() {
        return new JavaProcess.Builder();
    }

    @Provides
    static ReportGeneratorService provideReportGeneratorService(
            Environment env,
            RemoteReportGenerator remoteGenerator,
            SubprocessReportGenerator subprocessGenerator) {
        if (env.getEnv("CRYOSTAT_REPORT_GENERATOR", "subprocess").equals("subprocess")) {
            return subprocessGenerator;
        }
        return remoteGenerator;
    }

    @Provides
    static RemoteReportGenerator provideRemoteReportGenerator(
            TargetConnectionManager targetConnectionManager,
            FileSystem fs,
            Vertx vertx,
            WebClient http,
            Environment env,
            Logger logger) {
        // TODO extract this so it's reusable and not duplicated
        Provider<Path> tempFileProvider =
                () -> {
                    try {
                        return Files.createTempFile(null, null);
                    } catch (IOException e) {
                        logger.error(e);
                        throw new RuntimeException(e);
                    }
                };
        return new RemoteReportGenerator(
                targetConnectionManager, fs, tempFileProvider, vertx, http, env, logger);
    }

    @Provides
    static SubprocessReportGenerator provideSubprocessReportGenerator(
            Environment env,
            FileSystem fs,
            TargetConnectionManager targetConnectionManager,
            Set<ReportTransformer> reportTransformers,
            Provider<JavaProcess.Builder> javaProcessBuilder,
            Logger logger) {
        Provider<Path> tempFileProvider =
                () -> {
                    try {
                        return Files.createTempFile(null, null);
                    } catch (IOException e) {
                        logger.error(e);
                        throw new RuntimeException(e);
                    }
                };
        return new SubprocessReportGenerator(
                env,
                fs,
                targetConnectionManager,
                reportTransformers,
                javaProcessBuilder,
                tempFileProvider,
                logger);
    }

    @Provides
    @Singleton
    static ReportService provideReportService(
            ActiveRecordingReportCache activeCache, ArchivedRecordingReportCache archivedCache) {
        return new ReportService(activeCache, archivedCache);
    }
}
