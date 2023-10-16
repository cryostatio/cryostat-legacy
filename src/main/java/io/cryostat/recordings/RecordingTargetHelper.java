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
package io.cryostat.recordings;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor.RecordingState;

import io.cryostat.core.EventOptionsBuilder;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.core.templates.Template;
import io.cryostat.core.templates.TemplateType;
import io.cryostat.jmc.serialization.HyperlinkedSerializableRecordingDescriptor;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.reports.ReportService;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.recordings.RecordingMetadataManager.Metadata;

import dagger.Lazy;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

public class RecordingTargetHelper {

    private static final String CREATION_NOTIFICATION_CATEGORY = "ActiveRecordingCreated";
    public static final String STOP_NOTIFICATION_CATEGORY = "ActiveRecordingStopped";
    private static final String DELETION_NOTIFICATION_CATEGORY = "ActiveRecordingDeleted";
    private static final String SNAPSHOT_CREATION_NOTIFICATION_CATEGORY = "SnapshotCreated";
    private static final String SNAPSHOT_DELETION_NOTIFICATION_CATEGORY = "SnapshotDeleted";

    private static final long TIMESTAMP_DRIFT_SAFEGUARD = 500L;

    private static final Pattern TEMPLATE_PATTERN =
            Pattern.compile("^template=([\\w]+)(?:,type=([\\w]+))?$");

    private static final Pattern SNAPSHOT_NAME_PATTERN = Pattern.compile("^(snapshot\\-)([0-9]+)$");

    private final Vertx vertx;
    private final TargetConnectionManager targetConnectionManager;
    private final Lazy<WebServer> webServer;
    private final EventOptionsBuilder.Factory eventOptionsBuilderFactory;
    private final NotificationFactory notificationFactory;
    private final RecordingOptionsBuilderFactory recordingOptionsBuilderFactory;
    private final ReportService reportService;
    private final RecordingMetadataManager recordingMetadataManager;
    private final Logger logger;
    private final Map<Pair<String, String>, Long> scheduledRecordingTasks;
    private final RecordingArchiveHelper recordingArchiveHelper;

    RecordingTargetHelper(
            Vertx vertx,
            TargetConnectionManager targetConnectionManager,
            Lazy<WebServer> webServer,
            EventOptionsBuilder.Factory eventOptionsBuilderFactory,
            NotificationFactory notificationFactory,
            RecordingOptionsBuilderFactory recordingOptionsBuilderFactory,
            ReportService reportService,
            RecordingMetadataManager recordingMetadataManager,
            RecordingArchiveHelper recordingArchiveHelper,
            Logger logger) {
        this.vertx = vertx;
        this.targetConnectionManager = targetConnectionManager;
        this.webServer = webServer;
        this.eventOptionsBuilderFactory = eventOptionsBuilderFactory;
        this.notificationFactory = notificationFactory;
        this.recordingOptionsBuilderFactory = recordingOptionsBuilderFactory;
        this.reportService = reportService;
        this.recordingMetadataManager = recordingMetadataManager;
        this.recordingArchiveHelper = recordingArchiveHelper;
        this.logger = logger;
        this.scheduledRecordingTasks = new ConcurrentHashMap<>();
    }

    public List<IRecordingDescriptor> getRecordings(ConnectionDescriptor connectionDescriptor)
            throws Exception {
        return targetConnectionManager.executeConnectedTask(
                connectionDescriptor,
                connection -> connection.getService().getAvailableRecordings());
    }

    public enum ReplacementPolicy {
        ALWAYS,
        STOPPED,
        NEVER;

        public static ReplacementPolicy fromString(String value) {
            if (value == null) {
                return NEVER;
            }
            try {
                return valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                return NEVER;
            }
        }
    }

    public IRecordingDescriptor startRecording(
            ReplacementPolicy replace,
            ConnectionDescriptor connectionDescriptor,
            IConstrainedMap<String> recordingOptions,
            String templateName,
            TemplateType templateType,
            Metadata metadata,
            boolean archiveOnStop)
            throws Exception {
        String recordingName = (String) recordingOptions.get(RecordingOptionsBuilder.KEY_NAME);

        return targetConnectionManager.executeConnectedTask(
                connectionDescriptor,
                connection -> {
                    TemplateType preferredTemplateType =
                            getPreferredTemplateType(connection, templateName, templateType);
                    Optional<IRecordingDescriptor> previous =
                            getDescriptorByName(connection, recordingName);
                    if (previous.isPresent()) {
                        if (shouldRestartRecording(replace, previous.get())) {
                            if (isRecordingStopped(previous.get())) {
                                connection.getService().close(previous.get());
                            } else {
                                // If recording exists & running, close it before starting new one
                                connection.getService().close(previous.get());
                            }
                        } else {
                            throw new IllegalArgumentException(
                                    String.format(
                                            "Recording with name \"%s\" already exists",
                                            recordingName));
                        }
                    }
                    IRecordingDescriptor desc =
                            connection
                                    .getService()
                                    .start(recordingOptions, templateName, preferredTemplateType);
                    String targetId = connectionDescriptor.getTargetId();

                    Map<String, String> labels = metadata.getLabels();
                    labels.put("template.name", templateName);
                    labels.put("template.type", preferredTemplateType.name());
                    Metadata updatedMetadata = new Metadata(labels);
                    updatedMetadata =
                            recordingMetadataManager
                                    .setRecordingMetadata(
                                            connectionDescriptor, recordingName, updatedMetadata)
                                    .get();
                    HyperlinkedSerializableRecordingDescriptor linkedDesc =
                            new HyperlinkedSerializableRecordingDescriptor(
                                    desc,
                                    webServer.get().getDownloadURL(connection, desc.getName()),
                                    webServer.get().getReportURL(connection, desc.getName()),
                                    updatedMetadata,
                                    archiveOnStop);
                    this.issueNotification(targetId, linkedDesc, CREATION_NOTIFICATION_CATEGORY);

                    Object fixedDuration =
                            recordingOptions.get(RecordingOptionsBuilder.KEY_DURATION);
                    if (fixedDuration != null) {
                        Long delay =
                                Long.valueOf(fixedDuration.toString().replaceAll("[^0-9]", ""));
                        scheduleRecordingTasks(
                                recordingName, delay, connectionDescriptor, archiveOnStop);
                    }
                    return desc;
                });
    }

    private boolean shouldRestartRecording(
            ReplacementPolicy replace, IRecordingDescriptor descriptor) {
        if (replace != null) {
            switch (replace) {
                case ALWAYS:
                    return true;
                case STOPPED:
                    return descriptor.getState() == IRecordingDescriptor.RecordingState.STOPPED;
                case NEVER:
                    return false;
            }
        }
        // If neither restart nor replace is specified, default to never
        return false;
    }

    private boolean isRecordingStopped(IRecordingDescriptor recording) {
        return recording.getState() == IRecordingDescriptor.RecordingState.STOPPED;
    }

    /**
     * The returned {@link InputStream}, if any, is only readable while the remote connection
     * remains open. And so, {@link
     * TargetConnectionManager#markConnectionInUse(ConnectionDescriptor)} should be used while
     * reading to inform the {@link TargetConnectionManager} that the connection is still in use,
     * thereby avoiding accidental expiration/closing of the connection.
     */
    public Future<Optional<InputStream>> getRecording(
            ConnectionDescriptor connectionDescriptor, String recordingName) {
        CompletableFuture<Optional<InputStream>> future = new CompletableFuture<>();
        try {
            Optional<InputStream> recording =
                    targetConnectionManager.executeConnectedTask(
                            connectionDescriptor,
                            conn ->
                                    getDescriptorByName(conn, recordingName)
                                            .map(
                                                    desc -> {
                                                        try {
                                                            return conn.getService()
                                                                    .openStream(desc, false);
                                                        } catch (Exception e) {
                                                            throw new RuntimeException(e);
                                                        }
                                                    }));
            future.complete(recording);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    public Future<Void> deleteRecording(
            ConnectionDescriptor connectionDescriptor, String recordingName) {
        return this.deleteRecording(connectionDescriptor, recordingName, true);
    }

    public IRecordingDescriptor stopRecording(
            ConnectionDescriptor connectionDescriptor, String recordingName) throws Exception {
        return stopRecording(connectionDescriptor, recordingName, false);
    }

    public IRecordingDescriptor stopRecording(
            ConnectionDescriptor connectionDescriptor, String recordingName, boolean quiet)
            throws Exception {
        String targetId = connectionDescriptor.getTargetId();
        return targetConnectionManager.executeConnectedTask(
                connectionDescriptor,
                connection -> {
                    Optional<IRecordingDescriptor> descriptor =
                            getDescriptorByName(connection, recordingName);
                    if (descriptor.isPresent()) {
                        IRecordingDescriptor d = descriptor.get();
                        if (d.getState().equals(RecordingState.STOPPED) && quiet) {
                            return d;
                        }
                        connection.getService().stop(d);
                        this.cancelScheduledTasksIfExists(targetId, recordingName);
                        HyperlinkedSerializableRecordingDescriptor linkedDesc =
                                new HyperlinkedSerializableRecordingDescriptor(
                                        d,
                                        webServer.get().getDownloadURL(connection, d.getName()),
                                        webServer.get().getReportURL(connection, d.getName()),
                                        RecordingState.STOPPED);
                        this.issueNotification(targetId, linkedDesc, STOP_NOTIFICATION_CATEGORY);
                        return getDescriptorByName(connection, recordingName).get();
                    } else {
                        throw new RecordingNotFoundException(targetId, recordingName);
                    }
                });
    }

    public Future<HyperlinkedSerializableRecordingDescriptor> createSnapshot(
            ConnectionDescriptor connectionDescriptor) {
        CompletableFuture<HyperlinkedSerializableRecordingDescriptor> future =
                new CompletableFuture<>();
        try {
            HyperlinkedSerializableRecordingDescriptor recordingDescriptor =
                    targetConnectionManager.executeConnectedTask(
                            connectionDescriptor,
                            connection -> {
                                IRecordingDescriptor descriptor =
                                        connection.getService().getSnapshotRecording();

                                String rename =
                                        String.format(
                                                "%s-%d",
                                                descriptor.getName().toLowerCase(),
                                                descriptor.getId());

                                RecordingOptionsBuilder recordingOptionsBuilder =
                                        recordingOptionsBuilderFactory.create(
                                                connection.getService());
                                recordingOptionsBuilder.name(rename);

                                connection
                                        .getService()
                                        .updateRecordingOptions(
                                                descriptor, recordingOptionsBuilder.build());

                                Optional<IRecordingDescriptor> updatedDescriptor =
                                        getDescriptorByName(connection, rename);

                                if (updatedDescriptor.isEmpty()) {
                                    throw new SnapshotCreationException(
                                            "The most recent snapshot of the recording cannot be"
                                                    + " found after renaming.");
                                }

                                Metadata metadata =
                                        recordingMetadataManager.getMetadata(
                                                connectionDescriptor, rename);

                                return new HyperlinkedSerializableRecordingDescriptor(
                                        updatedDescriptor.get(),
                                        webServer.get().getDownloadURL(connection, rename),
                                        webServer.get().getReportURL(connection, rename),
                                        metadata,
                                        false);
                            });
            future.complete(recordingDescriptor);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    public Future<Boolean> verifySnapshot(
            ConnectionDescriptor connectionDescriptor,
            HyperlinkedSerializableRecordingDescriptor snapshotDescriptor) {
        return this.verifySnapshot(connectionDescriptor, snapshotDescriptor, true);
    }

    public Future<Boolean> verifySnapshot(
            ConnectionDescriptor connectionDescriptor,
            HyperlinkedSerializableRecordingDescriptor snapshotDescriptor,
            boolean issueNotification) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        try {
            String snapshotName = snapshotDescriptor.getName();
            Optional<InputStream> snapshotOptional =
                    this.getRecording(connectionDescriptor, snapshotName).get();
            if (snapshotOptional.isEmpty()) {
                throw new SnapshotCreationException(
                        "The most recent snapshot of the recording cannot be retrieved for"
                                + " verification.");
            } else {
                try (InputStream snapshot = snapshotOptional.get()) {
                    if (!snapshotIsReadable(connectionDescriptor, snapshot)) {
                        this.deleteRecording(connectionDescriptor, snapshotName, false).get();
                        future.complete(false);
                    } else {
                        if (issueNotification) {
                            this.issueNotification(
                                    connectionDescriptor.getTargetId(),
                                    snapshotDescriptor,
                                    SNAPSHOT_CREATION_NOTIFICATION_CATEGORY);
                        }
                        future.complete(true);
                    }
                }
            }
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    public static Pair<String, TemplateType> parseEventSpecifierToTemplate(String eventSpecifier)
            throws IllegalArgumentException {
        if (TEMPLATE_PATTERN.matcher(eventSpecifier).matches()) {
            Matcher m = TEMPLATE_PATTERN.matcher(eventSpecifier);
            m.find();
            String templateName = m.group(1);
            String typeName = m.group(2);
            TemplateType templateType = null;
            if (StringUtils.isNotBlank(typeName)) {
                templateType = TemplateType.valueOf(typeName.toUpperCase());
            }
            return Pair.of(templateName, templateType);
        }
        throw new IllegalArgumentException(eventSpecifier);
    }

    public Optional<IRecordingDescriptor> getDescriptorByName(
            JFRConnection connection, String recordingName) throws Exception {
        return connection.getService().getAvailableRecordings().stream()
                .filter(recording -> recording.getName().equals(recordingName))
                .findFirst();
    }

    private Future<Void> deleteRecording(
            ConnectionDescriptor connectionDescriptor,
            String recordingName,
            boolean issueNotification) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            String targetId = connectionDescriptor.getTargetId();
            Void v =
                    targetConnectionManager.executeConnectedTask(
                            connectionDescriptor,
                            connection -> {
                                Optional<IRecordingDescriptor> descriptor =
                                        getDescriptorByName(connection, recordingName);
                                if (descriptor.isPresent()) {
                                    IRecordingDescriptor d = descriptor.get();
                                    connection.getService().close(d);
                                    reportService.delete(connectionDescriptor, recordingName);
                                    this.cancelScheduledTasksIfExists(targetId, recordingName);
                                    HyperlinkedSerializableRecordingDescriptor linkedDesc =
                                            new HyperlinkedSerializableRecordingDescriptor(
                                                    d,
                                                    webServer
                                                            .get()
                                                            .getDownloadURL(
                                                                    connection, d.getName()),
                                                    webServer
                                                            .get()
                                                            .getReportURL(connection, d.getName()),
                                                    recordingMetadataManager
                                                            .deleteRecordingMetadataIfExists(
                                                                    connectionDescriptor,
                                                                    recordingName),
                                                    false);
                                    if (issueNotification) {
                                        Matcher m = SNAPSHOT_NAME_PATTERN.matcher(recordingName);
                                        String notificationCategory =
                                                m.matches()
                                                        ? SNAPSHOT_DELETION_NOTIFICATION_CATEGORY
                                                        : DELETION_NOTIFICATION_CATEGORY;
                                        this.issueNotification(
                                                targetId, linkedDesc, notificationCategory);
                                    }
                                } else {
                                    throw new RecordingNotFoundException(targetId, recordingName);
                                }
                                return null;
                            });
            future.complete(v);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    private void issueNotification(
            String targetId,
            HyperlinkedSerializableRecordingDescriptor linkedDesc,
            String notificationCategory) {
        notificationFactory
                .createBuilder()
                .metaCategory(notificationCategory)
                .metaType(HttpMimeType.JSON)
                .message(Map.of("recording", linkedDesc, "target", targetId))
                .build()
                .send();
    }

    private void cancelScheduledTasksIfExists(String targetId, String stoppedRecordingName)
            throws IOException {
        Long id = scheduledRecordingTasks.remove(Pair.of(targetId, stoppedRecordingName));
        if (id != null) {
            this.vertx.cancelTimer(id);
        }
    }

    private TemplateType getPreferredTemplateType(
            JFRConnection connection, String templateName, TemplateType templateType)
            throws Exception {
        if (templateType != null) {
            return templateType;
        }
        if (templateName.equals("ALL")) {
            // special case for the ALL meta-template
            return TemplateType.TARGET;
        }
        List<Template> matchingNameTemplates =
                connection.getTemplateService().getTemplates().stream()
                        .filter(t -> t.getName().equals(templateName))
                        .collect(Collectors.toList());
        boolean custom =
                matchingNameTemplates.stream()
                        .anyMatch(t -> t.getType().equals(TemplateType.CUSTOM));
        if (custom) {
            return TemplateType.CUSTOM;
        }
        boolean target =
                matchingNameTemplates.stream()
                        .anyMatch(t -> t.getType().equals(TemplateType.TARGET));
        if (target) {
            return TemplateType.TARGET;
        }
        throw new IllegalArgumentException(
                String.format("Invalid/unknown event template %s", templateName));
    }

    private void scheduleRecordingTasks(
            String recordingName,
            long delay,
            ConnectionDescriptor connectionDescriptor,
            boolean archiveOnStop) {
        String targetId = connectionDescriptor.getTargetId();

        Handler<Promise<HyperlinkedSerializableRecordingDescriptor>> promiseHandler =
                promise -> {
                    try {
                        HyperlinkedSerializableRecordingDescriptor linkedDesc =
                                targetConnectionManager.executeConnectedTask(
                                        connectionDescriptor,
                                        connection -> {
                                            Optional<IRecordingDescriptor> desc =
                                                    getDescriptorByName(connection, recordingName);

                                            desc =
                                                    desc.stream()
                                                            .filter(
                                                                    r ->
                                                                            r.getState()
                                                                                    .equals(
                                                                                            RecordingState
                                                                                                    .STOPPED))
                                                            .findFirst();
                                            if (desc.isPresent()) {
                                                String name = desc.get().getName();
                                                HyperlinkedSerializableRecordingDescriptor linked =
                                                        new HyperlinkedSerializableRecordingDescriptor(
                                                                desc.get(),
                                                                webServer
                                                                        .get()
                                                                        .getDownloadURL(
                                                                                connection, name),
                                                                webServer
                                                                        .get()
                                                                        .getReportURL(
                                                                                connection, name));
                                                return linked;
                                            }
                                            throw new IllegalStateException(
                                                    String.format(
                                                            "Could not find expected recording"
                                                                    + " named \"%s\" in target %s",
                                                            recordingName, targetId));
                                        });
                        promise.complete(linkedDesc);
                    } catch (Exception e) {
                        promise.fail(e);
                    }
                };
        long task =
                this.vertx.setTimer(
                        delay + TIMESTAMP_DRIFT_SAFEGUARD,
                        id -> {
                            vertx.executeBlocking(
                                    promiseHandler,
                                    false,
                                    result -> {
                                        if (result.failed()) {
                                            return;
                                        }
                                        this.issueNotification(
                                                targetId,
                                                ((HyperlinkedSerializableRecordingDescriptor)
                                                        result.result()),
                                                STOP_NOTIFICATION_CATEGORY);
                                        if (archiveOnStop) {
                                            try {
                                                recordingArchiveHelper
                                                        .saveRecording(
                                                                connectionDescriptor, recordingName)
                                                        .get();
                                            } catch (InterruptedException | ExecutionException e) {
                                                logger.error(
                                                        "Failed to archive the active recording: "
                                                                + e);
                                            }
                                        }
                                    });
                        });

        scheduledRecordingTasks.put(Pair.of(targetId, recordingName), task);
    }

    /**
     * This method will consume the first byte of the {@link InputStream} it is verifying, so
     * verification should only be done if the @param snapshot stream in question will not be used
     * for any other later purpose. Please ensure the stream is closed post-verification.
     */
    private boolean snapshotIsReadable(
            ConnectionDescriptor connectionDescriptor, InputStream snapshot) throws IOException {
        if (!targetConnectionManager.markConnectionInUse(connectionDescriptor)) {
            throw new IOException(
                    "Target connection unexpectedly closed while streaming recording");
        }

        try {
            return snapshot.read() != -1;
        } catch (IOException e) {
            return false;
        }
    }

    public static class SnapshotCreationException extends Exception {
        public SnapshotCreationException(String message) {
            super(message);
        }
    }
}
