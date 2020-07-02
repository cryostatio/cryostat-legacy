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

import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import javax.inject.Named;

import org.apache.commons.lang3.tuple.Pair;

import org.openjdk.jmc.rjmx.services.jfr.FlightRecorderException;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Scheduler;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.reports.ReportGenerator;
import com.redhat.rhjmc.containerjfr.net.TargetConnectionManager;
import com.redhat.rhjmc.containerjfr.net.internal.reports.ReportService.RecordingNotFoundException;

class ActiveRecordingReportCache {

    protected final TargetConnectionManager targetConnectionManager;
    protected final ReportGenerator reportGenerator;
    protected final ReentrantLock generationLock;
    protected final LoadingCache<String, String> cache;
    protected final Logger logger;

    ActiveRecordingReportCache(
            TargetConnectionManager targetConnectionManager,
            ReportGenerator reportGenerator,
            @Named(ReportsModule.REPORT_GENERATION_LOCK) ReentrantLock generationLock,
            Logger logger) {
        this.targetConnectionManager = targetConnectionManager;
        this.reportGenerator = reportGenerator;
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
                        .build(k -> getReport(keyParts(k)));
    }

    String get(String targetId, String recordingName) {
        return cache.get(key(targetId, recordingName));
    }

    boolean delete(String targetId, String recordingName) {
        logger.trace(String.format("Invalidating active report cache for %s", recordingName));
        String key = key(targetId, recordingName);
        boolean hasKey = cache.asMap().containsKey(key);
        cache.invalidate(key);
        return hasKey;
    }

    protected String key(String targetId, String recordingName) {
        return targetId + "@" + recordingName;
    }

    protected String key(Pair<String, String> key) {
        return key(key.getLeft(), key.getRight());
    }

    protected Pair<String, String> keyParts(String key) {
        // TODO validate key
        String[] parts = key.split("@");
        return Pair.of(parts[0], parts[1]);
    }

    protected String getReport(Pair<String, String> key) throws Exception {
        try {
            generationLock.lock();
            Pair<Optional<InputStream>, JFRConnection> pair = getRecordingStream(key);
            try (JFRConnection c = pair.getRight();
                    InputStream stream =
                            pair.getLeft().orElseThrow(() -> new RecordingNotFoundException(key))) {
                logger.trace(String.format("Active report cache miss for %s", key));
                return reportGenerator.generateReport(stream);
            }
        } finally {
            generationLock.unlock();
        }
    }

    protected Pair<Optional<InputStream>, JFRConnection> getRecordingStream(
            Pair<String, String> key) throws Exception {
        JFRConnection connection = targetConnectionManager.connect(key.getLeft());
        Optional<InputStream> desc =
                connection.getService().getAvailableRecordings().stream()
                        .filter(rec -> Objects.equals(key.getRight(), rec.getName()))
                        .findFirst()
                        .flatMap(
                                rec -> {
                                    try {
                                        return Optional.of(
                                                connection.getService().openStream(rec, false));
                                    } catch (FlightRecorderException e) {
                                        logger.warn(e);
                                        return Optional.empty();
                                    }
                                });
        return Pair.of(desc, connection);
    }
}
