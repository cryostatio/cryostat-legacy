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

package io.cryostat.net.web.http.api.beta;

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
import io.cryostat.configuration.Variables;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.net.web.http.api.v2.ApiException;
import io.cryostat.recordings.JvmIdHelper;
import io.cryostat.recordings.JvmIdHelper.JvmIdDoesNotExistException;
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

public class RecordingsFromIdPostHandler extends AbstractAuthenticatedRequestHandler {

    static final String PATH = "recordings/:jvmId";

    private final Gson gson;
    private final Logger logger;

    private final FileSystem fs;
    private final JvmIdHelper idHelper;
    private final NotificationFactory notificationFactory;
    private final RecordingArchiveHelper recordingArchiveHelper;
    private final RecordingMetadataManager recordingMetadataManager;
    private final Path savedRecordingsPath;
    private final int globalMaxFiles;
    private final Provider<WebServer> webServer;

    private static final String NOTIFICATION_CATEGORY = "ArchivedRecordingCreated";

    @Inject
    RecordingsFromIdPostHandler(
            AuthManager auth,
            CredentialsManager credentialsManager,
            Gson gson,
            FileSystem fs,
            JvmIdHelper idHelper,
            NotificationFactory notificationFactory,
            RecordingArchiveHelper recordingArchiveHelper,
            RecordingMetadataManager recordingMetadataManager,
            @Named(MainModule.RECORDINGS_PATH) Path savedRecordingsPath,
            @Named(Variables.PUSH_MAX_FILES_ENV) int globalMaxFiles,
            Provider<WebServer> webServer,
            Logger logger) {
        super(auth, credentialsManager, logger);
        this.fs = fs;
        this.idHelper = idHelper;
        this.notificationFactory = notificationFactory;
        this.recordingArchiveHelper = recordingArchiveHelper;
        this.recordingMetadataManager = recordingMetadataManager;
        this.savedRecordingsPath = savedRecordingsPath;
        this.globalMaxFiles = globalMaxFiles;
        this.webServer = webServer;
        this.gson = gson;
        this.logger = logger;
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.BETA;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.POST;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return EnumSet.of(
                ResourceAction.CREATE_RECORDING,
                ResourceAction.READ_RECORDING,
                ResourceAction.DELETE_RECORDING,
                ResourceAction.DELETE_REPORT);
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
            throw new ApiException(503, "Recording saving not available");
        }

        String maxFilesParam = ctx.request().getParam("maxFiles", String.valueOf(globalMaxFiles));
        int maxFiles;
        try {
            maxFiles = Integer.parseInt(maxFilesParam);
            if (maxFiles <= 0) {
                throw new ApiException(400, "maxFiles must be a positive integer");
            }
        } catch (NumberFormatException e) {
            throw new ApiException(400, "maxFiles must be a positive integer");
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
            throw new ApiException(400, "No recording submission");
        }

        String jvmId = ctx.pathParam("jvmId");
        final String connectUrl;
        try {
            connectUrl =
                    idHelper.reverseLookup(jvmId)
                            .orElseThrow(() -> new JvmIdDoesNotExistException(jvmId))
                            .getServiceUri()
                            .toString();
            if (connectUrl == null) {
                throw new JvmIdDoesNotExistException(jvmId);
            }
        } catch (JvmIdDoesNotExistException e) {
            recordingArchiveHelper.deleteTempFileUpload(upload);
            throw new ApiException(400, String.format("jvmId must be valid: %s", e.getMessage()));
        }
        String subdirectoryName = idHelper.jvmIdToSubdirectoryName(jvmId);

        String fileName = upload.fileName();
        if (fileName == null || fileName.isEmpty()) {
            recordingArchiveHelper.deleteTempFileUpload(upload);
            throw new ApiException(400, "Recording name must not be empty");
        }

        if (fileName.endsWith(".jfr")) {
            fileName = fileName.substring(0, fileName.length() - 4);
        }

        Matcher m = RecordingArchiveHelper.RECORDING_FILENAME_PATTERN.matcher(fileName);
        if (!m.matches()) {
            recordingArchiveHelper.deleteTempFileUpload(upload);
            throw new ApiException(400, "Incorrect recording file name pattern");
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
            throw new ApiException(400, "Invalid labels");
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

        final String basename = String.format("%s_%s_%s", targetName, recordingName, timestamp);
        final String uploadedFileName = upload.uploadedFileName();
        recordingArchiveHelper.validateRecording(
                upload.uploadedFileName(),
                (res) ->
                        recordingArchiveHelper.saveUploadedRecording(
                                subdirectoryName,
                                basename,
                                uploadedFileName,
                                connectUrl,
                                count,
                                (res2) -> {
                                    if (res2.failed()) {
                                        throw new ApiException(500, res2.cause());
                                    }

                                    String fsName = res2.result();

                                    try {
                                        recordingArchiveHelper.pruneTargetUploads(
                                                subdirectoryName, maxFiles);
                                        if (hasLabels) {
                                            recordingMetadataManager
                                                    .setRecordingMetadataFromPath(
                                                            subdirectoryName, fsName, metadata)
                                                    .get();
                                        }
                                    } catch (InterruptedException
                                            | ExecutionException
                                            | IOException e) {
                                        logger.error(e);
                                        throw new ApiException(500, e);
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
                                                                        connectUrl,
                                                                        fsName,
                                                                        webServer
                                                                                .get()
                                                                                .getArchivedDownloadURL(
                                                                                        connectUrl,
                                                                                        fsName),
                                                                        webServer
                                                                                .get()
                                                                                .getArchivedReportURL(
                                                                                        connectUrl,
                                                                                        fsName),
                                                                        metadata,
                                                                        size,
                                                                        archivedTime),
                                                                "target",
                                                                connectUrl))
                                                .build()
                                                .send();
                                    } catch (URISyntaxException
                                            | UnknownHostException
                                            | SocketException e) {
                                        logger.error(e);
                                        throw new ApiException(500, e);
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
