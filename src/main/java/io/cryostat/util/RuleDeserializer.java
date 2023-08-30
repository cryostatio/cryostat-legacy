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
package io.cryostat.util;

import java.lang.reflect.Type;

import io.cryostat.rules.MatchExpressionValidationException;
import io.cryostat.rules.Rule;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

public class RuleDeserializer implements JsonDeserializer<Rule> {

    @Override
    public Rule deserialize(JsonElement json, Type typeOf, JsonDeserializationContext context)
            throws IllegalArgumentException, JsonSyntaxException {
        JsonObject jsonObject = json.getAsJsonObject();
        try {
            return Rule.Builder.from(jsonObject).build();
        } catch (MatchExpressionValidationException meve) {
            throw new IllegalArgumentException(meve);
        }
    }
}
