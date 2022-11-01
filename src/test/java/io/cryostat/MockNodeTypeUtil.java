/*
 * Copyright The Cryostat Authors
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software (each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.cryostat;

import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.ServiceRef.AnnotationKey;
import io.cryostat.platform.discovery.AbstractNode;
import io.cryostat.platform.discovery.EnvironmentNode;
import io.cryostat.platform.discovery.NodeType;
import io.cryostat.platform.discovery.PlatformDiscoveryModule;
import io.cryostat.platform.discovery.TargetNode;
import io.cryostat.util.PluggableJsonDeserializer;
import io.cryostat.util.PluggableTypeAdapter;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

public class MockNodeTypeUtil {
    private static final PluggableTypeAdapter<?> adapter =
            PlatformDiscoveryModule.provideBaseNodeTypeAdapter();

    public static class MockNodeTypeDeserializer extends PluggableJsonDeserializer<NodeType> {
        private final Set<PluggableTypeAdapter<?>> adapters;

        public MockNodeTypeDeserializer(Set<PluggableTypeAdapter<?>> adapters) {
            super(NodeType.class);
            this.adapters = adapters;
        }

        @Override
        public NodeType deserialize(
                JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            String raw = json.getAsString();
            for (PluggableTypeAdapter<?> adapter : adapters) {
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
                                                    new StringReader(
                                                            String.format("\"%s\"", raw))));
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

    public static class MockAbstractNodeTypeAdapter extends PluggableTypeAdapter<AbstractNode> {
        public MockAbstractNodeTypeAdapter(Class<AbstractNode> klazz) {
            super(klazz);
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
                    case "name":
                        name = reader.nextString();
                        break;
                    case "nodeType":
                        String rawNodeType = reader.nextString();
                        for (PluggableTypeAdapter<?> adapter :
                                Set.of(
                                        adapter,
                                        new MockAbstractNodeTypeAdapter(AbstractNode.class))) {
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
                                                                            "\"%s\"",
                                                                            rawNodeType))));
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
                                                            AnnotationKey.valueOf(
                                                                    reader.nextName()),
                                                            reader.nextString());
                                                }
                                                reader.endObject();
                                                break;
                                            default:
                                        }
                                    }

                                    reader.endObject();
                                    break;
                            }
                        }
                        reader.endObject();

                        jvmId = "id";

                        target = new ServiceRef(jvmId, connectUrl, alias);
                        target.setLabels(targetLabels);
                        target.setPlatformAnnotations(platformAnnotations);
                        target.setCryostatAnnotations(cryostatAnnotations);
                        break;
                    default:
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
}
