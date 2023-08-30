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
package io.cryostat.rules;

import io.cryostat.recordings.RecordingMetadataManager.Metadata;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

// FIXME move to a more appropriate package
public class ArchivedRecordingInfo {

    private final transient String serviceUri;
    private final String downloadUrl;
    private final String name;
    private final String reportUrl;
    private final Metadata metadata;
    private final long size;
    private final long archivedTime;

    public ArchivedRecordingInfo(
            String serviceUri,
            String name,
            String downloadUrl,
            String reportUrl,
            Metadata metadata,
            long size,
            long archivedTime) {
        this.serviceUri = serviceUri;
        this.name = name;
        this.downloadUrl = downloadUrl;
        this.reportUrl = reportUrl;
        this.metadata = metadata;
        this.size = size;
        this.archivedTime = archivedTime;
    }

    public String getServiceUri() {
        return this.serviceUri;
    }

    public String getName() {
        return this.name;
    }

    public String getDownloadUrl() {
        return this.downloadUrl;
    }

    public String getReportUrl() {
        return this.reportUrl;
    }

    public Metadata getMetadata() {
        return this.metadata;
    }

    public long getSize() {
        return this.size;
    }

    public long getArchivedTime() {
        return this.archivedTime;
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }
        if (other == this) {
            return true;
        }
        if (!(other instanceof ArchivedRecordingInfo)) {
            return false;
        }
        ArchivedRecordingInfo ari = (ArchivedRecordingInfo) other;
        return new EqualsBuilder()
                .append(serviceUri, ari.serviceUri)
                .append(name, ari.name)
                .append(downloadUrl, ari.downloadUrl)
                .append(reportUrl, ari.reportUrl)
                .append(metadata, ari.metadata)
                .append(size, ari.size)
                .append(archivedTime, ari.archivedTime)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(serviceUri)
                .append(name)
                .append(downloadUrl)
                .append(reportUrl)
                .append(metadata)
                .append(size)
                .append(archivedTime)
                .hashCode();
    }
}
