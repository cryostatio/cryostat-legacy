package com.redhat.rhjmc.containerjfr.jmc.serialization;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.openjdk.jmc.common.unit.QuantityConversionException;
import org.openjdk.jmc.common.unit.UnitLookup;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor.RecordingState;

public class SerializableRecordingDescriptor {

    private long id;
    private String name;
    private RecordingState state;
    private long startTime;
    private long duration;
    private boolean continuous;
    private boolean toDisk;
    private long maxSize;
    private long maxAge;

    public SerializableRecordingDescriptor(IRecordingDescriptor orig) throws QuantityConversionException {
        this.id = orig.getId();
        this.name = orig.getName();
        this.state = orig.getState();
        this.startTime = orig.getStartTime().longValueIn(UnitLookup.EPOCH_MS);
        this.duration = orig.getDuration().longValueIn(UnitLookup.MILLISECOND);
        this.continuous = orig.isContinuous();
        this.toDisk = orig.getToDisk();
        this.maxSize = orig.getMaxSize().longValueIn(UnitLookup.BYTE);
        this.maxAge = orig.getMaxAge().longValueIn(UnitLookup.MILLISECOND);
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public RecordingState getState() {
        return state;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getDuration() {
        return duration;
    }

    public boolean isContinuous() {
        return continuous;
    }

    public boolean getToDisk() {
        return toDisk;
    }

    public long getMaxSize() {
        return maxSize;
    }

    public long getMaxAge() {
        return maxAge;
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