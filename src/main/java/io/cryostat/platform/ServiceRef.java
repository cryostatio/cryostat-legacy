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

    public static final String ANNOTATION_PREFIX = "target.cryostat.io";

    private final @SerializedName("connectUrl") URI serviceUri;
    private final String alias;
    private final Map<String, String> labels = new HashMap<>();
    private final Map<String, String> annotations = new HashMap<>();

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

    public void setAnnotations(Map<String, String> annotations) {
        this.annotations.clear();
        this.annotations.putAll(annotations);
    }

    public Map<String, String> getLabels() {
        return Collections.unmodifiableMap(labels);
    }

    public Map<String, String> getAnnotations() {
        return Collections.unmodifiableMap(annotations);
    }

    // FIXME "annotations" map should be used for ex. Kubernetes Annotations, and these Cryostat
    // "annoations" should be stored separately
    public String addCryostatAnnotation(AnnotationKey key, String value) {
        return annotations.put(prefixKey(key), value);
    }

    public String getCryostatAnnotation(AnnotationKey key) {
        return annotations.get(prefixKey(key));
    }

    private static final String prefixKey(AnnotationKey key) {
        return String.format("%s/%s", ANNOTATION_PREFIX, key.getKey());
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

    public enum AnnotationKey {
        HOST("host"),
        PORT("port"),
        MAIN_CLASS("mainClass"),
        PID("pid"),
        START_TIME("startTime"),
        NAMESPACE("namespace"),
        SERVICE_NAME("serviceName"),
        CONTAINER_NAME("containerName"),
        POD_NAME("podName"),
        ;

        private final String key;

        AnnotationKey(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }
    }
}
