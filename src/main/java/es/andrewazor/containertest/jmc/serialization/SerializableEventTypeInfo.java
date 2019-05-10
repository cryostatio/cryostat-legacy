package es.andrewazor.containertest.jmc.serialization;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.openjdk.jmc.common.unit.IOptionDescriptor;
import org.openjdk.jmc.rjmx.services.jfr.IEventTypeInfo;

public class SerializableEventTypeInfo {

    private String name;
    private String description;
    private String[] category;
    private Map<String, SerializableOptionDescriptor> options;

    public SerializableEventTypeInfo(IEventTypeInfo orig) {
        this.name = orig.getName();
        this.description = orig.getDescription();
        this.category = orig.getHierarchicalCategory();

        Map<String, ? extends IOptionDescriptor<?>> origOptions = orig.getOptionDescriptors();
        this.options = new HashMap<>(origOptions.size());
        for (Map.Entry<String, ? extends IOptionDescriptor<?>> entry : origOptions.entrySet()) {
            this.options.put(entry.getKey(), new SerializableOptionDescriptor(entry.getValue()));
        }
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String[] getHierarchicalCategory() {
        return Arrays.copyOf(category, category.length);
    }

    public Map<String, SerializableOptionDescriptor> getOptionDescriptors() {
        return options;
    }

}