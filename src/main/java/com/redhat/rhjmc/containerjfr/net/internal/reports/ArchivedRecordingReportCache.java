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
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

import javax.inject.Named;

import org.apache.commons.io.input.ReaderInputStream;

import com.redhat.rhjmc.containerjfr.MainModule;
import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.reports.ReportGenerator;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.net.web.WebModule;

class ArchivedRecordingReportCache {

    protected final Path savedRecordingsPath;
    protected final Path archivedRecordingsReportPath;
    protected final FileSystem fs;
    protected final ReportGenerator reportGenerator;
    protected final ReentrantLock generationLock;
    protected final Logger logger;

    ArchivedRecordingReportCache(
            @Named(MainModule.RECORDINGS_PATH) Path savedRecordingsPath,
            @Named(WebModule.WEBSERVER_TEMP_DIR_PATH) Path webServerTempPath,
            FileSystem fs,
            ReportGenerator reportGenerator,
            @Named(ReportsModule.REPORT_GENERATION_LOCK) ReentrantLock generationLock,
            Logger logger) {
        this.savedRecordingsPath = savedRecordingsPath;
        this.archivedRecordingsReportPath = webServerTempPath;
        this.fs = fs;
        this.reportGenerator = reportGenerator;
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
                                try (InputStream stream = fs.newInputStream(recording)) {
                                    String report = reportGenerator.generateReport(stream);
                                    try (ReaderInputStream ris =
                                            new ReaderInputStream(
                                                    new StringReader(report),
                                                    StandardCharsets.UTF_8)) {
                                        // TODO use an abstraction over Files.write and avoid this
                                        // intermediate stream
                                        fs.copy(ris, dest, StandardCopyOption.REPLACE_EXISTING);
                                    }
                                    return Optional.of(dest);
                                } catch (IOException ioe) {
                                    logger.warn(ioe);
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
