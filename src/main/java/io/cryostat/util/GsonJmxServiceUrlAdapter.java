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

import javax.management.remote.JMXServiceURL;

import io.cryostat.core.log.Logger;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class GsonJmxServiceUrlAdapter extends TypeAdapter<JMXServiceURL> {

    private final Logger logger;

    public GsonJmxServiceUrlAdapter(Logger logger) {
        this.logger = logger;
    }

    @Override
    public JMXServiceURL read(JsonReader reader) throws IOException {
        String url = reader.nextString();
        JMXServiceURL jmxUrl;
        try {
            jmxUrl = new JMXServiceURL(url);
        } catch (Exception e) {
            logger.warn(e);
            jmxUrl = null;
        }
        return jmxUrl;
    }

    @Override
    public void write(JsonWriter writer, JMXServiceURL url) throws IOException {
        writer.value(url.toString());
    }
}
