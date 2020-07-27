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
package com.redhat.rhjmc.containerjfr.net.web.handlers;

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

import org.apache.commons.lang3.tuple.Pair;

import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import com.redhat.rhjmc.containerjfr.MainModule;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.net.AuthManager;
import com.redhat.rhjmc.containerjfr.net.TargetConnectionManager;
import com.redhat.rhjmc.containerjfr.net.internal.reports.ReportService.RecordingNotFoundException;
import com.redhat.rhjmc.containerjfr.net.web.HttpMimeType;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import io.vertx.ext.web.multipart.MultipartForm;

class TargetRecordingUploadPostHandler extends AbstractAuthenticatedRequestHandler {

    private final TargetConnectionManager targetConnectionManager;
    private final WebClient webClient;
    private final FileSystem fs;
    private final Path savedRecordingsPath;

    @Inject
    TargetRecordingUploadPostHandler(
            AuthManager auth,
            TargetConnectionManager targetConnectionManager,
            WebClient webClient,
            FileSystem fs,
            @Named(MainModule.RECORDINGS_PATH) Path savedRecordingsPath) {
        super(auth);
        this.targetConnectionManager = targetConnectionManager;
        this.webClient = webClient;
        this.fs = fs;
        this.savedRecordingsPath = savedRecordingsPath;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.POST;
    }

    @Override
    public String path() {
        return "/api/v1/targets/:targetId/recordings/:recordingName/upload";
    }

    @Override
    void handleAuthenticated(RoutingContext ctx) {
        String targetId = ctx.pathParam("targetId");
        String recordingName = ctx.pathParam("recordingName");
        String uploadUrl = "FIXME"; // rebase on top of Victor's patch to fill this in
        try {
            ResponseMessage response = doPost(targetId, recordingName, uploadUrl);
            ctx.response().setStatusCode(response.statusCode);
            ctx.response().setStatusMessage(response.statusMessage);
            ctx.response().end(response.body);
        } catch (HttpStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new HttpStatusException(500, e);
        }
    }

    private ResponseMessage doPost(String targetId, String recordingName, String uploadUrl)
            throws Exception {
        Pair<Path, Boolean> recordingPath =
                targetConnectionManager.executeConnectedTask(
                        targetId,
                        connection -> {
                            return getBestRecordingForName(connection, targetId, recordingName)
                                    .orElseThrow(
                                            () ->
                                                    new RecordingNotFoundException(
                                                            targetId, recordingName));
                        });

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

    // FIXME get rid of this. This handler should be split into one for active recordings and one
    // for archived, so this would be unnecessary
    Optional<Pair<Path, Boolean>> getBestRecordingForName(
            JFRConnection connection, String targetId, String recordingName) throws Exception {
        Optional<IRecordingDescriptor> currentRecording =
                connection.getService().getAvailableRecordings().stream()
                        .filter(recording -> recording.getName().equals(recordingName))
                        .findFirst();
        if (currentRecording.isPresent()) {
            // FIXME extract createTempFile wrapper into FileSystem
            Path tempFile = Files.createTempFile(null, null);
            InputStream stream = connection.getService().openStream(currentRecording.get(), false);
            try (stream) {
                fs.copy(stream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return Optional.of(Pair.of(tempFile, true));
        }

        Path archivedRecording = savedRecordingsPath.resolve(recordingName);
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
}
