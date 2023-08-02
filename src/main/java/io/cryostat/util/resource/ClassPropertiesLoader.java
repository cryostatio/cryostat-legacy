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
package io.cryostat.util.resource;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class ClassPropertiesLoader {

    public Properties loadProperties(Class<?> klazz) throws IOException {
        try (InputStream stream =
                klazz.getResourceAsStream(klazz.getSimpleName() + ".properties")) {
            if (stream == null) {
                throw new FileNotFoundException(
                        klazz.getName().replaceAll("\\.", File.separator) + ".properties");
            }
            Properties props = new Properties();
            props.load(stream);
            return props;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Map<String, String> loadAsMap(Class<?> klazz) throws IOException {
        Map props = loadProperties(klazz);
        return new HashMap<String, String>(props);
    }
}
