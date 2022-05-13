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
import java.io.IOException;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
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
import io.cryostat.recordings.RecordingMetadataManager.Metadata;
import io.cryostat.rules.ArchivePathException;
import io.cryostat.rules.ArchivedRecordingInfo;
import io.cryostat.util.URIUtil;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.codec.binary.Base32;

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
    private final Base32 base32;

    private static final String SAVE_NOTIFICATION_CATEGORY = "ActiveRecordingSaved";
    private static final String DELETE_NOTIFICATION_CATEGORY = "ArchivedRecordingDeleted";

    public static final String UNLABELLED = "unlabelled";
    public static final String ARCHIVES = "archives";
    public static final String UPLOADED_RECORDINGS_SUBDIRECTORY = "uploads";

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
        this.base32 = base32;
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
                            },
                            false);
            validateSavePath(recordingName, savePath);
            Path parentPath = savePath.getParent();
            Path filenamePath = savePath.getFileName();
            String filename = filenamePath.toString();
            Metadata metadata =
                    recordingMetadataManager
                            .copyMetadataToArchives(
                                    connectionDescriptor.getTargetId(), recordingName, filename)
                            .get();
            ArchivedRecordingInfo archivedRecordingInfo =
                    new ArchivedRecordingInfo(
                            parentPath.toString(),
                            filename,
                            webServerProvider.get().getArchivedDownloadURL(filename),
                            webServerProvider.get().getArchivedReportURL(filename),
                            metadata);
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
    public Future<ArchivedRecordingInfo> deleteRecording(String recordingName) {

        CompletableFuture<ArchivedRecordingInfo> future = new CompletableFuture<>();

        try {
            Path archivedRecording = getRecordingPath(recordingName).get();
            fs.deleteIfExists(archivedRecording);
            validateSavePath(recordingName, archivedRecording);
            Path parentPath = archivedRecording.getParent();
            Path filenamePath = archivedRecording.getFileName();
            String filename = filenamePath.toString();
            ArchivedRecordingInfo archivedRecordingInfo =
                    new ArchivedRecordingInfo(
                            parentPath.toString(),
                            filename,
                            webServerProvider.get().getArchivedDownloadURL(filename),
                            webServerProvider.get().getArchivedReportURL(filename),
                            recordingMetadataManager.deleteRecordingMetadataIfExists(
                                    ARCHIVES, recordingName));
            String subdirectoryName = parentPath.getFileName().toString();
            String targetId =
                    (subdirectoryName.equals(UNLABELLED))
                            ? ""
                            : new String(base32.decode(subdirectoryName), StandardCharsets.UTF_8);
            notificationFactory
                    .createBuilder()
                    .metaCategory(DELETE_NOTIFICATION_CATEGORY)
                    .metaType(HttpMimeType.JSON)
                    .message(Map.of("recording", archivedRecordingInfo, "target", targetId))
                    .build()
                    .send();
            if (fs.listDirectoryChildren(parentPath).isEmpty()) {
                fs.deleteIfExists(parentPath);
            }
            future.complete(archivedRecordingInfo);
        } catch (IOException | InterruptedException | ExecutionException | URISyntaxException e) {
            future.completeExceptionally(e);
        } finally {
            deleteReport(recordingName);
        }

        return future;
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

    public boolean deleteReport(String recordingName) {
        try {
            logger.trace("Invalidating archived report cache for {}", recordingName);
            return fs.deleteIfExists(getCachedReportPath(recordingName));
        } catch (IOException ioe) {
            logger.warn(ioe);
            return false;
        }
    }

    public Path getCachedReportPath(String recordingName) {
        String fileName = recordingName + ".report.html";
        return archivedRecordingsReportPath.resolve(fileName).toAbsolutePath();
    }

    public Future<List<ArchivedRecordingInfo>> getRecordings(String targetId) {
        CompletableFuture<List<ArchivedRecordingInfo>> future = new CompletableFuture<>();

        String subdirectory =
                targetId.equals(UPLOADED_RECORDINGS_SUBDIRECTORY)
                        ? targetId
                        : base32.encodeAsString(targetId.getBytes(StandardCharsets.UTF_8));
        Path specificRecordingsPath = archivedRecordingsPath.resolve(subdirectory);

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
                    .map(
                            file -> {
                                try {
                                    return new ArchivedRecordingInfo(
                                            subdirectory,
                                            file,
                                            webServer.getArchivedDownloadURL(file),
                                            webServer.getArchivedReportURL(file),
                                            recordingMetadataManager.getMetadata(ARCHIVES, file));
                                } catch (SocketException
                                        | UnknownHostException
                                        | URISyntaxException e) {
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
            for (String subdirectory : subdirectories) {
                List<String> files =
                        this.fs.listDirectoryChildren(archivedRecordingsPath.resolve(subdirectory));
                List<ArchivedRecordingInfo> temp =
                        files.stream()
                                .map(
                                        file -> {
                                            try {
                                                return new ArchivedRecordingInfo(
                                                        subdirectory,
                                                        file,
                                                        webServer.getArchivedDownloadURL(file),
                                                        webServer.getArchivedReportURL(file),
                                                        recordingMetadataManager.getMetadata(
                                                                ARCHIVES, file));
                                            } catch (SocketException
                                                    | UnknownHostException
                                                    | URISyntaxException e) {
                                                logger.warn(e);
                                                return null;
                                            }
                                        })
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList());
                archivedRecordings.addAll(temp);
            }
            future.complete(archivedRecordings);
        } catch (ArchivePathException | IOException e) {
            future.completeExceptionally(e);
        }

        return future;
    }

    public Future<Path> getRecordingPath(String recordingName) {
        CompletableFuture<Path> future = new CompletableFuture<>();

        try {
            List<String> subdirectories = this.fs.listDirectoryChildren(archivedRecordingsPath);
            Optional<Path> optional =
                    searchSubdirectories(subdirectories, archivedRecordingsPath, recordingName);
            if (optional.isEmpty()) {
                throw new RecordingNotFoundException(ARCHIVES, recordingName);
            }
            Path archivedRecording = optional.get();
            if (!fs.exists(archivedRecording)) {
                throw new ArchivePathException(archivedRecording.toString(), "does not exist");
            }
            if (!fs.isRegularFile(archivedRecording)) {
                throw new ArchivePathException(
                        archivedRecording.toString(), "is not a regular file");
            }
            if (!fs.isReadable(archivedRecording)) {
                throw new ArchivePathException(archivedRecording.toString(), "is not readable");
            }
            future.complete(archivedRecording);
        } catch (RecordingNotFoundException | IOException | ArchivePathException e) {
            future.completeExceptionally(e);
        }

        return future;
    }

    private Optional<Path> searchSubdirectories(
            List<String> subdirectories, Path parent, String recordingName) throws IOException {
        // TODO refactor this into nicer streaming
        return subdirectories.stream()
                .map(parent::resolve)
                .map(
                        subdirectory -> {
                            try {
                                for (String file : this.fs.listDirectoryChildren(subdirectory)) {
                                    if (recordingName.equals(file)) {
                                        return subdirectory
                                                .resolve(file)
                                                .normalize()
                                                .toAbsolutePath();
                                    }
                                }
                            } catch (IOException ioe) {
                                logger.error(ioe);
                            }
                            return null;
                        })
                .filter(Objects::nonNull)
                .findFirst();
    }

    Path writeRecordingToDestination(JFRConnection connection, IRecordingDescriptor descriptor)
            throws IOException, URISyntaxException, FlightRecorderException, Exception {
        URI serviceUri = URIUtil.convert(connection.getJMXURL());
        String encodedServiceUri =
                base32.encodeAsString(serviceUri.toString().getBytes(StandardCharsets.UTF_8));
        Path specificRecordingsPath = archivedRecordingsPath.resolve(encodedServiceUri);

        if (!fs.exists(specificRecordingsPath)) {
            Files.createDirectory(specificRecordingsPath);
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
}
