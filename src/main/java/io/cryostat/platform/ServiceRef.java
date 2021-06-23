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
package io.cryostat.platform;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class ServiceRef {

    private final @SerializedName("connectUrl") URI serviceUri;
    private final String alias;
    private final Map<String, String> labels = new HashMap<>();
    private final Annotations annotations = new Annotations();

    public ServiceRef(URI uri, String alias) {
        this.serviceUri = uri;
        this.alias = alias;
    }

    public URI getServiceUri() {
        return serviceUri;
    }

    public Optional<String> getAlias() {
        return Optional.ofNullable(alias);
    }

    public void setLabels(Map<String, String> labels) {
        this.labels.clear();
        this.labels.putAll(labels);
    }

    public Map<String, String> getLabels() {
        return Collections.unmodifiableMap(labels);
    }

    public void setPlatformAnnotations(Map<String, String> annotations) {
        this.annotations.platform.clear();
        this.annotations.platform.putAll(annotations);
    }

    public Map<String, String> getPlatformAnnotations() {
        return new HashMap<>(annotations.platform);
    }

    public void setCryostatAnnotations(Map<AnnotationKey, String> annotations) {
        this.annotations.cryostat.clear();
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
                .append(serviceUri, sr.serviceUri)
                .append(alias, sr.alias)
                .append(labels, sr.labels)
                .append(annotations, sr.annotations)
                .build();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
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
        private final Map<String, String> platform = new HashMap<>();
        private final Map<AnnotationKey, String> cryostat = new HashMap<>();

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
        ;
    }
}
