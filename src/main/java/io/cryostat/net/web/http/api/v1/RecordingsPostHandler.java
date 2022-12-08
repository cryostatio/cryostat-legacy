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
package io.cryostat.net.web.http.api.v1;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.SocketException;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import org.openjdk.jmc.flightrecorder.CouldNotLoadRecordingException;
import org.openjdk.jmc.flightrecorder.internal.FlightRecordingLoader;
import org.openjdk.jmc.flightrecorder.internal.InvalidJfrFileException;

import io.cryostat.MainModule;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.HttpServer;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingMetadataManager;
import io.cryostat.recordings.RecordingMetadataManager.Metadata;
import io.cryostat.rules.ArchivedRecordingInfo;

import com.google.gson.Gson;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;

class RecordingsPostHandler extends AbstractAuthenticatedRequestHandler {

    private static final Pattern RECORDING_FILENAME_PATTERN =
            Pattern.compile("([A-Za-z\\d-]*)_([A-Za-z\\d-_]*)_([\\d]*T[\\d]*Z)(\\.[\\d]+)?");
    private static final Pattern DATE_TIME_PATTERN =
            Pattern.compile(
                    "^([0-9]{4})(1[0-2]|0[1-9])(3[01]|0[1-9]|[12][0-9])T([0-2][0-9])([0-5][0-9])([0-5][0-9])Z$");

    static final String PATH = "recordings";

    private final Vertx vertx;
    private final FileSystem fs;
    private final Path savedRecordingsPath;
    private final Gson gson;
    private final NotificationFactory notificationFactory;
    private final Provider<WebServer> webServer;
    private final RecordingMetadataManager recordingMetadataManager;
    private final Logger logger;

    private static final String NOTIFICATION_CATEGORY = "ArchivedRecordingCreated";

    @Inject
    RecordingsPostHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            HttpServer httpServer,
            FileSystem fs,
            @Named(MainModule.RECORDINGS_PATH) Path savedRecordingsPath,
            Gson gson,
            NotificationFactory notificationFactory,
            Provider<WebServer> webServer,
            RecordingMetadataManager recordingMetadataManager,
            Logger logger) {
        super(auth, credentialsManager, logger);
        this.vertx = httpServer.getVertx();
        this.fs = fs;
        this.savedRecordingsPath = savedRecordingsPath;
        this.gson = gson;
        this.notificationFactory = notificationFactory;
        this.webServer = webServer;
        this.recordingMetadataManager = recordingMetadataManager;
        this.logger = logger;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.V1;
    }

    @Override
    public int getPriority() {
        return DEFAULT_PRIORITY + 10;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.POST;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(ResourceAction.CREATE_RECORDING);
    }

    @Override
    public String path() {
        return basePath() + PATH;
    }

    @Override
    public boolean isAsync() {
        return true;
    }

    @Override
    public boolean isOrdered() {
        return true;
    }

    @Override
    public List<HttpMimeType> produces() {
        return List.of(HttpMimeType.JSON);
    }

    @Override
    public List<HttpMimeType> consumes() {
        return List.of(HttpMimeType.MULTIPART_FORM);
    }

    @Override
    public void handleAuthenticated(RoutingContext ctx) throws Exception {

        if (!fs.isDirectory(savedRecordingsPath)) {
            throw new HttpException(503, "Recording saving not available");
        }

        FileUpload upload = null;
        for (FileUpload fu : ctx.fileUploads()) {
            // ignore unrecognized form fields
            if ("recording".equals(fu.name())) {
                upload = fu;
            } else {
                deleteTempFileUpload(fu);
            }
        }

        if (upload == null) {
            throw new HttpException(400, "No recording submission");
        }

        String fileName = upload.fileName();
        if (fileName == null || fileName.isEmpty()) {
            deleteTempFileUpload(upload);
            throw new HttpException(400, "Recording name must not be empty");
        }

        if (fileName.endsWith(".jfr")) {
            fileName = fileName.substring(0, fileName.length() - 4);
        }

        Matcher m = RECORDING_FILENAME_PATTERN.matcher(fileName);
        if (!m.matches()) {
            deleteTempFileUpload(upload);
            throw new HttpException(400, "Incorrect recording file name pattern");
        }

        MultiMap attrs = ctx.request().formAttributes();
        Map<String, String> labels = new HashMap<>();
        Boolean hasLabels = ((attrs.contains("labels") ? true : false));

        try {
            if (hasLabels) {
                labels = recordingMetadataManager.parseRecordingLabels(attrs.get("labels"));
            }
        } catch (IllegalArgumentException e) {
            deleteTempFileUpload(upload);
            throw new HttpException(400, "Invalid labels");
        }
        Metadata metadata = new Metadata(labels);

        String targetName = m.group(1);
        String recordingName = m.group(2);
        String timestamp = m.group(3);

        Matcher dtm = DATE_TIME_PATTERN.matcher(timestamp);

        long size = upload.size();
        long archivedTime;

        if (!dtm.matches()) {
            logger.trace("Invalid timestamp: {}", timestamp);
            archivedTime = Instant.now().toEpochMilli();
        } else {
            String isoString =
                    String.format(
                            "%s-%s-%sT%s:%s:%s.00Z",
                            dtm.group(1),
                            dtm.group(2),
                            dtm.group(3),
                            dtm.group(4),
                            dtm.group(5),
                            dtm.group(6));
            archivedTime = Instant.parse(isoString).toEpochMilli();
        }
        int count =
                m.group(4) == null || m.group(4).isEmpty()
                        ? 0
                        : Integer.parseInt(m.group(4).substring(1));

        final String subdirectoryName = RecordingArchiveHelper.UPLOADED_RECORDINGS_SUBDIRECTORY;
        final String basename = String.format("%s_%s_%s", targetName, recordingName, timestamp);
        final String uploadedFileName = upload.uploadedFileName();
        validateRecording(
                upload.uploadedFileName(),
                (res) ->
                        saveRecording(
                                subdirectoryName,
                                basename,
                                uploadedFileName,
                                count,
                                (res2) -> {
                                    if (res2.failed()) {
                                        ctx.fail(res2.cause());
                                        return;
                                    }

                                    String fsName = res2.result();
                                    try {
                                        if (hasLabels) {
                                            recordingMetadataManager
                                                    .setRecordingMetadata(fsName, metadata)
                                                    .get();
                                        }

                                    } catch (InterruptedException
                                            | ExecutionException
                                            | IOException e) {
                                        logger.error(e);
                                        ctx.fail(new HttpException(500, e));
                                        return;
                                    }

                                    try {

                                        notificationFactory
                                                .createBuilder()
                                                .metaCategory(NOTIFICATION_CATEGORY)
                                                .metaType(HttpMimeType.JSON)
                                                .message(
                                                        Map.of(
                                                                "recording",
                                                                new ArchivedRecordingInfo(
                                                                        subdirectoryName,
                                                                        fsName,
                                                                        webServer
                                                                                .get()
                                                                                .getArchivedDownloadURL(
                                                                                        subdirectoryName,
                                                                                        fsName),
                                                                        webServer
                                                                                .get()
                                                                                .getArchivedReportURL(
                                                                                        subdirectoryName,
                                                                                        fsName),
                                                                        metadata,
                                                                        size,
                                                                        archivedTime),
                                                                "target",
                                                                subdirectoryName))
                                                .build()
                                                .send();
                                    } catch (URISyntaxException
                                            | UnknownHostException
                                            | SocketException e) {
                                        logger.error(e);
                                        ctx.fail(new HttpException(500, e));
                                        return;
                                    }

                                    ctx.response()
                                            .putHeader(
                                                    HttpHeaders.CONTENT_TYPE,
                                                    HttpMimeType.JSON.mime())
                                            .end(
                                                    gson.toJson(
                                                            Map.of(
                                                                    "name",
                                                                    fsName,
                                                                    "metadata",
                                                                    metadata)));
                                }));
    }

    private void validateRecording(String recordingFile, Handler<AsyncResult<Void>> handler) {
        vertx.executeBlocking(
                event -> {
                    try {
                        // try loading chunk info to see if it's a valid file
                        try (var is = new BufferedInputStream(new FileInputStream(recordingFile))) {
                            var supplier = FlightRecordingLoader.createChunkSupplier(is);
                            var chunks = FlightRecordingLoader.readChunkInfo(supplier);
                            if (chunks.size() < 1) {
                                throw new InvalidJfrFileException();
                            }
                        }
                        event.complete();
                    } catch (CouldNotLoadRecordingException | IOException e) {
                        event.fail(e);
                    }
                },
                res -> {
                    if (res.failed()) {
                        Throwable t;
                        if (res.cause() instanceof CouldNotLoadRecordingException) {
                            t =
                                    new HttpException(
                                            400, "Not a valid JFR recording file", res.cause());
                        } else {
                            t = res.cause();
                        }
                        vertx.fileSystem().deleteBlocking(recordingFile);

                        handler.handle(makeFailedAsyncResult(t));
                        return;
                    }

                    handler.handle(makeAsyncResult(null));
                });
    }

    // FIXME refactor into RecordingArchiveHelper
    private void saveRecording(
            String subdirectoryName,
            String basename,
            String tmpFile,
            int counter,
            Handler<AsyncResult<String>> handler) {
        // TODO byte-sized rename limit is arbitrary. Probably plenty since recordings
        // are also differentiated by second-resolution timestamp
        if (counter >= Byte.MAX_VALUE) {
            handler.handle(
                    makeFailedAsyncResult(
                            new IOException(
                                    "Recording could not be saved. File already exists and rename"
                                            + " attempts were exhausted.")));
            return;
        }

        String filename = counter > 1 ? basename + "." + counter + ".jfr" : basename + ".jfr";
        Path specificRecordingsPath = savedRecordingsPath.resolve(subdirectoryName);

        if (!fs.exists(specificRecordingsPath)) {
            try {
                Files.createDirectory(specificRecordingsPath);
            } catch (IOException e) {
                handler.handle(makeFailedAsyncResult(e));
                return;
            }
        }

        vertx.fileSystem()
                .exists(
                        specificRecordingsPath.resolve(filename).toString(),
                        (res) -> {
                            if (res.failed()) {
                                handler.handle(makeFailedAsyncResult(res.cause()));
                                return;
                            }

                            if (res.result()) {
                                saveRecording(
                                        subdirectoryName, basename, tmpFile, counter + 1, handler);
                                return;
                            }

                            // verified no name clash at this time
                            vertx.fileSystem()
                                    .move(
                                            tmpFile,
                                            specificRecordingsPath.resolve(filename).toString(),
                                            (res2) -> {
                                                if (res2.failed()) {
                                                    handler.handle(
                                                            makeFailedAsyncResult(res2.cause()));
                                                    return;
                                                }

                                                handler.handle(makeAsyncResult(filename));
                                            });
                        });
    }

    private void deleteTempFileUpload(FileUpload upload) {
        Path p = savedRecordingsPath.resolve("file-uploads").resolve(upload.uploadedFileName());
        vertx.fileSystem().deleteBlocking(p.toString());
    }

    private <T> AsyncResult<T> makeAsyncResult(T result) {
        return new AsyncResult<>() {
            @Override
            public T result() {
                return result;
            }

            @Override
            public Throwable cause() {
                return null;
            }

            @Override
            public boolean succeeded() {
                return true;
            }

            @Override
            public boolean failed() {
                return false;
            }
        };
    }

    private <T> AsyncResult<T> makeFailedAsyncResult(Throwable cause) {
        return new AsyncResult<>() {
            @Override
            public T result() {
                return null;
            }

            @Override
            public Throwable cause() {
                return cause;
            }

            @Override
            public boolean succeeded() {
                return false;
            }

            @Override
            public boolean failed() {
                return true;
            }
        };
    }
}
