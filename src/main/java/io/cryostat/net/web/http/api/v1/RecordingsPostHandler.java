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
