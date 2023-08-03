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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.openjdk.jmc.common.unit.IOptionDescriptor;
import org.openjdk.jmc.rjmx.services.jfr.IEventTypeInfo;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class SerializableEventTypeInfo {

    private String name;
    private String typeId;
    private String description;
    private String[] category;
    private Map<String, SerializableOptionDescriptor> options;

    public SerializableEventTypeInfo(IEventTypeInfo orig) {
        this.name = orig.getName();
        this.typeId = orig.getEventTypeID().getFullKey();
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

    public String getTypeId() {
        return this.typeId;
    }

    public String getDescription() {
        return description;
    }

    public String[] getHierarchicalCategory() {
        return Arrays.copyOf(category, category.length);
    }

    public Map<String, SerializableOptionDescriptor> getOptionDescriptors() {
        return new HashMap<String, SerializableOptionDescriptor>(options);
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
