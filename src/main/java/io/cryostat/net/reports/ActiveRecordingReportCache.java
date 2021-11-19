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
package io.cryostat.net.reports;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.inject.Provider;

import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Scheduler;

class ActiveRecordingReportCache {

    protected final Provider<ReportGeneratorService> reportGeneratorServiceProvider;
    protected final FileSystem fs;
    protected final LoadingCache<RecordingDescriptor, String> cache;
    protected final TargetConnectionManager targetConnectionManager;
    protected final Logger logger;

    ActiveRecordingReportCache(
            Provider<ReportGeneratorService> reportGeneratorServiceProvider,
            FileSystem fs,
            TargetConnectionManager targetConnectionManager,
            Logger logger) {
        this.reportGeneratorServiceProvider = reportGeneratorServiceProvider;
        this.fs = fs;
        this.targetConnectionManager = targetConnectionManager;
        this.logger = logger;

        this.cache =
                Caffeine.newBuilder()
                        .scheduler(Scheduler.systemScheduler())
                        .expireAfterWrite(30, TimeUnit.MINUTES)
                        .refreshAfterWrite(5, TimeUnit.MINUTES)
                        .softValues()
                        .build(k -> getReport(k));
    }

    Future<String> get(ConnectionDescriptor connectionDescriptor, String recordingName) {
        CompletableFuture<String> f = new CompletableFuture<>();
        try {
            f.complete(cache.get(new RecordingDescriptor(connectionDescriptor, recordingName)));
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
        Path saveFile = null;
        try {
            logger.trace("Active report cache miss for {}", recordingDescriptor.recordingName);
            try {
                saveFile = reportGeneratorServiceProvider.get().exec(recordingDescriptor).get();
                return fs.readString(saveFile);
            } catch (ExecutionException | CompletionException ee) {
                logger.error(ee);

                delete(recordingDescriptor.connectionDescriptor, recordingDescriptor.recordingName);

                if (ee.getCause() instanceof SubprocessReportGenerator.ReportGenerationException) {
                    SubprocessReportGenerator.ReportGenerationException generationException =
                            (SubprocessReportGenerator.ReportGenerationException) ee.getCause();

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
                throw ee;
            }
        } finally {
            if (saveFile != null) {
                fs.deleteIfExists(saveFile);
            }
        }
    }
}
