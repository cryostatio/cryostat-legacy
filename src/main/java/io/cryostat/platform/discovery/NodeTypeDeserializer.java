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
package io.cryostat.platform.discovery;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Set;

import io.cryostat.util.PluggableJsonDeserializer;
import io.cryostat.util.PluggableTypeAdapter;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;
import dagger.Lazy;

public class NodeTypeDeserializer extends PluggableJsonDeserializer<NodeType> {

    private final Lazy<Set<PluggableTypeAdapter<?>>> adapters;

    public NodeTypeDeserializer(Lazy<Set<PluggableTypeAdapter<?>>> adapters) {
        super(NodeType.class);
        this.adapters = adapters;
    }

    @Override
    public NodeType deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        String raw = json.getAsString();
        for (PluggableTypeAdapter<?> adapter : adapters.get()) {
            if (!adapter.getAdaptedType().isAssignableFrom(NodeType.class)
                    && !Arrays.asList(adapter.getAdaptedType().getInterfaces())
                            .contains(NodeType.class)) {
                continue;
            }
            try {
                NodeType nt =
                        (NodeType)
                                adapter.read(
                                        new JsonReader(
                                                new StringReader(String.format("\"%s\"", raw))));
                if (nt != null) {
                    return nt;
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        }
        throw new JsonParseException(raw);
    }
}
