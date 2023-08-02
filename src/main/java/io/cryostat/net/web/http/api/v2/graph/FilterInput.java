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
package io.cryostat.net.web.http.api.v2.graph;

import java.util.Map;

import graphql.schema.DataFetchingEnvironment;

class FilterInput {

    private static final String FILTER_ARGUMENT = "filter";

    private final Map<String, Object> filter;

    FilterInput(Map<String, Object> map) {
        this.filter = map;
    }

    static FilterInput from(DataFetchingEnvironment env) {
        Map<String, Object> map = env.getArgument(FILTER_ARGUMENT);
        return new FilterInput(map == null ? Map.of() : map);
    }

    boolean contains(Key key) {
        return filter.containsKey(key.key());
    }

    <T> T get(Key key) {
        return (T) filter.get(key.key());
    }

    enum Key {
        ID("id"),
        NAME("name"),
        NAMES("names"),
        LABELS("labels"),
        ANNOTATIONS("annotations"),
        SOURCE_TARGET("sourceTarget"),
        NODE_TYPE("nodeType"),
        STATE("state"),
        CONTINUOUS("continuous"),
        TO_DISK("toDisk"),
        DURATION_GE("durationMsGreaterThanEqual"),
        DURATION_LE("durationMsLessThanEqual"),
        START_TIME_BEFORE("startTimeMsBeforeEqual"),
        START_TIME_AFTER("startTimeMsAfterEqual"),
        SIZE_GE("sizeBytesGreaterThanEqual"),
        SIZE_LE("sizeBytesLessThanEqual"),
        ARCHIVED_TIME_BEFORE("archivedTimeMsBeforeEqual"),
        ARCHIVED_TIME_AFTER("archivedTimeMsAfterEqual");

        private final String key;

        Key(String key) {
            this.key = key;
        }

        String key() {
            return key;
        }
    }
}
