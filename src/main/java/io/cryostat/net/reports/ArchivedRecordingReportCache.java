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

    Future<Path> getFromPath(
            String subdirectoryName, String recordingName, String filter, boolean formatted) {
        CompletableFuture<Path> f = new CompletableFuture<>();
        Path dest = null;
        try {
            dest =
                    recordingArchiveHelper
                            .getCachedReportPathFromPath(
                                    subdirectoryName, recordingName, filter, formatted)
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
                            .exec(archivedRecording, dest, filter, formatted)
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

    Future<Path> get(String recordingName, String filter, boolean formatted) {
        return this.get(null, recordingName, filter, formatted);
    }

    Future<Path> get(String sourceTarget, String recordingName, String filter, boolean formatted) {
        CompletableFuture<Path> f = new CompletableFuture<>();
        Path dest = null;
        try {
            dest =
                    recordingArchiveHelper
                            .getCachedReportPath(sourceTarget, recordingName, filter, formatted)
                            .get();
            if (fs.isReadable(dest) && fs.isRegularFile(dest)) {
                f.complete(dest);
                logger.trace("Archived report cache miss for {}", recordingName);
                return f;
            }
            logger.info("Archived report cache miss for {}", recordingName);
            Path archivedRecording =
                    recordingArchiveHelper.getRecordingPath(sourceTarget, recordingName).get();
            Path saveFile =
                    reportGeneratorServiceProvider
                            .get()
                            .exec(archivedRecording, dest, filter, formatted)
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
