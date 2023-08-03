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
package io.cryostat.discovery;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.cryostat.core.log.Logger;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.ServiceRef.AnnotationKey;
import io.cryostat.platform.discovery.AbstractNode;
import io.cryostat.platform.discovery.EnvironmentNode;
import io.cryostat.platform.discovery.NodeType;
import io.cryostat.platform.discovery.TargetNode;
import io.cryostat.util.PluggableTypeAdapter;

import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import dagger.Lazy;

public class AbstractNodeTypeAdapter extends PluggableTypeAdapter<AbstractNode> {

    private final Lazy<Set<PluggableTypeAdapter<?>>> adapters;
    private final Logger logger;

    public AbstractNodeTypeAdapter(
            Class<AbstractNode> klazz, Lazy<Set<PluggableTypeAdapter<?>>> adapters, Logger logger) {
        super(klazz);
        this.adapters = adapters;
        this.logger = logger;
    }

    @Override
    public AbstractNode read(JsonReader reader) throws IOException {
        AbstractNode node = null;
        reader.beginObject();

        String name = null;
        NodeType nodeType = null;
        Map<String, String> labels = new HashMap<>();
        Set<AbstractNode> children = null;
        ServiceRef target = null;

        while (reader.hasNext()) {
            String tokenName = reader.nextName();
            switch (tokenName) {
                case "id":
                    // ignore this if it's provided. We will (re)calculate the ID on our own.
                    reader.nextInt();
                    break;
                case "name":
                    name = reader.nextString();
                    break;
                case "nodeType":
                    String rawNodeType = reader.nextString();
                    for (PluggableTypeAdapter<?> adapter : adapters.get()) {
                        if (!adapter.getAdaptedType().isAssignableFrom(NodeType.class)
                                && !Arrays.asList(adapter.getAdaptedType().getInterfaces())
                                        .contains(NodeType.class)) {
                            continue;
                        }
                        NodeType nt =
                                (NodeType)
                                        adapter.read(
                                                new JsonReader(
                                                        new StringReader(
                                                                String.format(
                                                                        "\"%s\"", rawNodeType))));
                        if (nt != null) {
                            nodeType = nt;
                            break;
                        }
                    }
                    break;
                case "labels":
                    reader.beginObject();
                    while (reader.hasNext()) {
                        labels.put(reader.nextName(), reader.nextString());
                    }
                    reader.endObject();
                    break;
                case "children":
                    children = new HashSet<>();
                    reader.beginArray();
                    while (reader.hasNext()) {
                        children.add(read(reader));
                    }
                    reader.endArray();
                    break;
                case "target":
                    reader.beginObject();
                    String jvmId = null;
                    URI connectUrl = null;
                    String alias = null;
                    Map<String, String> targetLabels = new HashMap<>();
                    Map<String, String> platformAnnotations = new HashMap<>();
                    Map<AnnotationKey, String> cryostatAnnotations = new HashMap<>();
                    while (reader.hasNext()) {
                        String targetTokenName = reader.nextName();
                        switch (targetTokenName) {
                            case "jvmId":
                                if (reader.peek() == JsonToken.NULL) {
                                    reader.nextNull();
                                } else {
                                    jvmId = reader.nextString();
                                }
                                break;
                            case "connectUrl":
                                try {
                                    connectUrl = new URI(reader.nextString());
                                } catch (URISyntaxException e) {
                                    throw new IllegalArgumentException(e);
                                }
                                break;
                            case "alias":
                                alias = reader.nextString();
                                break;
                            case "labels":
                                reader.beginObject();
                                while (reader.hasNext()) {
                                    targetLabels.put(reader.nextName(), reader.nextString());
                                }
                                reader.endObject();
                                break;
                            case "annotations":
                                reader.beginObject();

                                while (reader.hasNext()) {
                                    String annotationKey = reader.nextName();
                                    switch (annotationKey) {
                                        case "platform":
                                            reader.beginObject();
                                            while (reader.hasNext()) {
                                                platformAnnotations.put(
                                                        reader.nextName(), reader.nextString());
                                            }
                                            reader.endObject();
                                            break;
                                        case "cryostat":
                                            reader.beginObject();
                                            while (reader.hasNext()) {
                                                cryostatAnnotations.put(
                                                        AnnotationKey.valueOf(reader.nextName()),
                                                        reader.nextString());
                                            }
                                            reader.endObject();
                                            break;
                                        default:
                                            logger.warn(
                                                    "Unexpected token {} at {}",
                                                    tokenName,
                                                    reader.getPath());
                                            break;
                                    }
                                }

                                reader.endObject();
                                break;
                        }
                    }
                    reader.endObject();

                    target = new ServiceRef(jvmId, connectUrl, alias);
                    target.setLabels(targetLabels);
                    target.setPlatformAnnotations(platformAnnotations);
                    target.setCryostatAnnotations(cryostatAnnotations);
                    break;
                default:
                    logger.warn("Unexpected token {} at {}", tokenName, reader.getPath());
                    break;
            }
        }

        if (name == null) {
            throw new JsonSyntaxException("name");
        }
        if (nodeType == null) {
            throw new JsonSyntaxException("nodeType");
        }

        if (children != null) {
            node = new EnvironmentNode(name, nodeType, labels);
            ((EnvironmentNode) node).addChildren(children);
        } else if (target != null) {
            node = new TargetNode(nodeType, target, labels);
        } else {
            throw new JsonSyntaxException("no children or target");
        }

        reader.endObject();
        return node;
    }

    @Override
    public void write(JsonWriter writer, AbstractNode node) throws IOException {
        writer.beginObject();

        writer.name("id").value(node.getId());
        writer.name("name").value(node.getName());
        writer.name("nodeType").value(node.getNodeType().getKind());
        writeMap(writer, "labels", node.getLabels());

        if (node instanceof EnvironmentNode) {
            EnvironmentNode en = (EnvironmentNode) node;
            writer.name("children").beginArray();
            for (AbstractNode child : en.getChildren()) {
                write(writer, child);
            }
            writer.endArray();
        } else if (node instanceof TargetNode) {
            TargetNode tn = (TargetNode) node;
            ServiceRef sr = tn.getTarget();

            writer.name("target").beginObject();

            writer.name("jvmId").value(sr.getJvmId());
            writer.name("connectUrl").value(sr.getServiceUri().toString());
            writer.name("alias").value(sr.getAlias().orElse(sr.getServiceUri().toString()));
            writeMap(writer, "labels", sr.getLabels());

            writer.name("annotations").beginObject();
            writeMap(writer, "platform", sr.getPlatformAnnotations());
            writeMap(writer, "cryostat", sr.getCryostatAnnotations());
            writer.endObject();

            writer.endObject();
        }

        writer.endObject();
    }

    private void writeMap(JsonWriter writer, String key, Map<? extends Object, String> labels)
            throws IOException {
        writer.name(key).beginObject();
        for (Map.Entry<? extends Object, String> entry : labels.entrySet()) {
            writer.name(entry.getKey().toString()).value(entry.getValue());
        }
        writer.endObject();
    }
}
