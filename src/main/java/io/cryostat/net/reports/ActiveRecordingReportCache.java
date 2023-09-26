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
package io.cryostat.net.reports;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.inject.Named;
import javax.inject.Provider;

import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.jmc.serialization.HyperlinkedSerializableRecordingDescriptor;
import io.cryostat.messaging.notifications.Notification;
import io.cryostat.messaging.notifications.NotificationListener;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.recordings.RecordingTargetHelper;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Scheduler;

class ActiveRecordingReportCache implements NotificationListener<Map<String, Object>> {
    protected final Provider<ReportGeneratorService> reportGeneratorServiceProvider;
    protected final FileSystem fs;
    protected final LoadingCache<RecordingDescriptor, String> cache;
    protected final TargetConnectionManager targetConnectionManager;
    protected final long generationTimeoutSeconds;
    protected final long cacheExpirySeconds;
    protected final long cacheRefreshSeconds;

    protected final Logger logger;

    protected static final String EMPTY_FILTERS = "";

    ActiveRecordingReportCache(
            Provider<ReportGeneratorService> reportGeneratorServiceProvider,
            FileSystem fs,
            TargetConnectionManager targetConnectionManager,
            @Named(ReportsModule.REPORT_GENERATION_TIMEOUT_SECONDS) long generationTimeoutSeconds,
            @Named(ReportsModule.ACTIVE_REPORT_CACHE_EXPIRY_SECONDS) long cacheExpirySeconds,
            @Named(ReportsModule.ACTIVE_REPORT_CACHE_REFRESH_SECONDS) long cacheRefreshSeconds,
            Logger logger) {
        this.reportGeneratorServiceProvider = reportGeneratorServiceProvider;
        this.fs = fs;
        this.targetConnectionManager = targetConnectionManager;
        this.generationTimeoutSeconds = generationTimeoutSeconds;
        this.cacheExpirySeconds = cacheExpirySeconds;
        this.cacheRefreshSeconds = cacheRefreshSeconds;
        this.logger = logger;
        this.cache =
                Caffeine.newBuilder()
                        .scheduler(Scheduler.systemScheduler())
                        .expireAfterWrite(cacheExpirySeconds, TimeUnit.SECONDS)
                        .refreshAfterWrite(cacheRefreshSeconds, TimeUnit.SECONDS)
                        .softValues()
                        .build(this::getReport);
    }

    Future<String> get(
            ConnectionDescriptor connectionDescriptor, String recordingName, String filter) {
        CompletableFuture<String> f = new CompletableFuture<>();
        try {
            if (filter.isBlank()) {
                f.complete(cache.get(new RecordingDescriptor(connectionDescriptor, recordingName)));
            } else {
                f.complete(
                        getReport(
                                new RecordingDescriptor(connectionDescriptor, recordingName),
                                filter));
            }
        } catch (Exception e) {
            f.completeExceptionally(e);
        }
        return f;
    }

    boolean delete(ConnectionDescriptor connectionDescriptor, String recordingName) {
        RecordingDescriptor key = new RecordingDescriptor(connectionDescriptor, recordingName);
        boolean hasKey = cache.asMap().containsKey(key);
        if (hasKey) {
            logger.trace("Invalidated active report cache for {}", recordingName);
            cache.invalidate(key);
        } else {
            logger.trace("No cache entry for {} to invalidate", recordingName);
        }
        return hasKey;
    }

    protected String getReport(RecordingDescriptor recordingDescriptor) throws Exception {
        return getReport(recordingDescriptor, EMPTY_FILTERS);
    }

    protected String getReport(RecordingDescriptor recordingDescriptor, String filter)
            throws Exception {
        Path saveFile = null;
        try {
            /* NOTE: Not always a cache miss since if a filter is specified we do not even check the cache */
            logger.trace("Active report cache miss for {}", recordingDescriptor.recordingName);
            try {
                saveFile =
                        reportGeneratorServiceProvider
                                .get()
                                .exec(recordingDescriptor, filter)
                                .get(generationTimeoutSeconds, TimeUnit.SECONDS);
                return fs.readString(saveFile);
            } catch (ExecutionException | CompletionException e) {
                logger.error(e);

                delete(recordingDescriptor.connectionDescriptor, recordingDescriptor.recordingName);

                if (e.getCause()
                        instanceof SubprocessReportGenerator.SubprocessReportGenerationException) {
                    SubprocessReportGenerator.SubprocessReportGenerationException
                            generationException =
                                    (SubprocessReportGenerator.SubprocessReportGenerationException)
                                            e.getCause();

                    SubprocessReportGenerator.ExitStatus status = generationException.getStatus();
                    if (status == SubprocessReportGenerator.ExitStatus.OUT_OF_MEMORY) {
                        // subprocess OOM'd and therefore most likely did not properly clean up
                        // the cloned recording stream before exiting, so we do it here
                        String cloneName = "Clone of " + recordingDescriptor.recordingName;
                        targetConnectionManager.executeConnectedTask(
                                recordingDescriptor.connectionDescriptor,
                                conn -> {
                                    Optional<IRecordingDescriptor> clone =
                                            conn.getService().getAvailableRecordings().stream()
                                                    .filter(r -> r.getName().equals(cloneName))
                                                    .findFirst();
                                    if (clone.isPresent()) {
                                        conn.getService().close(clone.get());
                                        logger.trace("Cleaned dangling recording {}", cloneName);
                                    }
                                    return null;
                                });
                    }
                }
                throw e;
            }
        } finally {
            if (saveFile != null) {
                fs.deleteIfExists(saveFile);
            }
        }
    }

    @Override
    public void onNotification(Notification<Map<String, Object>> notification) {
        String category = notification.getCategory();
        switch (category) {
            case RecordingTargetHelper.STOP_NOTIFICATION_CATEGORY:
                String targetId = notification.getMessage().get("target").toString();
                String recordingName =
                        ((HyperlinkedSerializableRecordingDescriptor)
                                        notification.getMessage().get("recording"))
                                .getName();
                delete(new ConnectionDescriptor(targetId), recordingName);
                break;
            default:
                break;
        }
    }
}
