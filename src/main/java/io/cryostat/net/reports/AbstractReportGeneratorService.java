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
    public final CompletableFuture<Path> exec(
            RecordingDescriptor recordingDescriptor, String filter) throws Exception {
        Path recording =
                getRecordingFromLiveTarget(
                        recordingDescriptor.recordingName,
                        recordingDescriptor.connectionDescriptor);
        Path saveFile = fs.createTempFile(null, null);

        CompletableFuture<Path> cf = exec(recording, saveFile, filter);
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
                                    "Target connection unexpectedly closed while streaming"
                                            + " recording");
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
