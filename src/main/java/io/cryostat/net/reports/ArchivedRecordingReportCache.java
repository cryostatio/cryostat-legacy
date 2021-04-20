/*-
 * #%L
 * Cryostat
 * %%
 * Copyright (C) 2020 - 2021 The Cryostat Authors
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
package io.cryostat.net.reports;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;

import javax.inject.Named;
import javax.inject.Provider;

import io.cryostat.MainModule;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.net.reports.ReportService.RecordingNotFoundException;
import io.cryostat.net.web.WebModule;
import io.cryostat.net.web.http.generic.TimeoutHandler;

class ArchivedRecordingReportCache {

    protected final Path savedRecordingsPath;
    protected final Path archivedRecordingsReportPath;
    protected final FileSystem fs;
    protected final Provider<SubprocessReportGenerator> subprocessReportGeneratorProvider;
    protected final ReentrantLock generationLock;
    protected final Logger logger;

    ArchivedRecordingReportCache(
            @Named(MainModule.RECORDINGS_PATH) Path savedRecordingsPath,
            @Named(WebModule.WEBSERVER_TEMP_DIR_PATH) Path webServerTempPath,
            FileSystem fs,
            Provider<SubprocessReportGenerator> subprocessReportGeneratorProvider,
            @Named(ReportsModule.REPORT_GENERATION_LOCK) ReentrantLock generationLock,
            Logger logger) {
        this.savedRecordingsPath = savedRecordingsPath;
        this.archivedRecordingsReportPath = webServerTempPath;
        this.fs = fs;
        this.subprocessReportGeneratorProvider = subprocessReportGeneratorProvider;
        this.generationLock = generationLock;
        this.logger = logger;
    }

    Future<Path> get(String recordingName) {
        CompletableFuture<Path> f = new CompletableFuture<>();
        Path dest = getCachedReportPath(recordingName);
        if (fs.isReadable(dest) && fs.isRegularFile(dest)) {
            f.complete(dest);
            return f;
        }
        try {
            generationLock.lock();
            // check again in case the previous lock holder already created the cached file
            if (fs.isReadable(dest) && fs.isRegularFile(dest)) {
                f.complete(dest);
                return f;
            }

            fs.listDirectoryChildren(savedRecordingsPath).stream()
                    .filter(name -> name.equals(recordingName))
                    .map(savedRecordingsPath::resolve)
                    .findFirst()
                    .ifPresentOrElse(
                            recording -> {
                                logger.trace("Archived report cache miss for {}", recordingName);
                                try {
                                    Path saveFile =
                                            subprocessReportGeneratorProvider
                                                    .get()
                                                    .exec(
                                                            recording,
                                                            dest,
                                                            Duration.ofMillis(
                                                                    TimeoutHandler.TIMEOUT_MS))
                                                    .get();
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
                            },
                            () ->
                                    f.completeExceptionally(
                                            new RecordingNotFoundException(
                                                    "archives", recordingName)));
        } catch (IOException ioe) {
            logger.warn(ioe);
            f.completeExceptionally(ioe);
        } finally {
            generationLock.unlock();
        }
        return f;
    }

    boolean delete(String recordingName) {
        try {
            logger.trace("Invalidating archived report cache for {}", recordingName);
            return fs.deleteIfExists(getCachedReportPath(recordingName));
        } catch (IOException ioe) {
            logger.warn(ioe);
            return false;
        }
    }

    protected Path getCachedReportPath(String recordingName) {
        String fileName = recordingName + ".report.html";
        return archivedRecordingsReportPath.resolve(fileName).toAbsolutePath();
    }
}
