package com.redhat.rhjmc.containerjfr.jmc.serialization;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import org.openjdk.jmc.common.unit.QuantityConversionException;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

public class HyperlinkedSerializableRecordingDescriptor extends SerializableRecordingDescriptor {

    private String downloadUrl;
    private String reportUrl;

    public HyperlinkedSerializableRecordingDescriptor(
            IRecordingDescriptor original, String downloadUrl, String reportUrl)
            throws QuantityConversionException {
        super(original);
        this.downloadUrl = downloadUrl;
        this.reportUrl = reportUrl;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public String getReportUrl() {
        return reportUrl;
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }
}
