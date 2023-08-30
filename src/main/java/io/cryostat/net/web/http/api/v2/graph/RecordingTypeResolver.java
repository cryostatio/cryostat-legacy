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

import javax.inject.Inject;

import io.cryostat.jmc.serialization.HyperlinkedSerializableRecordingDescriptor;
import io.cryostat.rules.ArchivedRecordingInfo;

import graphql.TypeResolutionEnvironment;
import graphql.schema.GraphQLObjectType;

class RecordingTypeResolver extends AbstractTypeResolver {

    @Inject
    RecordingTypeResolver() {}

    @Override
    String typeName() {
        return "Recording";
    }

    @Override
    public GraphQLObjectType getType(TypeResolutionEnvironment env) {
        Object o = env.getObject();
        if (o instanceof HyperlinkedSerializableRecordingDescriptor) {
            return env.getSchema().getObjectType("ActiveRecording");
        } else if (o instanceof ArchivedRecordingInfo) {
            return env.getSchema().getObjectType("ArchivedRecording");
        } else {
            throw new IllegalStateException(o.getClass().getCanonicalName());
        }
    }
}
