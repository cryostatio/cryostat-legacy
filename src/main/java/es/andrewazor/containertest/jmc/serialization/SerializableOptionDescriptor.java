package es.andrewazor.containertest.jmc.serialization;

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

}