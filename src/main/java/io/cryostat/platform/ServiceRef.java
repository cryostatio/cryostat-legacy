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
package io.cryostat.platform;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class ServiceRef {

    private final String jvmId;
    private final @SerializedName("connectUrl") URI serviceUri;
    private final String alias;
    private final Map<String, String> labels;
    private final Annotations annotations;

    public ServiceRef(String jvmId, URI uri, String alias) {
        this.jvmId = jvmId;
        this.serviceUri = Objects.requireNonNull(uri);
        this.alias = alias;
        this.labels = new HashMap<>();
        this.annotations = new Annotations();
    }

    public ServiceRef(ServiceRef sr) {
        this.jvmId = sr.jvmId;
        this.serviceUri = sr.serviceUri;
        this.alias = sr.alias;
        this.labels = new HashMap<String, String>(sr.labels);
        this.annotations = new Annotations(sr.annotations);
    }

    public String getJvmId() {
        return jvmId;
    }

    public URI getServiceUri() {
        return serviceUri;
    }

    public Optional<String> getAlias() {
        return Optional.ofNullable(alias);
    }

    public void setLabels(Map<String, String> labels) {
        this.labels.clear();
        if (labels == null) {
            labels = Map.of();
        }
        this.labels.putAll(labels);
    }

    public Map<String, String> getLabels() {
        return Collections.unmodifiableMap(labels);
    }

    public void setPlatformAnnotations(Map<String, String> annotations) {
        this.annotations.platform.clear();
        if (annotations == null) {
            annotations = Map.of();
        }
        this.annotations.platform.putAll(annotations);
    }

    public Map<String, String> getPlatformAnnotations() {
        return new HashMap<>(annotations.platform);
    }

    public void setCryostatAnnotations(Map<AnnotationKey, String> annotations) {
        this.annotations.cryostat.clear();
        if (annotations == null) {
            annotations = Map.of();
        }
        this.annotations.cryostat.putAll(annotations);
    }

    public Map<AnnotationKey, String> getCryostatAnnotations() {
        return new HashMap<>(annotations.cryostat);
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }
        if (other == this) {
            return true;
        }
        if (!(other instanceof ServiceRef)) {
            return false;
        }
        ServiceRef sr = (ServiceRef) other;
        return new EqualsBuilder()
                .append(jvmId, sr.jvmId)
                .append(serviceUri, sr.serviceUri)
                .append(alias, sr.alias)
                .append(labels, sr.labels)
                .append(annotations, sr.annotations)
                .build();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(jvmId)
                .append(serviceUri)
                .append(alias)
                .append(labels)
                .append(annotations)
                .toHashCode();
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this);
    }

    private static class Annotations {
        private final Map<String, String> platform;
        private final Map<AnnotationKey, String> cryostat;

        public Annotations() {
            this.platform = new HashMap<>();
            this.cryostat = new EnumMap<>(AnnotationKey.class);
        }

        public Annotations(Annotations a) {
            this.platform = new HashMap<String, String>(a.platform);
            this.cryostat =
                    a.cryostat.isEmpty()
                            ? (new EnumMap<>(AnnotationKey.class))
                            : (new EnumMap<AnnotationKey, String>(a.cryostat));
        }

        @Override
        public boolean equals(Object other) {
            if (other == null) {
                return false;
            }
            if (other == this) {
                return true;
            }
            if (!(other instanceof Annotations)) {
                return false;
            }
            Annotations o = (Annotations) other;
            return new EqualsBuilder()
                    .append(platform, o.platform)
                    .append(cryostat, o.cryostat)
                    .build();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder().append(platform).append(cryostat).toHashCode();
        }

        @Override
        public String toString() {
            return ToStringBuilder.reflectionToString(this);
        }
    }

    public enum AnnotationKey {
        HOST,
        PORT,
        JAVA_MAIN,
        PID,
        START_TIME,
        NAMESPACE,
        SERVICE_NAME,
        CONTAINER_NAME,
        POD_NAME,
        REALM,
        ;
    }

    public static Compare compare(Collection<ServiceRef> src) {
        return new Compare(src);
    }

    public static class Compare {
        private Collection<ServiceRef> previous, current;

        public Compare(Collection<ServiceRef> previous) {
            this.previous = new HashSet<>(previous);
        }

        public Compare to(Collection<ServiceRef> current) {
            this.current = new HashSet<>(current);
            return this;
        }

        public Collection<ServiceRef> added() {
            return removeAllUpdatedRefs(addedOrUpdatedRefs(), updated());
        }

        public Collection<ServiceRef> removed() {
            return removeAllUpdatedRefs(removedOrUpdatedRefs(), updated());
        }

        public Collection<ServiceRef> updated() {
            Collection<ServiceRef> updated = new HashSet<>();
            intersection(removedOrUpdatedRefs(), addedOrUpdatedRefs(), false)
                    .forEach((ref) -> updated.add(ref));
            return updated;
        }

        private Collection<ServiceRef> addedOrUpdatedRefs() {
            Collection<ServiceRef> added = new HashSet<>(current);
            added.removeAll(previous);
            return added;
        }

        private Collection<ServiceRef> removedOrUpdatedRefs() {
            Collection<ServiceRef> removed = new HashSet<>(previous);
            removed.removeAll(current);
            return removed;
        }

        private Collection<ServiceRef> removeAllUpdatedRefs(
                Collection<ServiceRef> src, Collection<ServiceRef> updated) {
            Collection<ServiceRef> tnSet = new HashSet<>(src);
            intersection(src, updated, true).stream().forEach((ref) -> tnSet.remove(ref));
            return tnSet;
        }

        private Collection<ServiceRef> intersection(
                Collection<ServiceRef> src, Collection<ServiceRef> other, boolean keepOld) {
            final Collection<ServiceRef> intersection = new HashSet<>();

            // Manual removal since ServiceRef also compares jvmId
            for (ServiceRef srcRef : src) {
                for (ServiceRef otherRef : other) {
                    if (Objects.equals(srcRef.getServiceUri(), otherRef.getServiceUri())) {
                        intersection.add(keepOld ? srcRef : otherRef);
                    }
                }
            }

            return intersection;
        }
    }
}
