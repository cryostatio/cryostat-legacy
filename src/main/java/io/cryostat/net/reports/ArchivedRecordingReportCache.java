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
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.inject.Named;
import javax.inject.Provider;

import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.recordings.RecordingArchiveHelper;

class ArchivedRecordingReportCache {

    protected final FileSystem fs;
    protected final Provider<ReportGeneratorService> reportGeneratorServiceProvider;
    protected final RecordingArchiveHelper recordingArchiveHelper;
    protected final long generationTimeoutSeconds;
    protected final Logger logger;

    ArchivedRecordingReportCache(
            FileSystem fs,
            Provider<ReportGeneratorService> reportGeneratorServiceProvider,
            RecordingArchiveHelper recordingArchiveHelper,
            @Named(ReportsModule.REPORT_GENERATION_TIMEOUT_SECONDS) long generationTimeoutSeconds,
            Logger logger) {
        this.fs = fs;
        this.reportGeneratorServiceProvider = reportGeneratorServiceProvider;
        this.recordingArchiveHelper = recordingArchiveHelper;
        this.generationTimeoutSeconds = generationTimeoutSeconds;
        this.logger = logger;
    }

    Future<Path> getFromPath(String subdirectoryName, String recordingName, String filter) {
        CompletableFuture<Path> f = new CompletableFuture<>();
        Path dest = null;
        try {
            dest =
                    recordingArchiveHelper
                            .getCachedReportPathFromPath(subdirectoryName, recordingName, filter)
                            .get();
            if (fs.isReadable(dest) && fs.isRegularFile(dest)) {
                f.complete(dest);
                logger.trace("Archived report cache hit for {}", recordingName);
                return f;
            }
            logger.trace("Archived report cache miss for {}", recordingName);
            Path archivedRecording =
                    recordingArchiveHelper
                            .getRecordingPathFromPath(subdirectoryName, recordingName)
                            .get();

            Path saveFile =
                    reportGeneratorServiceProvider
                            .get()
                            .exec(archivedRecording, dest, filter)
                            .get(generationTimeoutSeconds, TimeUnit.SECONDS);
            f.complete(saveFile);
        } catch (Exception e) {
            logger.error(e);
            f.completeExceptionally(e);
            try {
                fs.deleteIfExists(dest);
            } catch (IOException ioe) {
                logger.warn(ioe);
            }
        }
        return f;
    }

    Future<Path> get(String recordingName, String filter) {
        return this.get(null, recordingName, filter);
    }

    Future<Path> get(String sourceTarget, String recordingName, String filter) {
        CompletableFuture<Path> f = new CompletableFuture<>();
        Path dest = null;
        try {
            dest =
                    recordingArchiveHelper
                            .getCachedReportPath(sourceTarget, recordingName, filter)
                            .get();
            if (fs.isReadable(dest) && fs.isRegularFile(dest)) {
                f.complete(dest);
                logger.trace("Archived report cache hit for {}", recordingName);
                return f;
            }
            logger.trace("Archived report cache miss for {}", recordingName);
            Path archivedRecording =
                    recordingArchiveHelper.getRecordingPath(sourceTarget, recordingName).get();
            Path saveFile =
                    reportGeneratorServiceProvider
                            .get()
                            .exec(archivedRecording, dest, filter)
                            .get(generationTimeoutSeconds, TimeUnit.SECONDS);
            f.complete(saveFile);
        } catch (Exception e) {
            logger.error(e);
            f.completeExceptionally(e);
            try {
                fs.deleteIfExists(dest);
            } catch (IOException ioe) {
                logger.warn(ioe);
            }
        }
        return f;
    }
}
