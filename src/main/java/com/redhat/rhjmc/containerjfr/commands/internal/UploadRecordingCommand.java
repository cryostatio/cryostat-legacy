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
package com.redhat.rhjmc.containerjfr.commands.internal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.tuple.Pair;

import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import com.redhat.rhjmc.containerjfr.MainModule;
import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.net.TargetConnectionManager;
import com.redhat.rhjmc.containerjfr.net.web.HttpMimeType;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.multipart.MultipartForm;

@Singleton
class UploadRecordingCommand extends AbstractConnectedCommand implements SerializableCommand {

    private final ClientWriter cw;
    private final FileSystem fs;
    private final Path recordingsPath;
    private final WebClient webClient;

    @Inject
    UploadRecordingCommand(
            ClientWriter cw,
            TargetConnectionManager targetConnectionManager,
            FileSystem fs,
            @Named(MainModule.RECORDINGS_PATH) Path recordingsPath,
            WebClient webClient) {
        super(targetConnectionManager);
        this.cw = cw;
        this.fs = fs;
        this.recordingsPath = recordingsPath;
        this.webClient = webClient;
    }

    @Override
    public String getName() {
        return "upload-recording";
    }

    @Override
    public void execute(String[] args) throws Exception {
        String targetId = args[0];
        String recordingName = args[1];
        String uploadUrl = args[2];
        ResponseMessage response = doPost(targetId, recordingName, uploadUrl);
        cw.println(
                String.format(
                        "[%d %s] %s", response.statusCode, response.statusMessage, response.body));
    }

    @Override
    public Output<?> serializableExecute(String[] args) {
        String targetId = args[0];
        String recordingName = args[1];
        String uploadUrl = args[2];
        try {
            ResponseMessage response = doPost(targetId, recordingName, uploadUrl);
            return new MapOutput<>(
                    Map.of(
                            "status",
                            String.format("%d %s", response.statusCode, response.statusMessage),
                            "body",
                            response.body));
        } catch (Exception e) {
            return new ExceptionOutput(e);
        }
    }

    private ResponseMessage doPost(String targetId, String recordingName, String uploadUrl)
            throws Exception {
        Pair<Path, Boolean> recordingPath =
                getBestRecordingForName(targetId, recordingName)
                        .orElseThrow(() -> new RecordingNotFoundException(targetId, recordingName));

        Path path = recordingPath.getLeft();
        if (path == null) {
            throw new IOException("Recording path could not be determined");
        }
        Path fileName = path.getFileName();
        path = path.toAbsolutePath();
        if (fileName == null || path == null) {
            throw new IOException("File name or path could not be determined");
        }

        MultipartForm form = MultipartForm.create();
        form.binaryFileUpload(
                "file", fileName.toString(), path.toString(), HttpMimeType.OCTET_STREAM.toString());

        CompletableFuture<ResponseMessage> future = new CompletableFuture<>();
        try {
            webClient
                    .postAbs(uploadUrl)
                    .sendMultipartForm(
                            form,
                            uploadHandler -> {
                                if (uploadHandler.failed()) {
                                    future.completeExceptionally(uploadHandler.cause());
                                    return;
                                }
                                HttpResponse<Buffer> response = uploadHandler.result();
                                future.complete(
                                        new ResponseMessage(
                                                response.statusCode(),
                                                response.statusMessage(),
                                                response.bodyAsString()));
                            });
            return future.get();
        } finally {
            if (recordingPath.getRight()) {
                fs.deleteIfExists(path);
            }
        }
    }

    @Override
    public boolean validate(String[] args) {
        if (args.length != 3) {
            cw.println(
                    "Expected three arguments: target (host:port, ip:port, or JMX service URL), recording name, and upload URL");
            return false;
        }

        String targetId = args[0];
        String recordingName = args[1];
        // String uploadUrl = args[2];

        boolean isValidTargetId = validateTargetId(targetId);
        if (!isValidTargetId) {
            cw.println(String.format("%s is an invalid connection specifier", args[0]));
        }

        boolean isValidRecordingName = validateRecordingName(recordingName);
        if (!isValidRecordingName) {
            cw.println(String.format("%s is an invalid recording name", recordingName));
        }

        // TODO validate upload URL

        return isValidTargetId && isValidRecordingName;
    }

    // returned stream should be cleaned up by HttpClient
    @SuppressFBWarnings("OBL_UNSATISFIED_OBLIGATION")
    Optional<Pair<Path, Boolean>> getBestRecordingForName(String targetId, String recordingName)
            throws Exception {
        Optional<IRecordingDescriptor> currentRecording =
                getDescriptorByName(targetId, recordingName);
        if (currentRecording.isPresent()) {
            // FIXME extract createTempFile wrapper into FileSystem
            Path tempFile = Files.createTempFile(null, null);
            return Optional.of(
                    targetConnectionManager.executeConnectedTask(
                            targetId,
                            connection -> {
                                InputStream stream =
                                        connection
                                                .getService()
                                                .openStream(currentRecording.get(), false);
                                try (stream) {
                                    fs.copy(stream, tempFile, StandardCopyOption.REPLACE_EXISTING);
                                }
                                return Pair.of(tempFile, true);
                            }));
        }

        Path archivedRecording = recordingsPath.resolve(recordingName);
        if (fs.isRegularFile(archivedRecording) && fs.isReadable(archivedRecording)) {
            return Optional.of(Pair.of(archivedRecording, false));
        }
        return Optional.empty();
    }

    private static class ResponseMessage {
        final int statusCode;
        final String statusMessage;
        final String body;

        ResponseMessage(int statusCode, String statusMessage, String body) {
            this.statusCode = statusCode;
            this.statusMessage = statusMessage;
            this.body = body;
        }
    }

    static class RecordingNotFoundException extends Exception {
        private static final long serialVersionUID = 1L;

        RecordingNotFoundException(String targetId, String recordingName) {
            super(
                    String.format(
                            "Recording \"%s\" could not be found at target \"%s\"",
                            recordingName, targetId));
        }
    }
}
