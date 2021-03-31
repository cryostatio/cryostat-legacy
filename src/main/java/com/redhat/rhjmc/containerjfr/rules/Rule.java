/*-
 * #%L
 * Container JFR
 * %%
 * Copyright (C) 2020 Red Hat, Inc.
 * %%
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
 * #L%
 */
package com.redhat.rhjmc.containerjfr.rules;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class Rule {

    private final String name;
    private final String description;
    // TODO for now, simply allow matching based on target's alias. This should be expanded to allow
    // for different match parameters such as port number, port name, container/pod label, etc.,
    //  and allow wildcards
    private final String targetAlias;
    private final String eventSpecifier;
    private final int archivalPeriodSeconds;
    private final int preservedArchives;
    private final int maxAgeSeconds;
    private final int maxSizeBytes;

    Rule(Builder builder) {
        this.name = sanitizeRuleName(requireNonBlank(builder.name, Attribute.NAME));
        this.description = builder.description == null ? "" : builder.description;
        this.targetAlias = requireNonBlank(builder.targetAlias, Attribute.TARGET_ALIAS);
        this.eventSpecifier = requireNonBlank(builder.eventSpecifier, Attribute.EVENT_SPECIFIER);
        this.archivalPeriodSeconds =
                requireNonNegative(
                        builder.archivalPeriodSeconds, Attribute.ARCHIVAL_PERIOD_SECONDS);
        this.preservedArchives =
                requireNonNegative(builder.preservedArchives, Attribute.PRESERVED_ARCHIVES);
        this.maxAgeSeconds =
                builder.maxAgeSeconds > 0 ? builder.maxAgeSeconds : this.archivalPeriodSeconds;
        this.maxSizeBytes = builder.maxSizeBytes;
    }

    public String getName() {
        return this.name;
    }

    public String getRecordingName() {
        // FIXME do something other than simply prepending "auto_"
        return String.format("auto_%s", this.getName());
    }

    public String getDescription() {
        return this.description;
    }

    public String getTargetAlias() {
        return this.targetAlias;
    }

    public String getEventSpecifier() {
        return this.eventSpecifier;
    }

    public int getArchivalPeriodSeconds() {
        return this.archivalPeriodSeconds;
    }

    public int getPreservedArchives() {
        return this.preservedArchives;
    }

    public int getMaxAgeSeconds() {
        return this.maxAgeSeconds;
    }

    public int getMaxSizeBytes() {
        return this.maxSizeBytes;
    }

    static String sanitizeRuleName(String name) {
        // FIXME this is not robust
        return name.replaceAll("\\s", "_");
    }

    private static String requireNonBlank(String s, Attribute attr) {
        if (StringUtils.isBlank(s)) {
            throw new IllegalArgumentException(
                    String.format("\"%s\" cannot be blank, was \"%s\"", attr, s));
        }
        return s;
    }

    private static int requireNonNegative(int i, Attribute attr) {
        if (i < 0) {
            throw new IllegalArgumentException(
                    String.format("\"%s\" cannot be negative, was \"%d\"", attr, i));
        }
        return i;
    }

    @Override
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }

    public static class Builder {
        private String name;
        private String description;
        private String targetAlias;
        private String eventSpecifier;
        private int archivalPeriodSeconds = 30;
        private int preservedArchives = 1;
        private int maxAgeSeconds = -1;
        private int maxSizeBytes = -1;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder targetAlias(String targetAlias) {
            this.targetAlias = targetAlias;
            return this;
        }

        public Builder eventSpecifier(String eventSpecifier) {
            this.eventSpecifier = eventSpecifier;
            return this;
        }

        public Builder archivalPeriodSeconds(int archivalPeriodSeconds) {
            this.archivalPeriodSeconds = archivalPeriodSeconds;
            return this;
        }

        public Builder preservedArchives(int preservedArchives) {
            this.preservedArchives = preservedArchives;
            return this;
        }

        public Builder maxAgeSeconds(int maxAgeSeconds) {
            this.maxAgeSeconds = maxAgeSeconds;
            return this;
        }

        public Builder maxSizeBytes(int maxSizeBytes) {
            this.maxSizeBytes = maxSizeBytes;
            return this;
        }

        public Rule build() {
            return new Rule(this);
        }
    }

    public enum Attribute {
        NAME("name"),
        DESCRIPTION("description"),
        TARGET_ALIAS("targetAlias"),
        EVENT_SPECIFIER("eventSpecifier"),
        ARCHIVAL_PERIOD_SECONDS("archivalPeriodSeconds"),
        PRESERVED_ARCHIVES("preservedArchives"),
        MAX_AGE_SECONDS("maxAgeSeconds"),
        MAX_SIZE_BYTES("maxSizeBytes"),
        ;

        private final String serialKey;

        Attribute(String serialKey) {
            this.serialKey = serialKey;
        }

        public String getSerialKey() {
            return serialKey;
        }

        @Override
        public String toString() {
            return getSerialKey();
        }
    }
}
