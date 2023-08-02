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
import java.lang.management.MemoryUsage;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class MemoryUsageTypeAdapter extends TypeAdapter<MemoryUsage> {

    @Override
    public MemoryUsage read(JsonReader reader) throws IOException {
        long init = -1;
        long used = 0;
        long committed = 0;
        long max = -1;
        reader.beginObject();
        while (reader.hasNext()) {
            String nextName = reader.nextName();
            switch (nextName) {
                case "init":
                    init = reader.nextLong();
                    break;
                case "used":
                    used = reader.nextLong();
                    break;
                case "committed":
                    committed = reader.nextLong();
                    break;
                case "max":
                    max = reader.nextLong();
                    break;
                default:
                    throw new IOException("Unexpected memory usage field: " + nextName);
            }
        }
        reader.endObject();
        return new MemoryUsage(init, used, committed, max);
    }

    @Override
    public void write(JsonWriter writer, MemoryUsage mu) throws IOException {
        writer.beginObject();
        writer.name("init").value(mu.getInit());
        writer.name("used").value(mu.getUsed());
        writer.name("committed").value(mu.getCommitted());
        writer.name("max").value(mu.getMax());
        writer.endObject();
    }
}
