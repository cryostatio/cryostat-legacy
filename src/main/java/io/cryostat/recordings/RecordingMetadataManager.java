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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Provider;
import javax.script.ScriptException;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.Credentials;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.SecurityContext;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.TargetDiscoveryEvent;
import io.cryostat.util.events.Event;
import io.cryostat.util.events.EventListener;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.EventBus;
import org.apache.commons.codec.binary.Base32;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.tuple.Pair;

public class RecordingMetadataManager extends AbstractVerticle
        implements Consumer<TargetDiscoveryEvent>, EventListener<JvmIdHelper.IdEvent, String> {

    public static final String NOTIFICATION_CATEGORY = "RecordingMetadataUpdated";
    private static final String UPLOADS = RecordingArchiveHelper.UPLOADED_RECORDINGS_SUBDIRECTORY;

    private final AuthManager auth;
    private final ExecutorService executor;
    private final Path recordingMetadataDir;
    private final Path archivedRecordingsPath;
    private final long connectionTimeoutSeconds;
    private final FileSystem fs;
    private final Provider<RecordingArchiveHelper> archiveHelperProvider;
    private final TargetConnectionManager targetConnectionManager;
    private final CredentialsManager credentialsManager;
    private final DiscoveryStorage discoveryStorage;
    private final NotificationFactory notificationFactory;
    private final JvmIdHelper jvmIdHelper;
    private final Gson gson;
    private final Base32 base32;
    private final Logger logger;

    private final CountDownLatch migrationLatch = new CountDownLatch(1);

    RecordingMetadataManager(
            AuthManager auth,
            ExecutorService executor,
            Path recordingMetadataDir,
            Path archivedRecordingsPath,
            long connectionTimeoutSeconds,
            FileSystem fs,
            Provider<RecordingArchiveHelper> archiveHelperProvider,
            TargetConnectionManager targetConnectionManager,
            CredentialsManager credentialsManager,
            DiscoveryStorage discoveryStorage,
            NotificationFactory notificationFactory,
            JvmIdHelper jvmIdHelper,
            Gson gson,
            Base32 base32,
            Logger logger) {
        this.auth = auth;
        this.executor = executor;
        this.recordingMetadataDir = recordingMetadataDir;
        this.archivedRecordingsPath = archivedRecordingsPath;
        this.connectionTimeoutSeconds = connectionTimeoutSeconds;
        this.fs = fs;
        this.archiveHelperProvider = archiveHelperProvider;
        this.targetConnectionManager = targetConnectionManager;
        this.credentialsManager = credentialsManager;
        this.discoveryStorage = discoveryStorage;
        this.notificationFactory = notificationFactory;
        this.jvmIdHelper = jvmIdHelper;
        this.gson = gson;
        this.base32 = base32;
        this.logger = logger;
    }

    @Override
    public void start(Promise<Void> future) {
        this.discoveryStorage.addTargetDiscoveryListener(this);
        this.jvmIdHelper.addListener(this);
        Map<StoredRecordingMetadata, Path> staleMetadata =
                new HashMap<StoredRecordingMetadata, Path>();
        RecordingArchiveHelper archiveHelper = archiveHelperProvider.get();
        try {
            this.fs.listDirectoryChildren(recordingMetadataDir).stream()
                    .peek(n -> logger.info("Peeking contents of metadata directory: {}", n))
                    .map(recordingMetadataDir::resolve)
                    .forEach(
                            subdirectory -> {
                                if (fs.isDirectory(subdirectory)) {
                                    try {
                                        String subdirectoryName =
                                                subdirectory.getFileName().toString();
                                        if (jvmIdHelper.isSpecialDirectory(subdirectoryName)) {
                                            logger.info(
                                                    "Skipping metadata validation: appears to be a"
                                                            + " special location: {}",
                                                    subdirectoryName);
                                            return;
                                        } else if (this.fs
                                                .listDirectoryChildren(subdirectory)
                                                .isEmpty()) {
                                            logger.info(
                                                    "Deleting empty recording metadata directory:"
                                                            + " {}",
                                                    subdirectory);
                                            this.fs.deleteIfExists(subdirectory);
                                        } else {
                                            this.fs.listDirectoryChildren(subdirectory).stream()
                                                    .peek(
                                                            n ->
                                                                    logger.trace(
                                                                            "Recording"
                                                                                    + " Metadata"
                                                                                    + " file:"
                                                                                    + " {}",
                                                                            n))
                                                    .map(subdirectory::resolve)
                                                    .filter(fs::isRegularFile)
                                                    .filter(
                                                            path ->
                                                                    !path.getFileName()
                                                                            .toString()
                                                                            .equals("connectUrl"))
                                                    .map(
                                                            path -> {
                                                                try {
                                                                    return Pair.of(
                                                                            fs.readFile(path),
                                                                            path);
                                                                } catch (IOException ioe) {
                                                                    logger.error(
                                                                            "Could not read"
                                                                                    + " metadata"
                                                                                    + " file"
                                                                                    + " {}, msg:"
                                                                                    + " {}",
                                                                            path,
                                                                            ioe.getMessage());
                                                                    deleteMetadataPathIfExists(
                                                                            path);
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
                                                                // since we are using directory
                                                                // structure
                                                                if (srm.getJvmId() != null) {
                                                                    try {
                                                                        if (!isArchivedRecording(
                                                                                recordingName)) {
                                                                            logger.info(
                                                                                    "Potentially"
                                                                                        + " stale"
                                                                                        + " metadata"
                                                                                        + " file:"
                                                                                        + " {}, for"
                                                                                        + " target:"
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
                                                                                    + " recording"
                                                                                    + " {} exists"
                                                                                    + " on target"
                                                                                    + " {}, msg:"
                                                                                    + " {}",
                                                                                recordingName,
                                                                                targetId,
                                                                                e.getMessage());
                                                                    }
                                                                } else {
                                                                    logger.warn(
                                                                            "Invalid metadata with"
                                                                                    + " no jvmId"
                                                                                    + " originating"
                                                                                    + " from"
                                                                                    + " {}",
                                                                            targetId);
                                                                    deleteMetadataPathIfExists(
                                                                            file);
                                                                }
                                                            });
                                        }
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
                                                        srm.getLabels());
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
                                            "Recording metadata subdirectory {} is"
                                                    + " neither a directory nor a file",
                                            subdirectory);
                                    throw new IllegalStateException(
                                            subdirectory + " is neither a directory nor a file");
                                }
                            });

            future.complete();
        } catch (IOException e) {
            logger.error(
                    "Could not read recording metadata directory! {}, msg: {}",
                    recordingMetadataDir,
                    e.getMessage());
            future.fail(e.getCause());
        }
        EventBus eb = vertx.eventBus();
        eb.consumer(
                DiscoveryStorage.DISCOVERY_STARTUP_ADDRESS,
                message -> {
                    logger.trace(
                            "Event bus [{}]: {}",
                            DiscoveryStorage.DISCOVERY_STARTUP_ADDRESS,
                            message.body());
                    new Thread(
                                    () -> {
                                        try {
                                            logger.info("Starting archive migration");
                                            archiveHelper.migrate(executor);
                                            logger.info("Successfully migrated archives");
                                            pruneStaleMetadata(staleMetadata);
                                            logger.info("Successfully pruned all stale metadata");
                                        } catch (Exception e) {
                                            logger.warn(
                                                    "Couldn't read archived recordings directory");
                                            logger.warn(e);
                                        } finally {
                                            migrationLatch.countDown();
                                        }
                                    })
                            .start();
                });
    }

    @Override
    public void stop() {
        this.discoveryStorage.removeTargetDiscoveryListener(this);
    }

    @Override
    public void accept(TargetDiscoveryEvent tde) {
        executor.execute(
                () -> {
                    try {
                        migrationLatch.await();
                    } catch (InterruptedException e) {
                        logger.error(e);
                    }

                    switch (tde.getEventKind()) {
                        case FOUND:
                            handleFoundTarget(tde.getServiceRef());
                            break;
                        case LOST:
                            // don't handle directly, let the JvmIdHelper invalidate its cached
                            // ID and inform us of that occurrence, and use that invalidation
                            // message to clear our stored metadata
                            break;
                        default:
                            throw new UnsupportedOperationException(tde.getEventKind().toString());
                    }
                });
    }

    private void handleFoundTarget(ServiceRef serviceRef) {
        ConnectionDescriptor cd;
        try {
            cd = getConnectionDescriptorWithCredentials(serviceRef);
        } catch (IOException | ScriptException e) {
            logger.error(
                    "Could not get credentials on FOUND target {}, msg: {}",
                    serviceRef.getServiceUri().toString(),
                    e.getMessage());
            return;
        }
        this.transferMetadataIfRestarted(cd);
        archiveHelperProvider.get().transferArchivesIfRestarted(cd.getTargetId());
    }

    @Override
    public void onEvent(Event<JvmIdHelper.IdEvent, String> event) {
        switch (event.getEventType()) {
            case INVALIDATED:
                this.removeLostTargetMetadata(event.getPayload());
                break;
            default:
                throw new UnsupportedOperationException(event.getEventType().toString());
        }
    }

    // Pre-condition: staleMetadata is Mapping of metadata to its filesystem path, pertaining to any
    // previously active recording
    private void pruneStaleMetadata(Map<StoredRecordingMetadata, Path> staleMetadata) {
        logger.info("Beginning to prune potentially stale metadata...");
        staleMetadata.forEach(
                (srm, path) -> {
                    String targetId = srm.getTargetId();
                    String recordingName = srm.getRecordingName();
                    ConnectionDescriptor cd;
                    try {
                        cd = getConnectionDescriptorWithCredentials(targetId);
                    } catch (Exception e) {
                        logger.error(
                                "Could not get credentials for targetId {}, msg: {}",
                                targetId,
                                e.getMessage());
                        return;
                    }
                    logger.info(
                            "Attempting to prune potentially stale recording metadata {}, from"
                                    + " target {}, on path {}",
                            recordingName,
                            targetId,
                            path);
                    if (!targetRecordingExists(cd, recordingName)) {
                        // recording was lost
                        logger.info("Active recording lost {}, deleting...", recordingName);
                        deleteMetadataPathIfExists(path);
                    } else {
                        // target still up
                        logger.info(
                                "Found active recording corresponding to recording metadata: {}",
                                recordingName);
                        try {
                            setRecordingMetadata(cd, recordingName, srm.getLabels());
                        } catch (IOException e) {
                            logger.error(
                                    "Could not set metadata for recording: {}, msg: {}",
                                    recordingName,
                                    e.getMessage());
                        }
                    }
                });
    }

    public Future<Metadata> setRecordingMetadataFromPath(
            String subdirectoryName, String recordingName, Metadata metadata)
            throws IOException, InterruptedException, ExecutionException {
        Objects.requireNonNull(subdirectoryName);
        Objects.requireNonNull(recordingName);
        Objects.requireNonNull(metadata);
        String connectUrl =
                this.archiveHelperProvider
                        .get()
                        .getConnectUrlFromPath(archivedRecordingsPath.resolve(subdirectoryName))
                        .get();
        String jvmId = jvmIdHelper.subdirectoryNameToJvmId(subdirectoryName);

        SecurityContext securityContext =
                discoveryStorage
                        .lookupServiceByTargetId(connectUrl)
                        .map(auth::contextFor)
                        .orElse(SecurityContext.DEFAULT);

        Metadata contextualMetadata = new Metadata(securityContext, metadata.getLabels());

        Path metadataPath = this.getMetadataPath(jvmId, recordingName);
        fs.writeString(
                metadataPath,
                gson.toJson(
                        StoredRecordingMetadata.of(
                                connectUrl, jvmId, recordingName, contextualMetadata)),
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);

        notificationFactory
                .createBuilder()
                .metaCategory(RecordingMetadataManager.NOTIFICATION_CATEGORY)
                .metaType(HttpMimeType.JSON)
                .message(
                        Map.of(
                                "recordingName",
                                recordingName,
                                "target",
                                connectUrl,
                                "metadata",
                                metadata))
                .build()
                .send();

        return CompletableFuture.completedFuture(metadata);
    }

    public Future<Metadata> setRecordingMetadata(
            ConnectionDescriptor connectionDescriptor,
            String recordingName,
            Map<String, String> labels,
            boolean issueNotification)
            throws IOException {
        Objects.requireNonNull(connectionDescriptor);
        Objects.requireNonNull(recordingName);
        Objects.requireNonNull(labels);
        String jvmId = jvmIdHelper.getJvmId(connectionDescriptor);

        SecurityContext securityContext =
                discoveryStorage
                        .lookupServiceByTargetId(connectionDescriptor.getTargetId())
                        .map(auth::contextFor)
                        .orElse(SecurityContext.DEFAULT);

        Metadata contextualMetadata = new Metadata(securityContext, labels);

        Path metadataPath = this.getMetadataPath(jvmId, recordingName);
        fs.writeString(
                metadataPath,
                gson.toJson(
                        StoredRecordingMetadata.of(
                                connectionDescriptor.getTargetId(),
                                jvmId,
                                recordingName,
                                contextualMetadata)),
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
                                    contextualMetadata))
                    .build()
                    .send();
        }

        return CompletableFuture.completedFuture(contextualMetadata);
    }

    public Future<Metadata> setRecordingMetadata(
            ConnectionDescriptor connectionDescriptor,
            String recordingName,
            Map<String, String> labels)
            throws IOException {
        Objects.requireNonNull(connectionDescriptor);
        Objects.requireNonNull(recordingName);
        Objects.requireNonNull(labels);
        return setRecordingMetadata(connectionDescriptor, recordingName, labels, false);
    }

    public Future<Metadata> setRecordingMetadata(String recordingName, Map<String, String> labels)
            throws IOException {
        Objects.requireNonNull(recordingName);
        Objects.requireNonNull(labels);
        return this.setRecordingMetadata(new ConnectionDescriptor(UPLOADS), recordingName, labels);
    }

    public Metadata getMetadata(ConnectionDescriptor connectionDescriptor, String recordingName)
            throws IOException {
        Objects.requireNonNull(connectionDescriptor);
        Objects.requireNonNull(recordingName);

        Metadata metadata = null;

        String jvmId;
        if (connectionDescriptor.getTargetId().equals(UPLOADS)) {
            jvmId = UPLOADS;
        } else {
            jvmId = jvmIdHelper.getJvmId(connectionDescriptor);
        }

        Path metadataPath = getMetadataPath(jvmId, recordingName);
        if (!fs.isRegularFile(metadataPath)) {

            SecurityContext securityContext =
                    discoveryStorage
                            .lookupServiceByTargetId(connectionDescriptor.getTargetId())
                            .map(auth::contextFor)
                            .orElse(SecurityContext.DEFAULT);

            metadata = new Metadata(securityContext, Map.of());
            fs.writeString(metadataPath, gson.toJson(metadata));
        } else {
            metadata = gson.fromJson(fs.readFile(metadataPath), Metadata.class);
        }
        return metadata;
    }

    // Public metadata getter which doesn't rely on target being available
    public Metadata getMetadataFromPathIfExists(String jvmId, String recordingName)
            throws IOException {
        Objects.requireNonNull(jvmId);
        Objects.requireNonNull(recordingName);
        Path metadataPath = getMetadataPath(jvmId, recordingName);
        if (!fs.isRegularFile(metadataPath)) {
            SecurityContext sc =
                    jvmIdHelper
                            .reverseLookup(jvmId)
                            .map(auth::contextFor)
                            .orElse(SecurityContext.DEFAULT);
            Metadata metadata = new Metadata(sc, Map.of());
            fs.writeString(metadataPath, gson.toJson(metadata));
            return metadata;
        }
        return gson.fromJson(fs.readFile(metadataPath), Metadata.class);
    }

    public Metadata deleteRecordingMetadataIfExists(
            ConnectionDescriptor connectionDescriptor, String recordingName) throws IOException {
        Objects.requireNonNull(connectionDescriptor);
        Objects.requireNonNull(recordingName);

        String jvmId = jvmIdHelper.getJvmId(connectionDescriptor);
        return deleteRecordingMetadataIfExists(jvmId, recordingName);
    }

    public Metadata deleteRecordingMetadataIfExists(String jvmId, String recordingName)
            throws IOException {
        Objects.requireNonNull(jvmId);
        Objects.requireNonNull(recordingName);

        Path metadataPath = this.getMetadataPath(jvmId, recordingName);
        if (fs.isRegularFile(metadataPath)) {
            Metadata metadata = gson.fromJson(fs.readFile(metadataPath), Metadata.class);
            if (fs.deleteIfExists(metadataPath)) {
                deleteSubdirectoryIfEmpty(metadataPath.getParent());
            }
            return metadata;
        }
        return null;
    }

    public Future<Metadata> copyMetadataToArchives(
            ConnectionDescriptor connectionDescriptor, String recordingName, String filename)
            throws IOException {
        Objects.requireNonNull(connectionDescriptor);
        Objects.requireNonNull(recordingName);
        Objects.requireNonNull(filename);
        Metadata metadata = this.getMetadata(connectionDescriptor, recordingName);
        return this.setRecordingMetadata(connectionDescriptor, filename, metadata.getLabels());
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

    private void transferMetadataIfRestarted(ConnectionDescriptor cd) {
        try {
            String targetId = cd.getTargetId();
            String newJvmId = jvmIdHelper.getJvmId(targetId);

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
            Path subdirName = subdirectoryPath.getFileName();
            if (subdirName == null) {
                return;
            }
            String oldJvmId =
                    new String(base32.decode(subdirName.toString()), StandardCharsets.UTF_8);

            if (Objects.equals(oldJvmId, newJvmId)) {
                logger.info("Skipping {} metadata transfer: {}", targetId, oldJvmId);
                return;
            }

            logger.info("[{}] Metadata transfer: {} -> {}", targetId, oldJvmId, newJvmId);
            Path oldParent = getMetadataPath(oldJvmId);
            for (String encodedFilename : fs.listDirectoryChildren(oldParent)) {
                try {
                    Path oldMetadataPath = oldParent.resolve(encodedFilename);
                    StoredRecordingMetadata srm =
                            gson.fromJson(
                                    fs.readFile(oldMetadataPath), StoredRecordingMetadata.class);
                    String recordingName = srm.recordingName;
                    StoredRecordingMetadata updatedSrm =
                            StoredRecordingMetadata.of(targetId, newJvmId, recordingName, srm);
                    Path newLocation = getMetadataPath(newJvmId, recordingName);
                    fs.writeString(newLocation, gson.toJson(updatedSrm));

                    fs.deleteIfExists(oldMetadataPath);
                } catch (Exception e) {
                    logger.error("Metadata could not be transferred");
                    logger.error(e);
                }
            }
            if (fs.listDirectoryChildren(oldParent).isEmpty()) {
                fs.deleteIfExists(oldParent);
            }
            logger.info(
                    "[{}] Metadata successfully transferred: {} -> {}",
                    targetId,
                    oldJvmId,
                    newJvmId);
        } catch (IOException e) {
            logger.error("Metadata could not be transferred upon target restart", e);
        }
    }

    private void removeLostTargetMetadata(String jvmId) {
        try {
            for (String encodedFilename : fs.listDirectoryChildren(getMetadataPath(jvmId))) {
                String recordingName =
                        new String(base32.decode(encodedFilename), StandardCharsets.UTF_8);
                try {
                    if (!isArchivedRecording(recordingName)) {
                        deleteRecordingMetadataIfExists(jvmId, recordingName);
                    }
                } catch (IOException e) {
                    logger.error(e);
                }
            }
        } catch (IOException e) {
            logger.error(e);
        }
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

    private boolean targetRecordingExists(ConnectionDescriptor cd, String recordingName) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        try {
            return this.targetConnectionManager
                    .executeConnectedTaskAsync(
                            cd,
                            conn ->
                                    conn.getService().getAvailableRecordings().stream()
                                            .anyMatch(
                                                    r ->
                                                            future.complete(
                                                                    Objects.equals(
                                                                            recordingName,
                                                                            r.getName()))))
                    .get(connectionTimeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException te) {
            logger.warn("Target unreachable {}, msg {}", cd.getTargetId(), te.getMessage());
            return false;
        } catch (Exception e) {
            logger.error(e);
            return false;
        }
    }

    private Path getMetadataPath(String jvmId) throws IOException {
        String subdirectory = jvmIdHelper.jvmIdToSubdirectoryName(jvmId);

        Path parentDir = recordingMetadataDir.resolve(subdirectory);
        if (parentDir == null) {
            throw new IllegalStateException();
        }
        if (!fs.isDirectory(parentDir)) {
            fs.createDirectory(parentDir);
        }
        return parentDir;
    }

    private Path getMetadataPath(String jvmId, String recordingName) throws IOException {
        Path subdirectory = getMetadataPath(jvmId);
        String filename =
                base32.encodeAsString(recordingName.getBytes(StandardCharsets.UTF_8)) + ".json";
        return subdirectory.resolve(filename);
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

    private ConnectionDescriptor getConnectionDescriptorWithCredentials(ServiceRef serviceRef)
            throws JsonSyntaxException, JsonIOException, IOException, ScriptException {
        Credentials credentials = credentialsManager.getCredentials(serviceRef);
        return new ConnectionDescriptor(serviceRef, credentials);
    }

    private ConnectionDescriptor getConnectionDescriptorWithCredentials(String targetId)
            throws JsonSyntaxException, JsonIOException, IOException, ScriptException {
        Credentials credentials = credentialsManager.getCredentialsByTargetId(targetId);
        return new ConnectionDescriptor(targetId, credentials);
    }

    static class StoredRecordingMetadata extends Metadata {
        private final String jvmId;
        private final String recordingName;
        private final String targetId;

        StoredRecordingMetadata(String targetId, String jvmId, String recordingName, Metadata o) {
            super(o);
            this.targetId = targetId;
            this.jvmId = jvmId;
            this.recordingName = recordingName;
        }

        static StoredRecordingMetadata of(
                String targetId, String jvmId, String recordingName, Metadata metadata) {
            return new StoredRecordingMetadata(targetId, jvmId, recordingName, metadata);
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
        /*
         * The main Gson instance ignores transient fields, but the Gson instance used by
         * RecordingMetadataManager is the GSON_INTERNAL which /does/ serialize transient fields.
         * This keeps the securityContext out of API responses but preserves it for internal
         * bookkeeping.
         * */
        protected final transient SecurityContext securityContext;
        protected final Map<String, String> labels;

        public Metadata(Metadata o) {
            this.securityContext = o.securityContext;
            this.labels = new HashMap<>(o.labels);
        }

        public Metadata(SecurityContext securityContext, Map<String, String> labels) {
            this.securityContext = securityContext;
            this.labels = new HashMap<>(labels);
        }

        public SecurityContext getSecurityContext() {
            return securityContext;
        }

        public Map<String, String> getLabels() {
            return new HashMap<>(labels);
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
            return new EqualsBuilder()
                    .append(securityContext, metadata.securityContext)
                    .append(labels, metadata.labels)
                    .build();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder().append(securityContext).append(labels).toHashCode();
        }
    }
}
