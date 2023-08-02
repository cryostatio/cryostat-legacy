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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClassPropertiesLoaderTest {

    ClassPropertiesLoader loader;

    @BeforeEach
    void setup() {
        this.loader = new ClassPropertiesLoader();
    }

    @Test
    void testAsProperties() throws IOException {
        Properties expected = new Properties();
        expected.putAll(
                Map.of(
                        "KEY1", "value1",
                        "KEY2", "value2",
                        "KEY3", "value3",
                        "KEY4", "some,values,list"));
        Properties result = loader.loadProperties(getClass());
        MatcherAssert.assertThat(result, Matchers.equalTo(expected));
    }

    @Test
    void testAsMap() throws IOException {
        Map<String, String> expected =
                Map.of(
                        "KEY1", "value1",
                        "KEY2", "value2",
                        "KEY3", "value3",
                        "KEY4", "some,values,list");
        Map<String, String> result = loader.loadAsMap(getClass());
        MatcherAssert.assertThat(result, Matchers.equalTo(expected));
    }

    @Test
    void throwsIfClassHasNoResourcePropertiesFile() {
        FileNotFoundException fnfe =
                Assertions.assertThrows(
                        FileNotFoundException.class, () -> loader.loadProperties(InnerClass.class));
        MatcherAssert.assertThat(
                fnfe.getMessage(),
                Matchers.equalTo(
                        "io/cryostat/util/resource/ClassPropertiesLoaderTest$InnerClass.properties"));
    }

    static class InnerClass {}
}
