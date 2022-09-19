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

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Named;
import javax.inject.Provider;
import javax.script.ScriptException;

import io.cryostat.MainModule;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.Credentials;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.platform.PlatformClient;
import io.cryostat.platform.TargetDiscoveryEvent;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.apache.commons.codec.binary.Base32;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.tuple.Pair;
import org.openjdk.jmc.rjmx.ConnectionException;

public class RecordingMetadataManager extends AbstractVerticle
        implements Consumer<TargetDiscoveryEvent> {

    public static final String NOTIFICATION_CATEGORY = "RecordingMetadataUpdated";
    private static final int STALE_METADATA_TIMEOUT_SECONDS = 5;
    private static final int TARGET_CONNECTION_TIMEOUT_SECONDS = 1;
    private static final String UPLOADS = RecordingArchiveHelper.UPLOADED_RECORDINGS_SUBDIRECTORY;

    private final Path recordingMetadataDir;
    private final Path archivedRecordingsPath;
    private final FileSystem fs;
    private final Provider<RecordingArchiveHelper> archiveHelperProvider;
    private final TargetConnectionManager targetConnectionManager;
    private final CredentialsManager credentialsManager;
    private final PlatformClient platformClient;
    private final NotificationFactory notificationFactory;
    private final JvmIdHelper jvmIdHelper;
    private final Gson gson;
    private final Base32 base32;
    private final Logger logger;

    private final Map<Pair<String, String>, Metadata> recordingMetadataMap;
    private final Map<String, Long> staleMetadataTimers;

    RecordingMetadataManager(
            Vertx vertx,
            Path recordingMetadataDir,
            @Named(MainModule.RECORDINGS_PATH) Path archivedRecordingsPath,
            FileSystem fs,
            Provider<RecordingArchiveHelper> archiveHelperProvider,
            TargetConnectionManager targetConnectionManager,
            CredentialsManager credentialsManager,
            PlatformClient platformClient,
            NotificationFactory notificationFactory,
            JvmIdHelper jvmIdHelper,
            Gson gson,
            Base32 base32,
            Logger logger) {
        this.vertx = vertx;
        this.recordingMetadataDir = recordingMetadataDir;
        this.archivedRecordingsPath = archivedRecordingsPath;
        this.fs = fs;
        this.archiveHelperProvider = archiveHelperProvider;
        this.targetConnectionManager = targetConnectionManager;
        this.credentialsManager = credentialsManager;
        this.platformClient = platformClient;
        this.notificationFactory = notificationFactory;
        this.jvmIdHelper = jvmIdHelper;
        this.gson = gson;
        this.base32 = base32;
        this.logger = logger;
        this.recordingMetadataMap = new ConcurrentHashMap<>();
        this.staleMetadataTimers = new ConcurrentHashMap<>();
    }

    @Override
    public void start(Promise<Void> future) {
        this.platformClient.addTargetDiscoveryListener(this);
        Map<StoredRecordingMetadata, Path> staleMetadata =
                new HashMap<StoredRecordingMetadata, Path>();
        RecordingArchiveHelper archiveHelper = archiveHelperProvider.get();
        try {
            this.fs.listDirectoryChildren(recordingMetadataDir).stream()
                    .peek(
                            n ->
                                    logger.info(
                                            "Peeking contents of recordingMetadata directory: {}",
                                            n))
                    .map(recordingMetadataDir::resolve)
                    .forEach(
                            subdirectory -> {
                                if (fs.isDirectory(subdirectory)) {
                                    try {
                                        this.fs.listDirectoryChildren(subdirectory).stream()
                                                .peek(
                                                        n ->
                                                                logger.info(
                                                                        "Recording"
                                                                                + " Metadata"
                                                                                + " file:"
                                                                                + " {}",
                                                                        n))
                                                .map(subdirectory::resolve)
                                                .filter(fs::isRegularFile)
                                                .map(
                                                        path -> {
                                                            try {
                                                                return Pair.of(
                                                                        fs.readFile(path), path);
                                                            } catch (IOException ioe) {
                                                                logger.error(
                                                                        "Could not read"
                                                                                + " metadata"
                                                                                + " file"
                                                                                + " {}, msg:"
                                                                                + " {}",
                                                                        path,
                                                                        ioe.getMessage());
                                                                deleteMetadataPathIfExists(path);
                                                                return null;
                                                            }
                                                        })
                                                .filter(Objects::nonNull)
                                                .forEach(
                                                        pair -> {
                                                            StoredRecordingMetadata srm =
                                                                    gson.fromJson(
                                                                            pair.getLeft(),
                                                                            StoredRecordingMetadata
                                                                                    .class);
                                                            Path file = pair.getRight();
                                                            String targetId = srm.getTargetId();
                                                            String recordingName =
                                                                    srm.getRecordingName();
                                                            // jvmId should always exist
                                                            // since we are
                                                            // using directory structure
                                                            if (srm.getJvmId() != null) {
                                                                try {
                                                                    if (!isArchivedRecording(
                                                                            recordingName)) {
                                                                        logger.info(
                                                                                "Potentially stale"
                                                                                    + " metadata"
                                                                                    + " file: {},"
                                                                                    + " for target:"
                                                                                    + " {}",
                                                                                recordingName,
                                                                                targetId);
                                                                        staleMetadata.put(
                                                                                srm, file);
                                                                        return;
                                                                    }
                                                                } catch (IOException e) {
                                                                    logger.error(
                                                                            "Could not check if"
                                                                                + " recording {}"
                                                                                + " exists on"
                                                                                + " target {}, msg:"
                                                                                + " {}",
                                                                            recordingName,
                                                                            targetId,
                                                                            e.getMessage());
                                                                }
                                                                // archived recording
                                                                // metadata
                                                                jvmIdHelper.putIfAbsent(
                                                                        targetId, srm.getJvmId());
                                                                recordingMetadataMap.put(
                                                                        Pair.of(
                                                                                srm.getJvmId(),
                                                                                recordingName),
                                                                        srm);
                                                            } else {
                                                                logger.warn(
                                                                        "Invalid metadata with"
                                                                                + " no jvmId"
                                                                                + " originating"
                                                                                + " from"
                                                                                + " {}",
                                                                        targetId);
                                                                deleteMetadataPathIfExists(file);
                                                            }
                                                        });
                                    } catch (IOException e) {
                                        logger.error(
                                                "Could not read metadata subdirectory"
                                                        + " {}, msg: {}",
                                                subdirectory,
                                                e.getMessage());
                                    }
                                }
                                /* TODO: This is a ONE-TIME migration check for the old metadata files that were stored without a directory
                                (remove after 2.2.0 release and replace with subdirectory::fs.isDirectory (ignore files))? */
                                else if (fs.isRegularFile(subdirectory)) {
                                    StoredRecordingMetadata srm;
                                    try {
                                        srm =
                                                gson.fromJson(
                                                        fs.readFile(subdirectory),
                                                        StoredRecordingMetadata.class);
                                    } catch (Exception e) {
                                        logger.error(
                                                "Could not read file {} in recordingMetadata"
                                                        + " directory, msg: {}",
                                                subdirectory,
                                                e.getMessage());
                                        deleteMetadataPathIfExists(subdirectory);
                                        return;
                                    }
                                    logger.info("Found old metadata file: {}", subdirectory);
                                    String targetId = srm.getTargetId();
                                    String recordingName = srm.getRecordingName();
                                    if (targetId.equals("archives")) {
                                        try {
                                            if (isArchivedRecording(recordingName)) {

                                                Path recordingPath =
                                                        archiveHelper
                                                                .getRecordingPath(recordingName)
                                                                .get();
                                                String subdirectoryName =
                                                        recordingPath
                                                                .getParent()
                                                                .getFileName()
                                                                .toString();
                                                String newTargetId =
                                                        new String(
                                                                base32.decode(subdirectoryName),
                                                                StandardCharsets.UTF_8);
                                                logger.info(
                                                        "Found metadata corresponding"
                                                                + " to archived recording:"
                                                                + " {}",
                                                        recordingName);
                                                setRecordingMetadata(
                                                        new ConnectionDescriptor(newTargetId),
                                                        recordingName,
                                                        new Metadata(srm.getLabels()));
                                            } else {
                                                logger.warn(
                                                        "Found metadata for lost"
                                                                + " archived recording: {}",
                                                        recordingName,
                                                        subdirectory);
                                                deleteMetadataPathIfExists(subdirectory);
                                            }
                                        } catch (InterruptedException | ExecutionException e) {
                                            logger.error(
                                                    "Couldn't get recording path {}",
                                                    recordingName);
                                        } catch (IOException e) {
                                            logger.error(
                                                    "Couldn't check if recording was"
                                                            + " archived {}",
                                                    recordingName);
                                        }

                                    } else {
                                        logger.info(
                                                "Potentially stale metadata file: {}, for target:"
                                                        + " {}",
                                                recordingName,
                                                targetId);
                                        staleMetadata.put(srm, subdirectory);
                                        return;
                                    }
                                    // delete old metadata file after migration
                                    try {
                                        fs.deleteIfExists(subdirectory);
                                        logger.info("Removed old metadata file: {}", subdirectory);
                                    } catch (IOException e) {
                                        logger.error(
                                                "Failed to delete metadata file {}," + " msg: {}",
                                                subdirectory,
                                                e.getCause());
                                    }
                                } else {
                                    logger.warn(
                                            "Recording Metadata subdirectory {} is"
                                                    + " neither a directory nor a file",
                                            subdirectory);
                                    // invalid
                                    throw new IllegalStateException(
                                            subdirectory + " is neither a directory nor a file");
                                }
                            });
            archiveHelper.migrate();
            future.complete();
        } catch (IOException e) {
            logger.error(
                    "Could not read recordingMetadataDirectory! {}, msg: {}",
                    recordingMetadataDir,
                    e.getMessage());
            future.fail(e.getCause());
        }
        pruneStaleMetadata(staleMetadata);
    }

    @Override
    public void stop() {
        this.platformClient.removeTargetDiscoveryListener(this);
    }

    @Override
    public void accept(TargetDiscoveryEvent tde) {
        String targetId = tde.getServiceRef().getServiceUri().toString();
        String oldJvmId = jvmIdHelper.get(targetId);
        logger.info("RECORDING METADATA MANAGER {}", targetId, oldJvmId);

        ConnectionDescriptor cd;

        try {
            cd = getConnectionDescriptorWithCredentials(tde);
        } catch (IOException | ScriptException e) {
            logger.error(
                    "Could not get credentials on FOUND target {}, msg: {}",
                    targetId,
                    e.getMessage());
            return;
        }

        if (oldJvmId == null) {
            logger.info("Target {} did not have a jvmId", targetId);
            try {
                String newJvmId = jvmIdHelper.getJvmId(cd);
                logger.info("Created jvmId {} for target {}", newJvmId, targetId);
                return;
            } catch (IOException e) {
                logger.error("Could not compute jvmId on FOUND target {}, msg: {}", targetId);
                e.printStackTrace();
            }
        }

        switch (tde.getEventKind()) {
            case FOUND:
                var archiveHelper = archiveHelperProvider.get();
                Path subdirectoryPath = archiveHelper.getRecordingSubdirectoryPath(oldJvmId);
                this.transferMetadataIfRestarted(cd, oldJvmId, targetId);
                archiveHelper.transferArchives(subdirectoryPath, oldJvmId);
                break;
            case LOST:
                this.removeLostTargetMetadata(cd, oldJvmId);
                break;
            default:
                throw new UnsupportedOperationException(tde.getEventKind().toString());
        }
    }

    // Pre-condition: staleMetadata is Mapping of metadata to its filesystem path, pertaining to any
    // previously active recording
    // Asynchronous since testing connections to targets takes long, especially for many targets
    private void pruneStaleMetadata(Map<StoredRecordingMetadata, Path> staleMetadata) {
        vertx.executeBlocking(
                future -> {
                    try {
                        logger.info("Trying to prune stale metadata");
                        staleMetadata.forEach(
                                (srm, path) -> {
                                    String targetId = srm.getTargetId();
                                    String recordingName = srm.getRecordingName();
                                    ConnectionDescriptor cd;
                                    try {
                                        cd = getConnectionDescriptorWithCredentials(targetId);
                                    } catch (Exception e) {
                                        logger.error(
                                                "Could not get credentials for"
                                                        + " targetId {}, msg: {}",
                                                targetId,
                                                e.getMessage());
                                        return;
                                    }
                                    logger.info(
                                            "Trying to prune stale recording metadata {}, of target"
                                                    + " {}, from path {}",
                                            recordingName,
                                            targetId,
                                            path);
                                    if (!targetRecordingExists(cd, recordingName)) {
                                        // recording was lost
                                        logger.info(
                                                "Active recording lost {}, deleting...",
                                                recordingName);
                                        deleteMetadataPathIfExists(path);
                                    } else {
                                        // target still up
                                        logger.info(
                                                "Found metadata corresponding to active recording:"
                                                        + " {}",
                                                recordingName);
                                        try {
                                            setRecordingMetadata(
                                                    cd,
                                                    recordingName,
                                                    new Metadata(srm.getLabels()));
                                        } catch (IOException e) {
                                            logger.error(
                                                    "Could not set metadata for recording: {}, msg:"
                                                            + " {}",
                                                    recordingName,
                                                    e.getMessage());
                                        }
                                    }
                                });
                    } finally {
                        future.complete();
                    }
                },
                result -> logger.info("Successfully pruned all stale metadata"));
    }

    public Future<Metadata> setRecordingMetadata(
            ConnectionDescriptor connectionDescriptor,
            String recordingName,
            Metadata metadata,
            boolean issueNotification)
            throws IOException {
        Objects.requireNonNull(connectionDescriptor);
        Objects.requireNonNull(recordingName);
        Objects.requireNonNull(metadata);

        String jvmId = jvmIdHelper.getJvmId(connectionDescriptor);
        this.recordingMetadataMap.put(Pair.of(jvmId, recordingName), metadata);

        fs.writeString(
                this.getMetadataPath(jvmId, recordingName),
                gson.toJson(
                        StoredRecordingMetadata.of(
                                connectionDescriptor.getTargetId(),
                                jvmId,
                                recordingName,
                                metadata)),
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        if (issueNotification) {
            notificationFactory
                    .createBuilder()
                    .metaCategory(RecordingMetadataManager.NOTIFICATION_CATEGORY)
                    .metaType(HttpMimeType.JSON)
                    .message(
                            Map.of(
                                    "recordingName",
                                    recordingName,
                                    "target",
                                    connectionDescriptor.getTargetId(),
                                    "metadata",
                                    metadata))
                    .build()
                    .send();
        }

        return CompletableFuture.completedFuture(metadata);
    }

    public Future<Metadata> setRecordingMetadata(
            ConnectionDescriptor connectionDescriptor, String recordingName, Metadata metadata)
            throws IOException {
        Objects.requireNonNull(connectionDescriptor);
        Objects.requireNonNull(recordingName);
        Objects.requireNonNull(metadata);
        return setRecordingMetadata(connectionDescriptor, recordingName, metadata, false);
    }

    public Future<Metadata> setRecordingMetadata(String recordingName, Metadata metadata)
            throws IOException {
        Objects.requireNonNull(recordingName);
        Objects.requireNonNull(metadata);
        return this.setRecordingMetadata(
                new ConnectionDescriptor(UPLOADS), recordingName, metadata);
    }

    public Metadata getMetadata(ConnectionDescriptor connectionDescriptor, String recordingName)
            throws IOException {
        Objects.requireNonNull(connectionDescriptor);
        Objects.requireNonNull(recordingName);

        if (connectionDescriptor.getTargetId().equals(UPLOADS)) {
            return this.recordingMetadataMap.computeIfAbsent(
                    Pair.of(UPLOADS, recordingName), k -> new Metadata());
        }

        String jvmId = jvmIdHelper.getJvmId(connectionDescriptor);
        return this.recordingMetadataMap.computeIfAbsent(
                Pair.of(jvmId, recordingName), k -> new Metadata());
    }

    public Metadata deleteRecordingMetadataIfExists(
            ConnectionDescriptor connectionDescriptor, String recordingName) throws IOException {
        Objects.requireNonNull(connectionDescriptor);
        Objects.requireNonNull(recordingName);
        String jvmId = jvmIdHelper.getJvmId(connectionDescriptor);

        Metadata deleted = this.recordingMetadataMap.remove(Pair.of(jvmId, recordingName));
        Path metadataPath = this.getMetadataPath(jvmId, recordingName);
        if (fs.deleteIfExists(metadataPath)) {
            deleteSubdirectoryIfEmpty(metadataPath.getParent());
        }
        return deleted;
    }

    public Future<Metadata> copyMetadataToArchives(
            ConnectionDescriptor connectionDescriptor, String recordingName, String filename)
            throws IOException {
        Objects.requireNonNull(connectionDescriptor);
        Objects.requireNonNull(recordingName);
        Objects.requireNonNull(filename);
        Metadata metadata = this.getMetadata(connectionDescriptor, recordingName);
        return this.setRecordingMetadata(connectionDescriptor, filename, metadata);
    }

    public Map<String, String> parseRecordingLabels(String labels) throws IllegalArgumentException {
        Objects.requireNonNull(labels, "Labels must not be null");

        try {
            Type mapType = new TypeToken<Map<String, String>>() {}.getType();
            Map<String, String> parsedLabels = gson.fromJson(labels, mapType);
            if (parsedLabels == null) {
                throw new IllegalArgumentException(labels);
            }
            Pattern noWhitespace = Pattern.compile("^(\\S)+$");

            for (var label : parsedLabels.entrySet()) {
                Matcher keyMatcher = noWhitespace.matcher(label.getKey());
                Matcher valueMatcher = noWhitespace.matcher(label.getValue());

                if (!keyMatcher.matches() || !valueMatcher.matches()) {
                    throw new IllegalArgumentException("Labels must not contain whitespace");
                }
            }
            return parsedLabels;
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private void transferMetadataIfRestarted(
            ConnectionDescriptor cd, String oldJvmId, String targetId) {
        try {
            String newJvmId =
                    this.targetConnectionManager.executeConnectedTask(
                            cd,
                            connection -> {
                                try {
                                    return connection.getJvmId();
                                } catch (Exception e) {
                                    logger.error(e);
                                    return null;
                                }
                            });

            if (newJvmId == null) {
                logger.info(
                        "Couldn't generate a new jvmId for target {} with old jvmId {}",
                        targetId,
                        oldJvmId);
                return;
            }

            if (oldJvmId.equals(newJvmId)) {
                Long id = staleMetadataTimers.remove(oldJvmId);
                if (id != null) {
                    this.vertx.cancelTimer(id);
                }
                return;
            }
            logger.info("{} Metadata transfer: {} -> {}", targetId, oldJvmId, newJvmId);
            recordingMetadataMap.keySet().stream()
                    .filter(
                            keyPair ->
                                    keyPair.getKey() != null && keyPair.getKey().equals(oldJvmId))
                    .forEach(
                            keyPair -> {
                                try {
                                    String recordingName = keyPair.getValue();

                                    Metadata m = this.getMetadata(cd, recordingName);
                                    deleteRecordingMetadataIfExists(cd, recordingName);
                                    jvmIdHelper.put(targetId, newJvmId);
                                    setRecordingMetadata(cd, recordingName, m);
                                    jvmIdHelper.put(targetId, oldJvmId);

                                } catch (IOException e) {
                                    logger.error("Metadata could not be transferred", e);
                                }
                            });
            jvmIdHelper.put(targetId, newJvmId);
            jvmIdHelper.transferJvmIds(oldJvmId, newJvmId);
            logger.info(
                    "{} Metadata successfully transferred: {} -> {}", targetId, oldJvmId, newJvmId);
        } catch (Exception e) {
            logger.error("Metadata could not be transferred upon target restart", e);
        }
    }

    private void removeLostTargetMetadata(ConnectionDescriptor cd, String unreachableJvmId) {
        staleMetadataTimers.computeIfAbsent(
                unreachableJvmId,
                k ->
                        this.vertx.setTimer(
                                Duration.ofSeconds(STALE_METADATA_TIMEOUT_SECONDS).toMillis(),
                                initialId -> {
                                    if (this.isTargetReachable(cd)) {
                                        return;
                                    }
                                    recordingMetadataMap.keySet().stream()
                                            .forEach(
                                                    keyPair -> {
                                                        if (!keyPair.getKey()
                                                                .equals(unreachableJvmId)) {
                                                            return;
                                                        }

                                                        try {
                                                            String recordingName =
                                                                    keyPair.getValue();

                                                            if (!isArchivedRecording(
                                                                    recordingName)) {
                                                                deleteRecordingMetadataIfExists(
                                                                        cd, recordingName);
                                                            }
                                                        } catch (IOException e) {
                                                            logger.error(e);
                                                        }
                                                    });
                                }));
    }

    private boolean isArchivedRecording(String recordingName) throws IOException {
        try {
            return this.fs.listDirectoryChildren(archivedRecordingsPath).stream()
                    .map(
                            subdirectory -> {
                                try {
                                    return fs
                                            .listDirectoryChildren(
                                                    archivedRecordingsPath.resolve(subdirectory))
                                            .stream()
                                            .anyMatch(
                                                    filename -> {
                                                        return filename.equals(recordingName);
                                                    });
                                } catch (IOException e) {
                                    // java streams doesn't allow checked exceptions to propagate
                                    // can assume won't throw b/c previous calls to other io methods
                                    // were successful
                                    logger.error(e);
                                    return false;
                                }
                            })
                    .filter(v -> v)
                    .findFirst()
                    .orElse(false);
        } catch (IOException ioe) {
            logger.error(ioe);
            throw ioe;
        }
    }

    private boolean isTargetReachable(ConnectionDescriptor cd) {
        CompletableFuture<Boolean> connectFuture = new CompletableFuture<>();
        try {
            this.targetConnectionManager.executeConnectedTask(
                    cd, connection -> connectFuture.complete(connection.isConnected()));
            return connectFuture.get(TARGET_CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.warn("Target unreachable {}", cd.getTargetId());
            return false;
        }
    }

    private boolean targetRecordingExists(ConnectionDescriptor cd, String recordingName) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        try {
            this.targetConnectionManager.executeConnectedTask(
                    cd,
                    conn ->
                            conn.getService().getAvailableRecordings().stream()
                                    .anyMatch(
                                            r ->
                                                    future.complete(
                                                            Objects.equals(
                                                                    recordingName, r.getName()))));
            return future.get(TARGET_CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            logger.warn("Target unreachable {}, msg {}", cd.getTargetId(), te.getMessage());
            return false;
        } catch (Exception e) {
            logger.error(e);
            return false;
        }
    }

    private Path getMetadataPath(String jvmId, String recordingName) throws IOException {
        String subdirectory =
                jvmId.equals(UPLOADS)
                        ? UPLOADS
                        : base32.encodeAsString(jvmId.getBytes(StandardCharsets.UTF_8));
        String filename =
                base32.encodeAsString(recordingName.getBytes(StandardCharsets.UTF_8)) + ".json";
        if (!fs.exists(recordingMetadataDir.resolve(subdirectory))) {
            fs.createDirectory(recordingMetadataDir.resolve(subdirectory));
        }
        return recordingMetadataDir.resolve(subdirectory).resolve(filename);
    }

    private boolean deleteMetadataPathIfExists(Path path) {
        if (fs.exists(path)) {
            try {
                if (fs.deleteIfExists(path)) {
                    logger.info("Deleted metadata file {}", path);
                    deleteSubdirectoryIfEmpty(path.getParent());
                    return true;
                } else {
                    logger.error("Failed to delete metadata file {}", path);
                }
            } catch (IOException e) {
                logger.error("Failed to delete metadata file {}, {}", path, e.getCause());
            }
        } else {
            logger.error("Try to delete path that doesn't exist {}", path);
        }
        return false;
    }

    private boolean deleteSubdirectoryIfEmpty(Path path) {
        if (path == null || path.equals(recordingMetadataDir)) {
            return false;
        }
        if (fs.exists(path)) {
            try {
                if (fs.listDirectoryChildren(path).isEmpty()) {
                    return fs.deleteIfExists(path);
                }
            } catch (IOException e) {
                logger.error("Couldn't delete path {}, msg: {}", path, e.getCause());
            }
        } else {
            logger.error("Try to delete path that doesn't exist {}", path);
        }
        return false;
    }

    private ConnectionDescriptor getConnectionDescriptorWithCredentials(TargetDiscoveryEvent tde)
            throws JsonSyntaxException, JsonIOException, IOException, ScriptException {
        Credentials credentials = credentialsManager.getCredentials(tde.getServiceRef());
        return new ConnectionDescriptor(tde.getServiceRef(), credentials);
    }

    private ConnectionDescriptor getConnectionDescriptorWithCredentials(String targetId)
            throws JsonSyntaxException, JsonIOException, IOException, ScriptException {
        Credentials credentials = credentialsManager.getCredentialsByTargetId(targetId);
        return new ConnectionDescriptor(targetId, credentials);
    }

    private static class StoredRecordingMetadata extends Metadata {
        private final String jvmId;
        private final String recordingName;
        private final String targetId;

        StoredRecordingMetadata(
                String targetId, String jvmId, String recordingName, Map<String, String> labels) {
            super(labels);
            this.targetId = targetId;
            this.jvmId = jvmId;
            this.recordingName = recordingName;
        }

        static StoredRecordingMetadata of(
                String targetId, String jvmId, String recordingName, Metadata metadata) {
            return new StoredRecordingMetadata(
                    targetId, jvmId, recordingName, metadata.getLabels());
        }

        String getTargetId() {
            return this.targetId;
        }

        String getJvmId() {
            return this.jvmId;
        }

        String getRecordingName() {
            return this.recordingName;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null) {
                return false;
            }
            if (o == this) {
                return true;
            }
            if (!(o instanceof StoredRecordingMetadata)) {
                return false;
            }

            StoredRecordingMetadata srm = (StoredRecordingMetadata) o;
            return new EqualsBuilder()
                    .appendSuper(super.equals(o))
                    .append(targetId, srm.targetId)
                    .append(jvmId, srm.jvmId)
                    .append(recordingName, srm.recordingName)
                    .build();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder()
                    .appendSuper(super.hashCode())
                    .append(targetId)
                    .append(jvmId)
                    .append(recordingName)
                    .toHashCode();
        }
    }

    public static class Metadata {
        protected final Map<String, String> labels;

        public Metadata() {
            this.labels = new ConcurrentHashMap<>();
        }

        public Metadata(Metadata o) {
            this.labels = new ConcurrentHashMap<>(o.labels);
        }

        public Metadata(Map<String, String> labels) {
            this.labels = new ConcurrentHashMap<>(labels);
        }

        public Map<String, String> getLabels() {
            return new ConcurrentHashMap<>(labels);
        }

        @Override
        public boolean equals(Object o) {
            if (o == null) {
                return false;
            }
            if (o == this) {
                return true;
            }
            if (!(o instanceof Metadata)) {
                return false;
            }

            Metadata metadata = (Metadata) o;
            return new EqualsBuilder().append(labels, metadata.labels).build();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder().append(labels).toHashCode();
        }
    }
}
