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
package io.cryostat.util;

import java.lang.reflect.Type;
import java.util.function.Function;

import io.cryostat.rules.Rule;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

public class RuleDeserializer implements JsonDeserializer<Rule> {

    @Override
    public Rule deserialize(JsonElement json, Type typeOf, JsonDeserializationContext context)
            throws IllegalArgumentException, JsonSyntaxException {

        JsonObject jsonObject = json.getAsJsonObject();

        String name = Rule.Attribute.NAME.getSerialKey();
        String dirty = jsonObject.get(name).getAsString();
        JsonElement sanitized = JsonParser.parseString(Rule.sanitizeRuleName(dirty));
        jsonObject.add(name, sanitized); // replaces field with sanitized name

        Rule.Builder builder =
                new Rule.Builder()
                        .name(jsonObject.get(Rule.Attribute.NAME.getSerialKey()).getAsString())
                        .targetAlias(
                                jsonObject
                                        .get(Rule.Attribute.TARGET_ALIAS.getSerialKey())
                                        .getAsString())
                        .description(
                                jsonObject
                                        .get(Rule.Attribute.DESCRIPTION.getSerialKey())
                                        .getAsString())
                        .eventSpecifier(
                                jsonObject
                                        .get(Rule.Attribute.EVENT_SPECIFIER.getSerialKey())
                                        .getAsString());
        try {
            builder = setOptionalInt(builder, Rule.Attribute.ARCHIVAL_PERIOD_SECONDS, jsonObject);
            builder = setOptionalInt(builder, Rule.Attribute.PRESERVED_ARCHIVES, jsonObject);
            builder = setOptionalInt(builder, Rule.Attribute.MAX_AGE_SECONDS, jsonObject);
            builder = setOptionalInt(builder, Rule.Attribute.MAX_SIZE_BYTES, jsonObject);
        } catch (IllegalArgumentException iae) {
            throw iae;
        }

        return builder.build();
    }

    private static Rule.Builder setOptionalInt(
            Rule.Builder builder, Rule.Attribute key, JsonObject jsonObject)
            throws IllegalArgumentException {

        if (jsonObject.get(key.getSerialKey()) == null) {
            return builder;
        }

        Function<Integer, Rule.Builder> fn;
        switch (key) {
            case ARCHIVAL_PERIOD_SECONDS:
                fn = builder::archivalPeriodSeconds;
                break;
            case PRESERVED_ARCHIVES:
                fn = builder::preservedArchives;
                break;
            case MAX_AGE_SECONDS:
                fn = builder::maxAgeSeconds;
                break;
            case MAX_SIZE_BYTES:
                fn = builder::maxSizeBytes;
                break;
            default:
                throw new IllegalArgumentException("Unknown key \"" + key + "\"");
        }

        int value;
        String attr = key.getSerialKey();

        try {
            value = jsonObject.get(attr).getAsInt();
        } catch (ClassCastException | IllegalStateException e) {
            throw new IllegalArgumentException(
                    String.format(
                            "\"%s\" is an invalid (non-integer) value for \"%s\"",
                            jsonObject.get(attr), attr),
                    e);
        }

        return fn.apply(value);
    }
}
