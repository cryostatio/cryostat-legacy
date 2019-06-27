package com.redhat.rhjmc.containerjfr.jmc.serialization;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class SavedRecordingDescriptor {

    private final String name;
    private final String downloadUrl;
    private final String reportUrl;

    public SavedRecordingDescriptor(String name, String downloadUrl, String reportUrl) {
        this.name = name;
        this.downloadUrl = downloadUrl;
        this.reportUrl = reportUrl;
    }

    public String getName() {
        return name;
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