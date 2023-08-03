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

import io.cryostat.platform.internal.KubeApiPlatformClient.KubernetesNodeType;
import io.cryostat.util.PluggableTypeAdapter;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class KubernetesNodeTypeAdapter extends PluggableTypeAdapter<KubernetesNodeType> {

    public KubernetesNodeTypeAdapter() {
        super(KubernetesNodeType.class);
    }

    @Override
    public KubernetesNodeType read(JsonReader reader) throws IOException {
        String token = reader.nextString();
        return KubernetesNodeType.fromKubernetesKind(token);
    }

    @Override
    public void write(JsonWriter writer, KubernetesNodeType nodeType) throws IOException {
        writer.value(nodeType.getKind());
    }
}
