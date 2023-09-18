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
package io.cryostat.rules;

import static io.cryostat.util.StringUtil.requireNonBlank;

import java.util.function.Function;

import io.cryostat.recordings.RecordingTargetHelper;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.vertx.core.MultiMap;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class Rule {

    private static final MatchExpressionValidator MATCH_EXPRESSION_VALIDATOR =
            new MatchExpressionValidator();

    static final String ARCHIVE_EVENT = "archive";

    private final String name;
    private final String description;
    private final String matchExpression;
    private final String eventSpecifier;
    private final int archivalPeriodSeconds;
    private final int initialDelaySeconds;
    private final int preservedArchives;
    private final int maxAgeSeconds;
    private final int maxSizeBytes;
    private boolean enabled;

    Rule(Builder builder) throws MatchExpressionValidationException {
        this.eventSpecifier = builder.eventSpecifier;
        if (isArchiver()) {
            this.name = builder.name;
        } else {
            this.name =
                    sanitizeRuleName(requireNonBlank(builder.name, Attribute.NAME.getSerialKey()));
        }
        this.description = builder.description == null ? "" : builder.description;
        this.matchExpression = builder.matchExpression;
        this.archivalPeriodSeconds = builder.archivalPeriodSeconds;
        this.initialDelaySeconds =
                builder.initialDelaySeconds <= 0
                        ? builder.archivalPeriodSeconds
                        : builder.initialDelaySeconds;
        int preservedArchives = builder.preservedArchives;
        // specifically allow the case where the rule only defines an initialDelay but no ongoing,
        // repeated archivalPeriod - a rule that only copies to archives once, ex. on target
        // application startup or shortly thereafter
        if (archivalPeriodSeconds <= 0 && initialDelaySeconds > 0 && preservedArchives <= 0) {
            preservedArchives = 1;
        }
        this.preservedArchives = preservedArchives;
        this.maxAgeSeconds =
                builder.maxAgeSeconds > 0 ? builder.maxAgeSeconds : this.archivalPeriodSeconds;
        this.maxSizeBytes = builder.maxSizeBytes;
        this.enabled = builder.enabled;
        this.validate();
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

    public String getMatchExpression() {
        return this.matchExpression;
    }

    public String getEventSpecifier() {
        return this.eventSpecifier;
    }

    public boolean isArchiver() {
        return ARCHIVE_EVENT.equals(getEventSpecifier());
    }

    public int getArchivalPeriodSeconds() {
        return this.archivalPeriodSeconds;
    }

    public int getInitialDelaySeconds() {
        return this.initialDelaySeconds;
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

    public boolean isEnabled() {
        return enabled;
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public static String sanitizeRuleName(String name) {
        // FIXME this is not robust
        return name.replaceAll("\\s", "_");
    }

    public void validate() throws IllegalArgumentException, MatchExpressionValidationException {
        requireNonBlank(this.matchExpression, Attribute.MATCH_EXPRESSION.getSerialKey());
        validateEventSpecifier(
                requireNonBlank(this.eventSpecifier, Attribute.EVENT_SPECIFIER.getSerialKey()));
        validateMatchExpression(this);

        if (isArchiver()) {
            requireNonPositive(this.archivalPeriodSeconds, Attribute.ARCHIVAL_PERIOD_SECONDS);
            requireNonPositive(this.initialDelaySeconds, Attribute.INITIAL_DELAY_SECONDS);
            requireNonPositive(this.preservedArchives, Attribute.PRESERVED_ARCHIVES);
            requireNonPositive(this.maxSizeBytes, Attribute.MAX_SIZE_BYTES);
            requireNonPositive(this.maxAgeSeconds, Attribute.MAX_AGE_SECONDS);
        } else {
            requireNonBlank(this.name, Attribute.NAME.getSerialKey());
            requireNonNegative(this.archivalPeriodSeconds, Attribute.ARCHIVAL_PERIOD_SECONDS);
            requireNonNegative(this.initialDelaySeconds, Attribute.INITIAL_DELAY_SECONDS);
            requireNonNegative(this.preservedArchives, Attribute.PRESERVED_ARCHIVES);
        }
    }

    private static String validateMatchExpression(Rule rule)
            throws MatchExpressionValidationException {
        return MATCH_EXPRESSION_VALIDATOR.validate(rule);
    }

    private static int requireNonNegative(int i, Attribute attr) {
        if (i < 0) {
            throw new IllegalArgumentException(
                    String.format("\"%s\" cannot be negative, was \"%d\"", attr, i));
        }
        return i;
    }

    private static int requireNonPositive(int i, Attribute attr) {
        if (i > 0) {
            throw new IllegalArgumentException(
                    String.format("\"%s\" cannot be positive, was \"%d\"", attr, i));
        }
        return i;
    }

    private static String validateEventSpecifier(String eventSpecifier)
            throws IllegalArgumentException {
        if (eventSpecifier.equals(ARCHIVE_EVENT)) {
            return eventSpecifier;
        }
        // throws if cannot be parsed
        RecordingTargetHelper.parseEventSpecifierToTemplate(eventSpecifier);
        return eventSpecifier;
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }
        if (other == this) {
            return true;
        }
        if (!(other instanceof Rule)) {
            return false;
        }
        Rule r = (Rule) other;
        // ignore the enabled state
        return new EqualsBuilder()
                .append(name, r.name)
                .append(description, r.description)
                .append(matchExpression, r.matchExpression)
                .append(eventSpecifier, r.eventSpecifier)
                .append(archivalPeriodSeconds, r.archivalPeriodSeconds)
                .append(initialDelaySeconds, r.initialDelaySeconds)
                .append(preservedArchives, r.preservedArchives)
                .append(maxAgeSeconds, r.maxAgeSeconds)
                .append(maxSizeBytes, r.maxSizeBytes)
                .build();
    }

    @Override
    public int hashCode() {
        // ignore the enabled state
        return new HashCodeBuilder()
                .append(name)
                .append(description)
                .append(matchExpression)
                .append(eventSpecifier)
                .append(archivalPeriodSeconds)
                .append(initialDelaySeconds)
                .append(preservedArchives)
                .append(maxAgeSeconds)
                .append(maxSizeBytes)
                .toHashCode();
    }

    public static class Builder {
        private String name = "";
        private String description = "";
        private String matchExpression = "";
        private String eventSpecifier = "";
        private int archivalPeriodSeconds = 0;
        private int initialDelaySeconds = 0;
        private int preservedArchives = 0;
        private int maxAgeSeconds = -1;
        private int maxSizeBytes = -1;
        private boolean enabled = true;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder matchExpression(String matchExpression) {
            this.matchExpression = matchExpression;
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

        public Builder initialDelaySeconds(int initialDelaySeconds) {
            this.initialDelaySeconds = initialDelaySeconds;
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

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Rule build() throws MatchExpressionValidationException {
            return new Rule(this);
        }

        public static Builder from(MultiMap formAttributes) {
            Rule.Builder builder =
                    new Rule.Builder()
                            .name(formAttributes.get(Rule.Attribute.NAME.getSerialKey()))
                            .matchExpression(
                                    formAttributes.get(
                                            Rule.Attribute.MATCH_EXPRESSION.getSerialKey()))
                            .description(
                                    formAttributes.get(Rule.Attribute.DESCRIPTION.getSerialKey()))
                            .eventSpecifier(
                                    formAttributes.get(
                                            Rule.Attribute.EVENT_SPECIFIER.getSerialKey()))
                            .enabled(
                                    getBoolean(
                                            formAttributes.get(
                                                    Rule.Attribute.ENABLED.getSerialKey())));

            builder.setOptionalInt(Rule.Attribute.ARCHIVAL_PERIOD_SECONDS, formAttributes);
            builder.setOptionalInt(Rule.Attribute.INITIAL_DELAY_SECONDS, formAttributes);
            builder.setOptionalInt(Rule.Attribute.PRESERVED_ARCHIVES, formAttributes);
            builder.setOptionalInt(Rule.Attribute.MAX_AGE_SECONDS, formAttributes);
            builder.setOptionalInt(Rule.Attribute.MAX_SIZE_BYTES, formAttributes);

            return builder;
        }

        public static Builder from(JsonObject jsonObj) throws IllegalArgumentException {
            Rule.Builder builder =
                    new Rule.Builder()
                            .name(getAsNullableString(jsonObj, Rule.Attribute.NAME))
                            .matchExpression(
                                    jsonObj.get(Rule.Attribute.MATCH_EXPRESSION.getSerialKey())
                                            .getAsString())
                            .description(getAsNullableString(jsonObj, Rule.Attribute.DESCRIPTION))
                            .eventSpecifier(
                                    jsonObj.get(Rule.Attribute.EVENT_SPECIFIER.getSerialKey())
                                            .getAsString())
                            .enabled(
                                    getBoolean(
                                            getAsNullableString(jsonObj, Rule.Attribute.ENABLED)));

            builder.setOptionalInt(Rule.Attribute.ARCHIVAL_PERIOD_SECONDS, jsonObj);
            builder.setOptionalInt(Rule.Attribute.INITIAL_DELAY_SECONDS, jsonObj);
            builder.setOptionalInt(Rule.Attribute.PRESERVED_ARCHIVES, jsonObj);
            builder.setOptionalInt(Rule.Attribute.MAX_AGE_SECONDS, jsonObj);
            builder.setOptionalInt(Rule.Attribute.MAX_SIZE_BYTES, jsonObj);

            return builder;
        }

        private static boolean getBoolean(String enabled) {
            if (enabled == null) {
                return true;
            }
            return Boolean.parseBoolean(enabled);
        }

        private static String getAsNullableString(JsonObject jsonObj, Rule.Attribute attr) {
            JsonElement el = jsonObj.get(attr.getSerialKey());
            if (el == null) {
                return null;
            }
            return el.getAsString();
        }

        private Builder setOptionalInt(Rule.Attribute key, MultiMap formAttributes)
                throws IllegalArgumentException {

            if (!formAttributes.contains(key.getSerialKey())) {
                return this;
            }

            Function<Integer, Rule.Builder> fn = this.selectAttribute(key);

            int value;
            try {
                value = Integer.parseInt(formAttributes.get(key.getSerialKey()));
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException(
                        String.format(
                                "\"%s\" is an invalid (non-integer) value for \"%s\"",
                                formAttributes.get(key.getSerialKey()), key),
                        nfe);
            }
            return fn.apply(value);
        }

        private Builder setOptionalInt(Rule.Attribute key, JsonObject jsonObj)
                throws IllegalArgumentException {

            if (jsonObj.get(key.getSerialKey()) == null) {
                return this;
            }

            Function<Integer, Rule.Builder> fn = this.selectAttribute(key);

            int value;
            String attr = key.getSerialKey();

            try {
                value = jsonObj.get(attr).getAsInt();
            } catch (ClassCastException | IllegalStateException e) {
                throw new IllegalArgumentException(
                        String.format(
                                "\"%s\" is an invalid (non-integer) value for \"%s\"",
                                jsonObj.get(attr), attr),
                        e);
            }
            return fn.apply(value);
        }

        private Function<Integer, Rule.Builder> selectAttribute(Rule.Attribute key)
                throws IllegalArgumentException {

            Function<Integer, Rule.Builder> fn;

            switch (key) {
                case ARCHIVAL_PERIOD_SECONDS:
                    fn = this::archivalPeriodSeconds;
                    break;
                case INITIAL_DELAY_SECONDS:
                    fn = this::initialDelaySeconds;
                    break;
                case PRESERVED_ARCHIVES:
                    fn = this::preservedArchives;
                    break;
                case MAX_AGE_SECONDS:
                    fn = this::maxAgeSeconds;
                    break;
                case MAX_SIZE_BYTES:
                    fn = this::maxSizeBytes;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown key \"" + key + "\"");
            }

            return fn;
        }
    }

    public enum Attribute {
        NAME("name"),
        DESCRIPTION("description"),
        MATCH_EXPRESSION("matchExpression"),
        EVENT_SPECIFIER("eventSpecifier"),
        ARCHIVAL_PERIOD_SECONDS("archivalPeriodSeconds"),
        INITIAL_DELAY_SECONDS("initialDelaySeconds"),
        PRESERVED_ARCHIVES("preservedArchives"),
        MAX_AGE_SECONDS("maxAgeSeconds"),
        MAX_SIZE_BYTES("maxSizeBytes"),
        ENABLED("enabled");

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
