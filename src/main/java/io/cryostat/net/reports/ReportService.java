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
import java.util.concurrent.Future;

import io.cryostat.net.ConnectionDescriptor;

public class ReportService {

    private final ActiveRecordingReportCache activeCache;
    private final ArchivedRecordingReportCache archivedCache;

    ReportService(
            ActiveRecordingReportCache activeCache, ArchivedRecordingReportCache archivedCache) {
        this.activeCache = activeCache;
        this.archivedCache = archivedCache;
    }

    public Future<Path> getFromPath(String subdirectoryName, String recordingName, String filter) {
        return archivedCache.getFromPath(subdirectoryName, recordingName, filter);
    }

    public Future<Path> get(String recordingName, String filter) {
        return archivedCache.get(recordingName, filter);
    }

    public Future<Path> get(String sourceTarget, String recordingName, String filter) {
        return archivedCache.get(sourceTarget, recordingName, filter);
    }

    public Future<String> get(
            ConnectionDescriptor connectionDescriptor, String recordingName, String filter) {
        return activeCache.get(connectionDescriptor, recordingName, filter);
    }

    public boolean delete(ConnectionDescriptor connectionDescriptor, String recordingName) {
        return activeCache.delete(connectionDescriptor, recordingName);
    }
}
