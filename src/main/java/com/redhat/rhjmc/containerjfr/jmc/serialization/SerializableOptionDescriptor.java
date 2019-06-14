package com.redhat.rhjmc.containerjfr.jmc.serialization;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.openjdk.jmc.common.unit.IOptionDescriptor;

public class SerializableOptionDescriptor {

    private String name;
    private String description;
    private String defaultValue;

    public SerializableOptionDescriptor(IOptionDescriptor<?> orig) {
        this.name = orig.getName();
        this.description = orig.getDescription();
        this.defaultValue = orig.getDefault().toString();
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getDefaultValue() {
        return defaultValue;
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