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
package io.cryostat.recordings;

import java.io.BufferedInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.inject.Named;
import javax.inject.Provider;

import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.MainModule;
import io.cryostat.core.FlightRecorderException;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.core.sys.Clock;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.web.WebModule;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.platform.PlatformClient;
import io.cryostat.recordings.JvmIdHelper.JvmIdGetException;
import io.cryostat.recordings.RecordingMetadataManager.Metadata;
import io.cryostat.rules.ArchivePathException;
import io.cryostat.rules.ArchivedRecordingInfo;
import io.cryostat.util.URIUtil;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.codec.binary.Base32;
import org.apache.commons.io.FileUtils;

public class RecordingArchiveHelper {

    private final TargetConnectionManager targetConnectionManager;
    private final FileSystem fs;
    private final Provider<WebServer> webServerProvider;
    private final Logger logger;
    private final Path archivedRecordingsPath;
    private final Path archivedRecordingsReportPath;
    private final RecordingMetadataManager recordingMetadataManager;
    private final Clock clock;
    private final PlatformClient platformClient;
    private final NotificationFactory notificationFactory;
    private final JvmIdHelper jvmIdHelper;
    private final Base32 base32;

    private static final String SAVE_NOTIFICATION_CATEGORY = "ActiveRecordingSaved";
    private static final String DELETE_NOTIFICATION_CATEGORY = "ArchivedRecordingDeleted";
    private static final long FS_TIMEOUT_SECONDS = 1;

    // FIXME: remove ARCHIVES after 2.2.0 release since we either use "uploads" or sourceTarget
    public static final String ARCHIVES = "archives";
    public static final String UPLOADED_RECORDINGS_SUBDIRECTORY = "uploads";
    public static final String DEFAULT_CACHED_REPORT_SUBDIRECTORY = "default";
    private static final String CONNECT_URL = "connectUrl";

    RecordingArchiveHelper(
            FileSystem fs,
            Provider<WebServer> webServerProvider,
            Logger logger,
            @Named(MainModule.RECORDINGS_PATH) Path archivedRecordingsPath,
            @Named(WebModule.WEBSERVER_TEMP_DIR_PATH) Path webServerTempPath,
            TargetConnectionManager targetConnectionManager,
            RecordingMetadataManager recordingMetadataManager,
            Clock clock,
            PlatformClient platformClient,
            NotificationFactory notificationFactory,
            JvmIdHelper jvmIdHelper,
            Base32 base32) {
        this.fs = fs;
        this.webServerProvider = webServerProvider;
        this.logger = logger;
        this.archivedRecordingsPath = archivedRecordingsPath;
        this.archivedRecordingsReportPath = webServerTempPath;
        this.targetConnectionManager = targetConnectionManager;
        this.recordingMetadataManager = recordingMetadataManager;
        this.clock = clock;
        this.platformClient = platformClient;
        this.notificationFactory = notificationFactory;
        this.jvmIdHelper = jvmIdHelper;
        this.base32 = base32;
    }

    // on startup migration and jvmId transfer method for archived recordings
    protected void migrate(ExecutorService executor) throws Exception {
        List<String> subdirectories = fs.listDirectoryChildren(archivedRecordingsPath);
        List<Future<Void>> futures = new ArrayList<>(subdirectories.size());
        for (String subdirectoryName : subdirectories) {
            Future<Void> future =
                    executor.submit(
                            () -> {
                                try {
                                    logger.info(
                                            "Found archived recordings subdirectory: {}",
                                            subdirectoryName);
                                    // FIXME: refactor structure to remove file-uploads (v1
                                    // RecordingsPostBodyHandler)
                                    if (subdirectoryName.equals("file-uploads")
                                            || subdirectoryName.equals("uploads")) {
                                        logger.info("Skipping: appears to be an upload location");
                                        return null;
                                    }
                                    Path subdirectoryPath =
                                            archivedRecordingsPath.resolve(subdirectoryName);
                                    String connectUrl;
                                    try {
                                        connectUrl = getConnectUrlFromPath(subdirectoryPath).get();
                                        logger.info("Found connectUrl: {}", connectUrl);
                                    } catch (InterruptedException | ExecutionException e) {
                                        // try to migrate the recording to the new structure
                                        connectUrl =
                                                new String(
                                                        base32.decode(subdirectoryName),
                                                        StandardCharsets.UTF_8);
                                    }
                                    String jvmId = jvmIdHelper.getJvmId(connectUrl);
                                    Path encodedJvmIdPath = getRecordingSubdirectoryPath(jvmId);
                                    if (Objects.equals(subdirectoryPath, encodedJvmIdPath)) {
                                        logger.info(
                                                "Skipping {} - no change in ID", subdirectoryPath);
                                        return null;
                                    }
                                    logger.info(
                                            "Migrating recordings from {} to {}",
                                            subdirectoryPath,
                                            encodedJvmIdPath);
                                    if (!fs.exists(encodedJvmIdPath)) {
                                        fs.createDirectory(encodedJvmIdPath);
                                        fs.writeString(
                                                encodedJvmIdPath.resolve("connectUrl"),
                                                connectUrl,
                                                StandardOpenOption.CREATE);
                                    }
                                    fs.deleteIfExists(subdirectoryPath.resolve("connectUrl"));
                                    for (String file : fs.listDirectoryChildren(subdirectoryPath)) {
                                        Path oldLocation = subdirectoryPath.resolve(file);
                                        Path newLocation = encodedJvmIdPath.resolve(file);
                                        logger.info("{} -> {}", oldLocation, newLocation);
                                        Files.move(oldLocation, newLocation);
                                    }
                                    FileUtils.deleteQuietly(subdirectoryPath.toFile());

                                } catch (JvmIdGetException e) {
                                    logger.warn(
                                            "Could not find jvmId for targetId"
                                                    + " {}, skipping migration of"
                                                    + " recordings",
                                            e.getTarget());
                                } catch (IOException e) {
                                    logger.warn(e);
                                } catch (CancellationException e) {
                                    logger.error(e);
                                }
                                return null;
                            });
            futures.add(future);
        }
        for (var f : futures) {
            f.get();
        }
    }

    protected void transferArchivesIfRestarted(String targetId) {
        try {
            Path subdirectoryPath = null;
            for (String encodedJvmId : fs.listDirectoryChildren(archivedRecordingsPath)) {
                Path subdir = archivedRecordingsPath.resolve(encodedJvmId);
                Path connectUrl = subdir.resolve("connectUrl");
                if (fs.exists(connectUrl)) {
                    String u = fs.readString(connectUrl);
                    if (Objects.equals(targetId, u)) {
                        subdirectoryPath = subdir;
                        break;
                    }
                }
            }
            if (subdirectoryPath == null) {
                return;
            }
            String connectUrl = getConnectUrlFromPath(subdirectoryPath).get();
            String newJvmId = jvmIdHelper.getJvmId(connectUrl);
            Path jvmIdPath = getRecordingSubdirectoryPath(newJvmId);

            if (Objects.equals(subdirectoryPath, jvmIdPath)) {
                return;
            }

            logger.info(
                    "{} Archives subdirectory transfer: {} -> {}",
                    connectUrl,
                    subdirectoryPath,
                    newJvmId);

            fs.createDirectory(jvmIdPath);
            for (String file : fs.listDirectoryChildren(subdirectoryPath)) {
                Path oldLocation = subdirectoryPath.resolve(file);
                Path newLocation = jvmIdPath.resolve(file);
                logger.info("{} -> {}", oldLocation, newLocation);
                Files.move(oldLocation, newLocation);
            }
            fs.deleteIfExists(subdirectoryPath);

            logger.info(
                    "{} Archives subdirectory successfully transferred: {} -> {}",
                    connectUrl,
                    subdirectoryPath,
                    newJvmId);
        } catch (Exception e) {
            logger.error("Archives subdirectory could not be renamed upon target restart", e);
        }
    }

    @SuppressFBWarnings(
            value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE",
            justification =
                    "SpotBugs false positive. The following checks ensures that the"
                            + " getFileName() of the Path are not null, barring some exceptional"
                            + " circumstance like some external filesystem access race.")
    protected Future<String> getConnectUrlFromPath(Path subdirectory) {
        CompletableFuture<String> future =
                new CompletableFuture<String>().orTimeout(FS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (subdirectory == null || subdirectory.getFileName() == null) {
            future.completeExceptionally(new FileNotFoundException("No subdirectory name"));
        } else if (subdirectory.getFileName().toString().equals(UPLOADED_RECORDINGS_SUBDIRECTORY)
                || subdirectory.getFileName().toString().equals("file-uploads")) {
            future.complete(UPLOADED_RECORDINGS_SUBDIRECTORY);
        } else {
            Optional<String> connectUrl = Optional.empty();
            try {
                for (String file : fs.listDirectoryChildren(subdirectory)) {
                    // use metadata file to determine connectUrl to probe for jvmId
                    if (file.equals("connectUrl")) {
                        connectUrl =
                                Optional.of(fs.readFile(subdirectory.resolve(file)).readLine());
                    }
                }
                future.complete(connectUrl.orElseThrow(IOException::new));
            } catch (IOException e) {
                logger.warn("Couldn't get connectUrl from file system");
                future.completeExceptionally(e);
            }
        }
        return future;
    }

    Path getRecordingSubdirectoryPath(String jvmId) {
        String subdirectory =
                jvmId.equals(UPLOADED_RECORDINGS_SUBDIRECTORY)
                        ? UPLOADED_RECORDINGS_SUBDIRECTORY
                        : base32.encodeAsString(jvmId.getBytes(StandardCharsets.UTF_8));
        return archivedRecordingsPath.resolve(subdirectory);
    }

    @SuppressFBWarnings(
            value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE",
            justification =
                    "SpotBugs false positive. validateSavePath() ensures that the getParent() and"
                            + " getFileName() of the Path are not null, barring some exceptional"
                            + " circumstance like some external filesystem access race.")
    public Future<ArchivedRecordingInfo> saveRecording(
            ConnectionDescriptor connectionDescriptor, String recordingName) {

        CompletableFuture<ArchivedRecordingInfo> future = new CompletableFuture<>();

        try {
            Path savePath =
                    targetConnectionManager.executeConnectedTask(
                            connectionDescriptor,
                            connection -> {
                                Optional<IRecordingDescriptor> descriptor =
                                        this.getDescriptorByName(connection, recordingName);

                                if (descriptor.isPresent()) {
                                    return writeRecordingToDestination(
                                            connection, descriptor.get());
                                } else {
                                    throw new RecordingNotFoundException(
                                            "active recordings", recordingName);
                                }
                            });
            validateSavePath(recordingName, savePath);
            Path filenamePath = savePath.getFileName();
            String filename = filenamePath.toString();
            Metadata metadata =
                    recordingMetadataManager
                            .copyMetadataToArchives(connectionDescriptor, recordingName, filename)
                            .get();
            ArchivedRecordingInfo archivedRecordingInfo =
                    new ArchivedRecordingInfo(
                            connectionDescriptor.getTargetId(),
                            filename,
                            webServerProvider
                                    .get()
                                    .getArchivedDownloadURL(
                                            connectionDescriptor.getTargetId(), filename),
                            webServerProvider
                                    .get()
                                    .getArchivedReportURL(
                                            connectionDescriptor.getTargetId(), filename),
                            metadata,
                            getFileSize(filename));
            future.complete(archivedRecordingInfo);
            notificationFactory
                    .createBuilder()
                    .metaCategory(SAVE_NOTIFICATION_CATEGORY)
                    .metaType(HttpMimeType.JSON)
                    .message(
                            Map.of(
                                    "recording",
                                    archivedRecordingInfo,
                                    "target",
                                    connectionDescriptor.getTargetId()))
                    .build()
                    .send();
        } catch (Exception e) {
            future.completeExceptionally(e);
        }

        return future;
    }

    @SuppressFBWarnings(
            value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE",
            justification =
                    "SpotBugs false positive. validateSavePath() ensures that the getParent() and"
                            + " getFileName() of the Path are not null, barring some exceptional"
                            + " circumstance like some external filesystem access race.")
    public Future<ArchivedRecordingInfo> deleteRecordingFromPath(
            String subdirectoryName, String recordingName) {
        CompletableFuture<ArchivedRecordingInfo> future = new CompletableFuture<>();
        try {
            String jvmId = new String(base32.decode(subdirectoryName), StandardCharsets.UTF_8);
            Path subdirectoryPath = archivedRecordingsPath.resolve(subdirectoryName);
            Path recordingPath = subdirectoryPath.resolve(recordingName);
            validateSavePath(recordingName, recordingPath);
            Path filenamePath = recordingPath.getFileName();
            String filename = filenamePath.toString();
            String targetId = getConnectUrlFromPath(subdirectoryPath).get();
            ArchivedRecordingInfo archivedRecordingInfo =
                    new ArchivedRecordingInfo(
                            targetId,
                            recordingName,
                            webServerProvider.get().getArchivedDownloadURL(targetId, filename),
                            webServerProvider.get().getArchivedReportURL(targetId, filename),
                            recordingMetadataManager.deleteRecordingMetadataIfExists(
                                    jvmId, recordingName),
                            getFileSize(filename));
            notificationFactory
                    .createBuilder()
                    .metaCategory(DELETE_NOTIFICATION_CATEGORY)
                    .metaType(HttpMimeType.JSON)
                    .message(Map.of("recording", archivedRecordingInfo, "target", targetId))
                    .build()
                    .send();
            fs.deleteIfExists(recordingPath);
            checkEmptySubdirectory(subdirectoryPath);
            future.complete(archivedRecordingInfo);
        } catch (IOException | URISyntaxException | InterruptedException | ExecutionException e) {
            future.completeExceptionally(e);
        } finally {
            deleteReportFromPath(subdirectoryName, recordingName);
        }
        return future;
    }

    public Future<ArchivedRecordingInfo> deleteRecording(
            String sourceTarget, String recordingName) {
        CompletableFuture<ArchivedRecordingInfo> future = new CompletableFuture<>();

        try {
            Path archivedRecording = getRecordingPath(sourceTarget, recordingName).get();
            future = handleDeleteRecordingRequest(sourceTarget, recordingName, archivedRecording);
        } catch (InterruptedException | ExecutionException e) {
            future.completeExceptionally(e);
        }

        return future;
    }

    public Future<ArchivedRecordingInfo> deleteRecording(String recordingName) {
        return deleteRecording(null, recordingName);
    }

    @SuppressFBWarnings(
            value = "NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE",
            justification =
                    "SpotBugs false positive. validateSavePath() ensures that the getParent() and"
                            + " getFileName() of the Path are not null, barring some exceptional"
                            + " circumstance like some external filesystem access race.")
    private CompletableFuture<ArchivedRecordingInfo> handleDeleteRecordingRequest(
            String sourceTarget, String recordingName, Path archivedRecording) {
        CompletableFuture<ArchivedRecordingInfo> future = new CompletableFuture<>();

        try {
            fs.deleteIfExists(archivedRecording);
            validateSavePath(recordingName, archivedRecording);
            Path parentPath = archivedRecording.getParent();
            Path filenamePath = archivedRecording.getFileName();
            String filename = filenamePath.toString();
            String targetId =
                    sourceTarget == null ? UPLOADED_RECORDINGS_SUBDIRECTORY : sourceTarget;
            ArchivedRecordingInfo archivedRecordingInfo =
                    new ArchivedRecordingInfo(
                            targetId,
                            filename,
                            webServerProvider.get().getArchivedDownloadURL(targetId, filename),
                            webServerProvider.get().getArchivedReportURL(targetId, filename),
                            recordingMetadataManager.deleteRecordingMetadataIfExists(
                                    new ConnectionDescriptor(targetId), recordingName),
                            getFileSize(filename));
            notificationFactory
                    .createBuilder()
                    .metaCategory(DELETE_NOTIFICATION_CATEGORY)
                    .metaType(HttpMimeType.JSON)
                    .message(Map.of("recording", archivedRecordingInfo, "target", targetId))
                    .build()
                    .send();
            checkEmptySubdirectory(parentPath);
            future.complete(archivedRecordingInfo);
        } catch (IOException | URISyntaxException e) {
            future.completeExceptionally(e);
        } finally {
            deleteReport(sourceTarget, recordingName);
        }

        return future;
    }

    private void checkEmptySubdirectory(Path parentPath) throws IOException {
        if (fs.listDirectoryChildren(parentPath).size() == 1
                && fs.listDirectoryChildren(parentPath).contains(CONNECT_URL)) {
            fs.deleteIfExists(parentPath.resolve(CONNECT_URL));
            fs.deleteIfExists(parentPath);
        }
    }

    private void validateSavePath(String recordingName, Path path) throws IOException {
        if (path.getParent() == null) {
            throw new IOException(
                    String.format(
                            "Filesystem parent for %s could not be determined", recordingName));
        }
        if (path.getFileName() == null) {
            throw new IOException(
                    String.format("Filesystem path for %s could not be determined", recordingName));
        }
    }

    public boolean deleteReportFromPath(String subdirectoryName, String recordingName) {
        try {
            logger.trace("Invalidating archived report cache for {}", recordingName);
            return fs.deleteIfExists(
                    getCachedReportPathFromPath(subdirectoryName, recordingName).get());
        } catch (IOException | InterruptedException | ExecutionException e) {
            logger.warn(e);
            return false;
        }
    }

    public boolean deleteReport(String sourceTarget, String recordingName) {
        try {
            logger.trace("Invalidating archived report cache for {}", recordingName);
            return fs.deleteIfExists(getCachedReportPath(sourceTarget, recordingName).get());
        } catch (IOException | InterruptedException | ExecutionException e) {
            logger.warn(e);
            return false;
        }
    }

    public CompletableFuture<Path> getCachedReportPathFromPath(
            String subdirectoryName, String recordingName) {
        CompletableFuture<Path> future = new CompletableFuture<>();
        try {
            Path tempSubdirectory = archivedRecordingsReportPath.resolve(subdirectoryName);
            if (!fs.exists(tempSubdirectory)) {
                tempSubdirectory = fs.createDirectory(tempSubdirectory);
            }
            future.complete(tempSubdirectory.resolve(recordingName).toAbsolutePath());
        } catch (IOException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    public Future<Path> getCachedReportPath(String sourceTarget, String recordingName) {
        CompletableFuture<Path> future = new CompletableFuture<>();
        try {
            String jvmId = jvmIdHelper.getJvmId(sourceTarget);
            String subdirectory =
                    sourceTarget == null
                            ? DEFAULT_CACHED_REPORT_SUBDIRECTORY
                            : sourceTarget.equals(UPLOADED_RECORDINGS_SUBDIRECTORY)
                                    ? UPLOADED_RECORDINGS_SUBDIRECTORY
                                    : base32.encodeAsString(jvmId.getBytes(StandardCharsets.UTF_8));
            String fileName = recordingName + ".report.html";
            Path tempSubdirectory = archivedRecordingsReportPath.resolve(subdirectory);
            if (!fs.exists(tempSubdirectory)) {
                tempSubdirectory = fs.createDirectory(tempSubdirectory);
            }
            future.complete(tempSubdirectory.resolve(fileName).toAbsolutePath());
        } catch (IOException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    public Future<List<ArchivedRecordingInfo>> getRecordings(String targetId) {
        CompletableFuture<List<ArchivedRecordingInfo>> future = new CompletableFuture<>();

        try {
            String jvmId = jvmIdHelper.getJvmId(targetId);
            Path specificRecordingsPath = getRecordingSubdirectoryPath(jvmId);
            if (!fs.exists(archivedRecordingsPath)) {
                throw new ArchivePathException(archivedRecordingsPath.toString(), "does not exist");
            }
            if (!fs.isReadable(archivedRecordingsPath)) {
                throw new ArchivePathException(
                        archivedRecordingsPath.toString(), "is not readable");
            }
            if (!fs.isDirectory(archivedRecordingsPath)) {
                throw new ArchivePathException(
                        archivedRecordingsPath.toString(), "is not a directory");
            }

            if (!fs.exists(specificRecordingsPath)) {
                future.complete(List.of());
                return future;
            }
            if (!fs.isReadable(specificRecordingsPath)) {
                throw new ArchivePathException(
                        specificRecordingsPath.toString(), "is not readable");
            }
            if (!fs.isDirectory(specificRecordingsPath)) {
                throw new ArchivePathException(
                        specificRecordingsPath.toString(), "is not a directory");
            }
            WebServer webServer = webServerProvider.get();
            List<ArchivedRecordingInfo> archivedRecordings = new ArrayList<>();
            this.fs.listDirectoryChildren(specificRecordingsPath).stream()
                    .filter(filename -> !filename.equals(CONNECT_URL))
                    .map(
                            file -> {
                                try {
                                    return new ArchivedRecordingInfo(
                                            targetId,
                                            file,
                                            webServer.getArchivedDownloadURL(targetId, file),
                                            webServer.getArchivedReportURL(targetId, file),
                                            recordingMetadataManager.getMetadata(
                                                    new ConnectionDescriptor(targetId), file),
                                            getFileSize(file));
                                } catch (IOException | URISyntaxException e) {
                                    logger.warn(e);
                                    return null;
                                }
                            })
                    .filter(Objects::nonNull)
                    .forEach(archivedRecordings::add);
            future.complete(archivedRecordings);
        } catch (ArchivePathException | IOException e) {
            future.completeExceptionally(e);
        }

        return future;
    }

    public Future<List<ArchiveDirectory>> getRecordingsAndDirectories() {
        CompletableFuture<List<ArchiveDirectory>> future = new CompletableFuture<>();
        try {
            if (!fs.exists(archivedRecordingsPath)) {
                throw new ArchivePathException(archivedRecordingsPath.toString(), "does not exist");
            }
            if (!fs.isReadable(archivedRecordingsPath)) {
                throw new ArchivePathException(
                        archivedRecordingsPath.toString(), "is not readable");
            }
            if (!fs.isDirectory(archivedRecordingsPath)) {
                throw new ArchivePathException(
                        archivedRecordingsPath.toString(), "is not a directory");
            }
            WebServer webServer = webServerProvider.get();
            List<ArchiveDirectory> directories = new ArrayList<>();
            List<String> subdirectories = this.fs.listDirectoryChildren(archivedRecordingsPath);
            for (String subdirectoryName : subdirectories) {
                if (subdirectoryName.equals("file-uploads")) {
                    continue;
                }
                Path subdirectory = archivedRecordingsPath.resolve(subdirectoryName);
                String targetId = getConnectUrlFromPath(subdirectory).get();
                String jvmId = new String(base32.decode(subdirectoryName), StandardCharsets.UTF_8);
                List<String> files = this.fs.listDirectoryChildren(subdirectory);
                List<ArchivedRecordingInfo> temp =
                        files.stream()
                                .filter(filename -> !filename.equals(CONNECT_URL))
                                .map(
                                        file -> {
                                            try {
                                                // FIXME: string replacing
                                                return new ArchivedRecordingInfo(
                                                        targetId,
                                                        file,
                                                        webServer
                                                                .getArchivedDownloadURL(
                                                                        subdirectoryName, file)
                                                                .replace(
                                                                        "beta/recordings",
                                                                        "beta/fs/recordings"),
                                                        webServer
                                                                .getArchivedReportURL(
                                                                        subdirectoryName, file)
                                                                .replace(
                                                                        "beta/reports",
                                                                        "beta/fs/reports"),
                                                        recordingMetadataManager
                                                                .getMetadataFromPathIfExists(
                                                                        jvmId, file),
                                                        getFileSize(file));
                                            } catch (IOException | URISyntaxException e) {
                                                logger.warn(e);
                                                return null;
                                            }
                                        })
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList());
                directories.add(new ArchiveDirectory(targetId, subdirectoryName, temp));
            }
            future.complete(directories);
        } catch (ArchivePathException | IOException | InterruptedException | ExecutionException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    public Future<List<ArchivedRecordingInfo>> getRecordings() {
        CompletableFuture<List<ArchivedRecordingInfo>> future = new CompletableFuture<>();

        try {
            if (!fs.exists(archivedRecordingsPath)) {
                throw new ArchivePathException(archivedRecordingsPath.toString(), "does not exist");
            }
            if (!fs.isReadable(archivedRecordingsPath)) {
                throw new ArchivePathException(
                        archivedRecordingsPath.toString(), "is not readable");
            }
            if (!fs.isDirectory(archivedRecordingsPath)) {
                throw new ArchivePathException(
                        archivedRecordingsPath.toString(), "is not a directory");
            }
            WebServer webServer = webServerProvider.get();
            List<String> subdirectories = this.fs.listDirectoryChildren(archivedRecordingsPath);
            List<ArchivedRecordingInfo> archivedRecordings = new ArrayList<>();
            for (String subdirectoryName : subdirectories) {
                Path subdirectory = archivedRecordingsPath.resolve(subdirectoryName);
                String targetId = getConnectUrlFromPath(subdirectory).get();
                List<String> files = this.fs.listDirectoryChildren(subdirectory);
                List<ArchivedRecordingInfo> temp =
                        files.stream()
                                .filter(filename -> !filename.equals(CONNECT_URL))
                                .map(
                                        file -> {
                                            try {
                                                return new ArchivedRecordingInfo(
                                                        targetId,
                                                        file,
                                                        webServer.getArchivedDownloadURL(
                                                                targetId, file),
                                                        webServer.getArchivedReportURL(
                                                                targetId, file),
                                                        recordingMetadataManager.getMetadata(
                                                                new ConnectionDescriptor(targetId),
                                                                file),
                                                        getFileSize(file));
                                            } catch (IOException | URISyntaxException e) {
                                                logger.warn(e);
                                                return null;
                                            }
                                        })
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList());
                archivedRecordings.addAll(temp);
            }
            future.complete(archivedRecordings);
        } catch (ArchivePathException | IOException | InterruptedException | ExecutionException e) {
            future.completeExceptionally(e);
        }

        return future;
    }

    public Future<Path> getRecordingPathFromPath(String subdirectoryName, String recordingName) {
        try {
            Path path = archivedRecordingsPath.resolve(subdirectoryName).resolve(recordingName);
            validateRecordingPath(Optional.of(path), recordingName, false);
            return CompletableFuture.completedFuture(path);
        } catch (RecordingNotFoundException | ArchivePathException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public Future<Path> getRecordingPath(String recordingName) {
        CompletableFuture<Path> future = new CompletableFuture<>();
        try {
            List<String> subdirectories = this.fs.listDirectoryChildren(archivedRecordingsPath);
            Optional<Path> optional =
                    searchSubdirectories(subdirectories, archivedRecordingsPath, recordingName);
            validateRecordingPath(optional, recordingName, true);
            future.complete(optional.get());
        } catch (RecordingNotFoundException | IOException | ArchivePathException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    public Future<Path> getRecordingPath(String sourceTarget, String recordingName) {
        if (sourceTarget == null || sourceTarget.equals(UPLOADED_RECORDINGS_SUBDIRECTORY)) {
            return getRecordingPath(recordingName);
        }
        CompletableFuture<Path> future = new CompletableFuture<>();
        try {
            String jvmId = jvmIdHelper.getJvmId(sourceTarget);
            Path subdirectory = getRecordingSubdirectoryPath(jvmId);
            if (!fs.exists(archivedRecordingsPath.resolve(subdirectory))) {
                fs.createDirectory(archivedRecordingsPath.resolve(subdirectory));
                fs.writeString(
                        archivedRecordingsPath.resolve(subdirectory.resolve("connectUrl")),
                        sourceTarget,
                        StandardOpenOption.CREATE);
            }
            Path archivedRecording = searchSubdirectory(subdirectory, recordingName);
            if (archivedRecording == null) {
                throw new RecordingNotFoundException(sourceTarget, recordingName);
            }
            validateRecordingPath(Optional.of(archivedRecording), recordingName, false);
            future.complete(archivedRecording);
        } catch (RecordingNotFoundException | ArchivePathException | IOException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    private Path searchSubdirectory(Path subdirectory, String recordingName) {
        Path recordingPath = null;
        try {
            for (String file : this.fs.listDirectoryChildren(subdirectory)) {
                if (file.equals(CONNECT_URL)) continue;
                if (recordingName.equals(file)) {
                    recordingPath = subdirectory.resolve(file).normalize().toAbsolutePath();
                    break;
                }
            }
        } catch (IOException ioe) {
            logger.error(ioe);
        }
        return recordingPath;
    }

    private Optional<Path> searchSubdirectories(
            List<String> subdirectories, Path parent, String recordingName) {
        // TODO refactor this into nicer streaming
        return subdirectories.stream()
                .map(parent::resolve)
                .map(
                        subdirectory -> {
                            return searchSubdirectory(subdirectory, recordingName);
                        })
                .filter(Objects::nonNull)
                .findFirst();
    }

    public void validateSourceTarget(String sourceTarget)
            throws RecordingSourceTargetNotFoundException {
        if (sourceTarget.equals(UPLOADED_RECORDINGS_SUBDIRECTORY)) {
            return;
        }
        // assume sourceTarget is percent encoded
        String decodedTargetId = URLDecoder.decode(sourceTarget, StandardCharsets.UTF_8);
        boolean exists =
                this.platformClient.listDiscoverableServices().stream()
                        .anyMatch(
                                target ->
                                        target.getServiceUri().toString().equals(decodedTargetId));
        if (!exists) {
            throw new RecordingSourceTargetNotFoundException(decodedTargetId);
        }
    }

    private void validateRecordingPath(
            Optional<Path> optional, String recordingName, boolean isUploads)
            throws RecordingNotFoundException, ArchivePathException {
        if (optional.isEmpty()) {
            throw new RecordingNotFoundException(ARCHIVES, recordingName);
        }
        Path archivedRecording = optional.get();
        if (!fs.exists(archivedRecording)) {
            throw new ArchivePathException(archivedRecording.toString(), "does not exist");
        }
        if (!fs.isRegularFile(archivedRecording)) {
            throw new ArchivePathException(archivedRecording.toString(), "is not a regular file");
        }
        if (!fs.isReadable(archivedRecording)) {
            throw new ArchivePathException(archivedRecording.toString(), "is not readable");
        }
        if (!isUploads && !fs.exists(archivedRecording.resolveSibling(CONNECT_URL))) {
            throw new ArchivePathException(
                    archivedRecording.resolveSibling(CONNECT_URL).toString(), "does not exist");
        }
    }

    Path writeRecordingToDestination(JFRConnection connection, IRecordingDescriptor descriptor)
            throws IOException, URISyntaxException, FlightRecorderException, Exception {
        URI serviceUri = URIUtil.convert(connection.getJMXURL());
        String jvmId = jvmIdHelper.getJvmId(serviceUri.toString());
        Path specificRecordingsPath = getRecordingSubdirectoryPath(jvmId);
        if (!fs.exists(specificRecordingsPath)) {
            fs.createDirectory(specificRecordingsPath);
            fs.writeString(
                    specificRecordingsPath.resolve("connectUrl"),
                    serviceUri.toString(),
                    StandardOpenOption.CREATE);
        }

        String recordingName = descriptor.getName();
        if (recordingName.endsWith(".jfr")) {
            recordingName = recordingName.substring(0, recordingName.length() - 4);
        }

        // TODO: To avoid having to perform this lookup each time, we should implement
        // something like a map from targetIds to corresponding ServiceRefs
        String targetName =
                platformClient.listDiscoverableServices().stream()
                        .filter(
                                serviceRef -> {
                                    return serviceRef.getServiceUri().equals(serviceUri)
                                            && serviceRef.getAlias().isPresent();
                                })
                        .map(s -> s.getAlias().get())
                        .findFirst()
                        .orElse(connection.getHost())
                        .replaceAll("[\\._]+", "-");

        String timestamp =
                clock.now().truncatedTo(ChronoUnit.SECONDS).toString().replaceAll("[-:]+", "");
        String destination =
                String.format(
                        "%s_%s_%s",
                        URLEncoder.encode(targetName, StandardCharsets.UTF_8),
                        URLEncoder.encode(recordingName, StandardCharsets.UTF_8),
                        timestamp);
        // TODO byte-sized rename limit is arbitrary. Probably plenty since recordings are also
        // differentiated by second-resolution timestamp
        byte count = 1;
        while (fs.exists(specificRecordingsPath.resolve(destination + ".jfr"))) {
            destination =
                    String.format("%s_%s_%s.%d", targetName, recordingName, timestamp, count++);
            if (count == Byte.MAX_VALUE) {
                throw new IOException(
                        "Recording could not be saved; file already exists and rename attempts were"
                                + " exhausted.");
            }
        }
        destination += ".jfr";
        Path destinationPath = specificRecordingsPath.resolve(destination);
        try (BufferedInputStream bufferedStream =
                new BufferedInputStream(connection.getService().openStream(descriptor, false))) {

            // Check if recording stream is non-empty
            int readLimit = 1; // arbitrary number greater than 0
            bufferedStream.mark(readLimit);

            try {
                if (bufferedStream.read() == -1) {
                    fs.deleteIfExists(destinationPath);
                    throw new EmptyRecordingException();
                }
            } catch (IOException e) {
                throw new EmptyRecordingException(e);
            }

            bufferedStream.reset();

            fs.copy(bufferedStream, destinationPath);
        }
        return destinationPath;
    }

    private Optional<IRecordingDescriptor> getDescriptorByName(
            JFRConnection connection, String recordingName)
            throws FlightRecorderException, Exception {
        return connection.getService().getAvailableRecordings().stream()
                .filter(recording -> recording.getName().equals(recordingName))
                .findFirst();
    }

    private long getFileSize(String recordingName) {
        try {
            return Files.size(getRecordingPath(recordingName).get());
        } catch (IOException | InterruptedException | ExecutionException e) {
            logger.error("Invalid path: {}", recordingName);
            return 0;
        }
    }

    // FIXME: override equals() so that tests are easier to write
    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification =
                    "This class is never used by the client and is not stored, it is simply constructed upon request so it wouldn't matter even if somehow the List was modified")
    public static class ArchiveDirectory {
        private final String connectUrl;
        private final String jvmId;
        private final List<ArchivedRecordingInfo> recordings;

        public ArchiveDirectory(
                String connectUrl, String jvmId, List<ArchivedRecordingInfo> recordings) {
            this.connectUrl = connectUrl;
            this.jvmId = jvmId;
            this.recordings = recordings;
        }

        public String getConnectUrl() {
            return connectUrl;
        }

        public String getJvmId() {
            return jvmId;
        }

        public List<ArchivedRecordingInfo> getRecordings() {
            return Collections.unmodifiableList(recordings);
        }
    }
}
