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
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import javax.inject.Named;
import javax.inject.Singleton;

import com.redhat.rhjmc.containerjfr.MainModule;
import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.reports.ReportGenerator;
import com.redhat.rhjmc.containerjfr.core.reports.ReportTransformer;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.net.TargetConnectionManager;
import com.redhat.rhjmc.containerjfr.net.web.WebModule;
import com.redhat.rhjmc.containerjfr.util.JavaProcess;

import dagger.Module;
import dagger.Provides;

@Module(
        includes = {
            ReportTransformerModule.class,
        })
public abstract class ReportsModule {

    static final String REPORT_GENERATION_LOCK = "REPORT_GENERATION_LOCK";

    @Provides
    @Singleton
    static ReportGenerator provideReportGenerator(
            Logger logger, Set<ReportTransformer> transformers) {
        return new ReportGenerator(logger, transformers);
    }

    @Provides
    @Singleton
    @Named(REPORT_GENERATION_LOCK)
    /** Used to ensure that only one report is generated at a time */
    static ReentrantLock provideReportGenerationLock() {
        return new ReentrantLock();
    }

    @Provides
    @Singleton
    static ActiveRecordingReportCache provideActiveRecordingReportCache(
            TargetConnectionManager targetConnectionManager,
            Set<ReportTransformer> reportTransformers,
            FileSystem fs,
            @Named(REPORT_GENERATION_LOCK) ReentrantLock generationLock,
            Logger logger) {
        return new ActiveRecordingReportCache(
                targetConnectionManager,
                () -> SubprocessReportGenerator.class,
                () -> new JavaProcess(logger),
                reportTransformers,
                fs,
                generationLock,
                logger);
    }

    @Provides
    @Singleton
    static ArchivedRecordingReportCache provideArchivedRecordingReportCache(
            @Named(MainModule.RECORDINGS_PATH) Path savedRecordingsPath,
            @Named(WebModule.WEBSERVER_TEMP_DIR_PATH) Path webServerTempDir,
            FileSystem fs,
            ReportGenerator reportGenerator,
            @Named(REPORT_GENERATION_LOCK) ReentrantLock generationLock,
            Logger logger) {
        return new ArchivedRecordingReportCache(
                savedRecordingsPath, webServerTempDir, fs, reportGenerator, generationLock, logger);
    }

    @Provides
    @Singleton
    static ReportService provideReportService(
            ActiveRecordingReportCache activeCache, ArchivedRecordingReportCache archivedCache) {
        return new ReportService(activeCache, archivedCache);
    }
}
