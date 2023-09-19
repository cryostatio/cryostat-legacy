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
package io.cryostat.jmc.serialization;

import org.openjdk.jmc.common.unit.QuantityConversionException;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor.RecordingState;

import io.cryostat.core.serialization.SerializableRecordingDescriptor;
import io.cryostat.recordings.RecordingMetadataManager.Metadata;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class HyperlinkedSerializableRecordingDescriptor extends SerializableRecordingDescriptor {

    protected String downloadUrl;
    protected String reportUrl;
    protected Metadata metadata;
    protected boolean archiveOnStop;

    public HyperlinkedSerializableRecordingDescriptor(
            IRecordingDescriptor original, String downloadUrl, String reportUrl)
            throws QuantityConversionException {
        super(original);
        this.downloadUrl = downloadUrl;
        this.reportUrl = reportUrl;
        this.metadata = new Metadata();
        this.archiveOnStop = false;
    }

    public HyperlinkedSerializableRecordingDescriptor(
            IRecordingDescriptor original, String downloadUrl, String reportUrl, Metadata metadata)
            throws QuantityConversionException {
        super(original);
        this.downloadUrl = downloadUrl;
        this.reportUrl = reportUrl;
        this.metadata = metadata;
        this.archiveOnStop = false;
    }

    public HyperlinkedSerializableRecordingDescriptor(
            IRecordingDescriptor original,
            String downloadUrl,
            String reportUrl,
            Metadata metadata,
            boolean archiveOnStop)
            throws QuantityConversionException {
        super(original);
        this.downloadUrl = downloadUrl;
        this.reportUrl = reportUrl;
        this.metadata = metadata;
        this.archiveOnStop = archiveOnStop;
    }

    public HyperlinkedSerializableRecordingDescriptor(
            IRecordingDescriptor original,
            String downloadUrl,
            String reportUrl,
            RecordingState state)
            throws QuantityConversionException {
        super(original);
        this.downloadUrl = downloadUrl;
        this.reportUrl = reportUrl;
        this.metadata = new Metadata();
        this.archiveOnStop = false;
        this.state = state;
    }

    public HyperlinkedSerializableRecordingDescriptor(
            SerializableRecordingDescriptor original, String downloadUrl, String reportUrl)
            throws QuantityConversionException {
        super(original);
        this.downloadUrl = downloadUrl;
        this.reportUrl = reportUrl;
        this.metadata = new Metadata();
        this.archiveOnStop = false;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public String getReportUrl() {
        return reportUrl;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public boolean getArchiveOnStop() {
        return archiveOnStop;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }
        if (o == this) {
            return true;
        }
        if (!(o instanceof HyperlinkedSerializableRecordingDescriptor)) {
            return false;
        }

        HyperlinkedSerializableRecordingDescriptor descriptor =
                (HyperlinkedSerializableRecordingDescriptor) o;
        return new EqualsBuilder()
                .append(downloadUrl, descriptor.downloadUrl)
                .append(reportUrl, descriptor.reportUrl)
                .append(metadata, descriptor.metadata)
                .append(archiveOnStop, descriptor.archiveOnStop)
                .build();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(downloadUrl)
                .append(reportUrl)
                .append(metadata)
                .append(archiveOnStop)
                .toHashCode();
    }
}
