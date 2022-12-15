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
package io.cryostat.rules;

import static io.cryostat.util.StringUtil.requireNonBlank;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
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

    private final List<String> contexts;
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
        this.contexts = builder.contexts;
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

    public List<String> getContext() {
        return new ArrayList<>(contexts);
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
            // requireNonBlank(this.context, Attribute.CONTEXT.getSerialKey());
        } else {
            requireNonBlank(this.name, Attribute.NAME.getSerialKey());
            requireNonNegative(this.archivalPeriodSeconds, Attribute.ARCHIVAL_PERIOD_SECONDS);
            requireNonNegative(this.initialDelaySeconds, Attribute.INITIAL_DELAY_SECONDS);
            requireNonNegative(this.preservedArchives, Attribute.PRESERVED_ARCHIVES);
            // requireNonBlank(this.context, Attribute.CONTEXT.getSerialKey());
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
    public boolean equals(Object o) {
        return EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
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
        private List<String> contexts = new ArrayList<>();

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

        public Builder context(String context) {
            this.contexts.add(context);
            return this;
        }

        public Builder contexts(Collection<String> contexts) {
            this.contexts.addAll(contexts);
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
                                                    Rule.Attribute.ENABLED.getSerialKey())))
                            .contexts(
                                    Arrays.asList(
                                            formAttributes
                                                    .get(Rule.Attribute.CONTEXTS.getSerialKey())
                                                    .split(",")));

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
                                            getAsNullableString(jsonObj, Rule.Attribute.ENABLED)))
                            .contexts(getStringList(jsonObj, Rule.Attribute.CONTEXTS));

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

        private static List<String> getStringList(JsonObject jsonObj, Rule.Attribute attr) {
            JsonElement el = jsonObj.get(attr.getSerialKey());
            if (el == null) {
                return List.of();
            }
            return el.getAsJsonArray().asList().stream().map(JsonElement::getAsString).toList();
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
        ENABLED("enabled"),
        CONTEXTS("contexts");

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
