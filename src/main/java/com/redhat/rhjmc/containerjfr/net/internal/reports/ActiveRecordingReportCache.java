/*-
 * #%L
 * Container JFR
 * %%
 * Copyright (C) 2020 Red Hat, Inc.
 * %%
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
 * #L%
 */
package com.redhat.rhjmc.containerjfr.net.internal.reports;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import javax.inject.Named;
import javax.inject.Provider;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Scheduler;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.reports.ReportTransformer;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.net.ConnectionDescriptor;
import com.redhat.rhjmc.containerjfr.net.TargetConnectionManager;
import com.redhat.rhjmc.containerjfr.net.internal.reports.SubprocessReportGenerator.ExitStatus;
import com.redhat.rhjmc.containerjfr.util.JavaProcess;

class ActiveRecordingReportCache {

    protected final TargetConnectionManager targetConnectionManager;
    protected final Provider<Class<? extends SubprocessReportGenerator>> repGenProvider;
    protected final Provider<JavaProcess.Builder> processBuilderProvider;
    protected final Set<ReportTransformer> reportTransformers;
    protected final FileSystem fs;
    protected final ReentrantLock generationLock;
    protected final LoadingCache<RecordingDescriptor, String> cache;
    protected final Logger logger;

    ActiveRecordingReportCache(
            TargetConnectionManager targetConnectionManager,
            Provider<Class<? extends SubprocessReportGenerator>> repGenProvider,
            Provider<JavaProcess.Builder> processBuilderProvider,
            Set<ReportTransformer> reportTransformers,
            FileSystem fs,
            @Named(ReportsModule.REPORT_GENERATION_LOCK) ReentrantLock generationLock,
            Logger logger) {
        this.targetConnectionManager = targetConnectionManager;
        this.repGenProvider = repGenProvider;
        this.processBuilderProvider = processBuilderProvider;
        this.reportTransformers = reportTransformers;
        this.fs = fs;
        this.generationLock = generationLock;
        this.logger = logger;

        this.cache =
                Caffeine.newBuilder()
                        .executor(Executors.newSingleThreadExecutor())
                        .initialCapacity(4)
                        .scheduler(Scheduler.systemScheduler())
                        .expireAfterWrite(30, TimeUnit.MINUTES)
                        .refreshAfterWrite(5, TimeUnit.MINUTES)
                        .softValues()
                        .build(k -> getReport(k));
    }

    String get(ConnectionDescriptor connectionDescriptor, String recordingName) {
        return cache.get(new RecordingDescriptor(connectionDescriptor, recordingName));
    }

    boolean delete(ConnectionDescriptor connectionDescriptor, String recordingName) {
        logger.trace(String.format("Invalidating active report cache for %s", recordingName));
        RecordingDescriptor key = new RecordingDescriptor(connectionDescriptor, recordingName);
        boolean hasKey = cache.asMap().containsKey(key);
        cache.invalidate(key);
        return hasKey;
    }

    protected String getReport(RecordingDescriptor recordingDescriptor) throws Exception {
        Path saveFile = null;
        try {
            generationLock.lock();
            // TODO extract this into FileSystem
            saveFile = Files.createTempFile(null, null);
            fs.writeString(
                    saveFile,
                    SubprocessReportGenerator.serializeTransformersSet(reportTransformers));
            logger.trace(
                    String.format(
                            "Active report cache miss for %s", recordingDescriptor.recordingName));

            Process proc =
                    processBuilderProvider
                            .get()
                            .klazz(repGenProvider.get())
                            .jvmArgs(SubprocessReportGenerator.createJvmArgs(200))
                            .processArgs(
                                    SubprocessReportGenerator.createProcessArgs(
                                            recordingDescriptor.connectionDescriptor,
                                            recordingDescriptor.recordingName,
                                            saveFile))
                            .exec();
            int status = ExitStatus.TERMINATED.code;
            // TODO this timeout should be related to the HTTP response timeout. See
            // https://github.com/rh-jmc-team/container-jfr/issues/288
            if (proc.waitFor(15, TimeUnit.SECONDS)) {
                status = proc.exitValue();
            } else {
                logger.info("SubprocessReportGenerator timed out, terminating");
                proc.destroyForcibly();
            }
            if (status == SubprocessReportGenerator.ExitStatus.OK.code) {
                return fs.readString(saveFile);
            } else {
                ExitStatus es = ExitStatus.TERMINATED;
                for (ExitStatus e : ExitStatus.values()) {
                    if (e.code == status) {
                        es = e;
                        break;
                    }
                }
                throw new SubprocessReportGenerator.ReportGenerationException(es);
            }
        } finally {
            if (saveFile != null) {
                Files.deleteIfExists(saveFile);
            }
            generationLock.unlock();
        }
    }

    static class RecordingDescriptor {
        final ConnectionDescriptor connectionDescriptor;
        final String recordingName;

        RecordingDescriptor(ConnectionDescriptor connectionDescriptor, String recordingName) {
            this.connectionDescriptor = connectionDescriptor;
            this.recordingName = recordingName;
        }

        @Override
        public boolean equals(Object other) {
            if (other == null) {
                return false;
            }
            if (other == this) {
                return true;
            }
            if (!(other instanceof RecordingDescriptor)) {
                return false;
            }
            RecordingDescriptor rd = (RecordingDescriptor) other;
            return new EqualsBuilder()
                    .append(connectionDescriptor, rd.connectionDescriptor)
                    .append(recordingName, rd.recordingName)
                    .isEquals();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder()
                    .append(connectionDescriptor)
                    .append(recordingName)
                    .hashCode();
        }
    }
}
