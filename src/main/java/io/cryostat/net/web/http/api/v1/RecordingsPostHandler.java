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

import java.io.IOException;
import java.net.SocketException;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import io.cryostat.MainModule;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
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
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;

class RecordingsPostHandler extends AbstractAuthenticatedRequestHandler {

    static final String PATH = "recordings";

    private final FileSystem fs;
    private final Path savedRecordingsPath;
    private final Gson gson;
    private final NotificationFactory notificationFactory;
    private final Provider<WebServer> webServer;
    private final RecordingArchiveHelper recordingArchiveHelper;
    private final RecordingMetadataManager recordingMetadataManager;
    private final Logger logger;

    private static final String NOTIFICATION_CATEGORY = "ArchivedRecordingCreated";

    @Inject
    RecordingsPostHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            FileSystem fs,
            @Named(MainModule.RECORDINGS_PATH) Path savedRecordingsPath,
            Gson gson,
            NotificationFactory notificationFactory,
            Provider<WebServer> webServer,
            RecordingArchiveHelper recordingArchiveHelper,
            RecordingMetadataManager recordingMetadataManager,
            Logger logger) {
        super(auth, credentialsManager, logger);
        this.fs = fs;
        this.savedRecordingsPath = savedRecordingsPath;
        this.gson = gson;
        this.notificationFactory = notificationFactory;
        this.webServer = webServer;
        this.recordingArchiveHelper = recordingArchiveHelper;
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
            throw new HttpException(503, "Recording saving not available.");
        }

        FileUpload upload =
                webServer
                        .get()
                        .getTempFileUpload(
                                ctx.fileUploads(),
                                savedRecordingsPath.resolve(
                                        RecordingArchiveHelper.TEMP_UPLOADS_SUBDIRECTORY),
                                RecordingArchiveHelper.MULTIFORM_RECORDINGS_KEY);
        if (upload == null) {
            throw new HttpException(400, "No recording submission.");
        }

        String fileName = upload.fileName();
        if (fileName == null || fileName.isEmpty()) {
            recordingArchiveHelper.deleteTempFileUpload(upload);
            throw new HttpException(400, "Recording name must not be empty.");
        }

        if (fileName.endsWith(".jfr")) {
            fileName = fileName.substring(0, fileName.length() - 4);
        }

        Matcher m = RecordingArchiveHelper.RECORDING_FILENAME_PATTERN.matcher(fileName);
        if (!m.matches()) {
            recordingArchiveHelper.deleteTempFileUpload(upload);
            throw new HttpException(400, RecordingArchiveHelper.RECORDING_NAME_ERR_MSG);
        }

        MultiMap attrs = ctx.request().formAttributes();
        Map<String, String> labels = new HashMap<>();
        Boolean hasLabels = ((attrs.contains("labels") ? true : false));

        try {
            if (hasLabels) {
                labels = recordingMetadataManager.parseRecordingLabels(attrs.get("labels"));
            }
        } catch (IllegalArgumentException e) {
            recordingArchiveHelper.deleteTempFileUpload(upload);
            throw new HttpException(400, "Invalid metadata labels for the recording.");
        }
        Metadata metadata = new Metadata(labels);

        String targetName = m.group(1);
        String recordingName = m.group(2);
        String timestamp = m.group(3);

        long size = upload.size();
        long archivedTime = recordingArchiveHelper.getArchivedTimeFromTimestamp(timestamp);

        int count =
                m.group(4) == null || m.group(4).isEmpty()
                        ? 0
                        : Integer.parseInt(m.group(4).substring(1));

        final String subdirectoryName = RecordingArchiveHelper.UPLOADED_RECORDINGS_SUBDIRECTORY;
        final String basename = String.format("%s_%s_%s", targetName, recordingName, timestamp);
        final String uploadedFileName = upload.uploadedFileName();
        recordingArchiveHelper.validateRecording(
                upload.uploadedFileName(),
                (res) ->
                        recordingArchiveHelper.saveUploadedRecording(
                                subdirectoryName,
                                basename,
                                uploadedFileName,
                                RecordingArchiveHelper.UPLOADED_RECORDINGS_SUBDIRECTORY,
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
}
