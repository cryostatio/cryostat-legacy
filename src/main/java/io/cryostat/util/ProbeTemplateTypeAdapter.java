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

import java.io.IOException;

import io.cryostat.core.agent.ProbeTemplate;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class ProbeTemplateTypeAdapter extends TypeAdapter<ProbeTemplate> {

    @Override
    public void write(JsonWriter out, ProbeTemplate value) throws IOException {
        out.beginObject();
        out.name("name");
        out.value(value.getFileName());
        out.name("xml");
        out.value(value.serialize().replaceAll("\n", "").replaceAll("\"", ""));
        out.endObject();
    }

    @Override
    public ProbeTemplate read(JsonReader in) throws IOException {
        // Unused, we never read ProbeTemplates from Json, only write them
        return null;
    }
}
