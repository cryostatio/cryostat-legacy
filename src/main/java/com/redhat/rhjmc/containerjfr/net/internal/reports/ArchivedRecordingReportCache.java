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
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.ReentrantLock;

import javax.inject.Named;
import javax.inject.Provider;

import com.redhat.rhjmc.containerjfr.MainModule;
import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.net.ConnectionDescriptor;
import com.redhat.rhjmc.containerjfr.net.internal.reports.ActiveRecordingReportCache.RecordingDescriptor;
import com.redhat.rhjmc.containerjfr.net.internal.reports.SubprocessReportGenerator.ExitStatus;
import com.redhat.rhjmc.containerjfr.net.internal.reports.SubprocessReportGenerator.ReportGenerationException;
import com.redhat.rhjmc.containerjfr.net.web.WebModule;
import com.redhat.rhjmc.containerjfr.net.web.http.generic.TimeoutHandler;

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

    Optional<Path> get(String recordingName) {
        Path dest = getCachedReportPath(recordingName);
        if (fs.isReadable(dest) && fs.isRegularFile(dest)) {
            return Optional.of(dest);
        }
        try {
            generationLock.lock();
            // check again in case the previous lock holder already created the cached file
            if (fs.isReadable(dest) && fs.isRegularFile(dest)) {
                return Optional.of(dest);
            }

            return fs.listDirectoryChildren(savedRecordingsPath).stream()
                    .filter(name -> name.equals(recordingName))
                    .map(savedRecordingsPath::resolve)
                    .findFirst()
                    .flatMap(
                            recording -> {
                                logger.trace(
                                        String.format(
                                                "Archived report cache miss for %s",
                                                recordingName));
                                ConnectionDescriptor cd =
                                        new ConnectionDescriptor(recording.toUri().toString());
                                RecordingDescriptor rd = new RecordingDescriptor(cd, "");
                                try {
                                    Path saveFile =
                                            subprocessReportGeneratorProvider
                                                    .get()
                                                    .exec(
                                                            rd,
                                                            dest,
                                                            Duration.ofMillis(
                                                                    TimeoutHandler.TIMEOUT_MS))
                                                    .get();
                                    return Optional.of(saveFile);
                                } catch (ExecutionException | CompletionException ee) {
                                    logger.error(ee);
                                    if (ee.getCause() instanceof ReportGenerationException) {
                                        ReportGenerationException generationException =
                                                (ReportGenerationException) ee.getCause();
                                        ExitStatus status = generationException.getStatus();
                                        try {
                                            fs.writeString(
                                                    dest,
                                                    String.format(
                                                            "Error %d: %s",
                                                            status.code, status.message));
                                        } catch (IOException e) {
                                            logger.warn(e);
                                        }
                                    }
                                    return Optional.of(dest);
                                } catch (Exception e) {
                                    logger.error(e);
                                    try {
                                        fs.deleteIfExists(dest);
                                    } catch (IOException ioe) {
                                        logger.warn(ioe);
                                    }
                                    return Optional.empty();
                                }
                            });
        } catch (IOException ioe) {
            logger.warn(ioe);
            return Optional.empty();
        } finally {
            generationLock.unlock();
        }
    }

    boolean delete(String recordingName) {
        try {
            logger.trace(String.format("Invalidating archived report cache for %s", recordingName));
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
