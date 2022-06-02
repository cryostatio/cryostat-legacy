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

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.core.log.Logger;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.recordings.RecordingNotFoundException;

abstract class AbstractReportGeneratorService implements ReportGeneratorService {

    static final int READ_BUFFER_SIZE = 64 * 1024; // 64 KB

    protected final TargetConnectionManager targetConnectionManager;
    protected final FileSystem fs;
    protected final Logger logger;

    protected AbstractReportGeneratorService(
            TargetConnectionManager targetConnectionManager, FileSystem fs, Logger logger) {
        this.targetConnectionManager = targetConnectionManager;
        this.fs = fs;
        this.logger = logger;
    }

    @Override
    public final CompletableFuture<Path> exec(RecordingDescriptor recordingDescriptor)
            throws Exception {
                System.out.println("IS IT HERE IN ABSTRACT?");
        Path recording =
                getRecordingFromLiveTarget(
                        recordingDescriptor.recordingName,
                        recordingDescriptor.connectionDescriptor);
        Path saveFile = fs.createTempFile(null, null);
        CompletableFuture<Path> cf = exec(recording, saveFile);
        return cf.whenComplete(
                (p, t) -> {
                    try {
                        fs.deleteIfExists(recording);
                    } catch (IOException e) {
                        logger.warn(e);
                    }
                });
    }

    Path getRecordingFromLiveTarget(String recordingName, ConnectionDescriptor cd)
            throws Exception {
        return this.targetConnectionManager.executeConnectedTask(
                cd,
                conn ->
                        copyRecordingToFile(
                                conn, cd, recordingName, fs.createTempFile(null, null)));
    }

    Path copyRecordingToFile(
            JFRConnection conn, ConnectionDescriptor cd, String recordingName, Path path)
            throws Exception {
        for (IRecordingDescriptor rec : conn.getService().getAvailableRecordings()) {
            if (!Objects.equals(rec.getName(), recordingName)) {
                continue;
            }
            try (OutputStream out = new BufferedOutputStream(new FileOutputStream(path.toFile()))) {
                try (conn;
                        InputStream in = conn.getService().openStream(rec, false)) {
                    byte[] buff = new byte[READ_BUFFER_SIZE];
                    int n = 0;
                    while ((n = in.read(buff)) != -1) {
                        out.write(buff, 0, n);
                        if (!targetConnectionManager.markConnectionInUse(cd)) {
                            throw new IOException(
                                    "Target connection unexpectedly closed while streaming recording");
                        }
                    }
                    out.flush();
                    return path;
                }
            }
        }
        throw new RecordingNotFoundException(cd.getTargetId(), recordingName);
    }
}
