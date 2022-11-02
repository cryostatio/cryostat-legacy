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
package io.cryostat.jmc.serialization;

import org.openjdk.jmc.common.unit.QuantityConversionException;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

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
            SerializableRecordingDescriptor original,
            String downloadUrl,
            String reportUrl,
            Metadata metadata)
            throws QuantityConversionException {
        super(original);
        this.downloadUrl = downloadUrl;
        this.reportUrl = reportUrl;
        this.metadata = metadata;
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
