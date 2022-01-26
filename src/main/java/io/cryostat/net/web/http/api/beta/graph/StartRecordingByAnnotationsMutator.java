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
package io.cryostat.net.web.http.api.beta.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.templates.TemplateType;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.platform.ServiceRef.AnnotationKey;
import io.cryostat.platform.discovery.AbstractNode;
import io.cryostat.platform.discovery.EnvironmentNode;
import io.cryostat.platform.discovery.TargetNode;
import io.cryostat.recordings.RecordingOptionsBuilderFactory;
import io.cryostat.recordings.RecordingTargetHelper;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;

class StartRecordingByAnnotationsMutator implements DataFetcher<List<TargetNode>> {

    private final DiscoveryFetcher discoveryFetcher;
    private final TargetNodeRecurseFetcher recurseFetcher;
    private final TargetConnectionManager targetConnectionManager;
    private final RecordingTargetHelper recordingTargetHelper;
    private final RecordingOptionsBuilderFactory recordingOptionsBuilderFactory;
    private final CredentialsManager credentialsManager;
    private final Logger logger;

    @Inject
    StartRecordingByAnnotationsMutator(
            DiscoveryFetcher discoveryFetcher,
            TargetNodeRecurseFetcher recurseFetcher,
            TargetConnectionManager targetConnectionManager,
            RecordingTargetHelper recordingTargetHelper,
            RecordingOptionsBuilderFactory recordingOptionsBuilderFactory,
            CredentialsManager credentialsManager,
            Logger logger) {
        this.discoveryFetcher = discoveryFetcher;
        this.recurseFetcher = recurseFetcher;
        this.targetConnectionManager = targetConnectionManager;
        this.recordingTargetHelper = recordingTargetHelper;
        this.recordingOptionsBuilderFactory = recordingOptionsBuilderFactory;
        this.credentialsManager = credentialsManager;
        this.logger = logger;
    }

    @Override
    public List<TargetNode> get(DataFetchingEnvironment environment) throws Exception {
        Map<String, Object> settings = environment.getArgument("recording");
        List<Map<String, String>> annotationSelectors = environment.getArgument("annotations");

        EnvironmentNode root = discoveryFetcher.get(environment);
        List<TargetNode> matches = new ArrayList<>();
        for (Map<String, String> selector : annotationSelectors) {
            String selectorKey = selector.get("key");
            String selectorValue = selector.get("value");
            Set<TargetNode> labeled =
                    findNodesWithAnnotation(selectorKey, selectorValue, root).stream()
                            .map(
                                    child -> {
                                        try {
                                            return recurseFetcher.get(
                                                    DataFetchingEnvironmentImpl
                                                            .newDataFetchingEnvironment()
                                                            .source(child)
                                                            .build());
                                        } catch (Exception e) {
                                            throw new RuntimeException(e);
                                        }
                                    })
                            .flatMap(List::stream)
                            .collect(Collectors.toSet());
            matches.addAll(labeled);
        }
        List<Exception> exceptions = new ArrayList<>();
        matches.parallelStream()
                .forEach(
                        tn -> {
                            String uri = tn.getTarget().getServiceUri().toString();
                            ConnectionDescriptor cd =
                                    new ConnectionDescriptor(
                                            uri, credentialsManager.getCredentials(tn.getTarget()));
                            try {
                                targetConnectionManager.executeConnectedTask(
                                        cd,
                                        conn -> {
                                            RecordingOptionsBuilder builder =
                                                    recordingOptionsBuilderFactory
                                                            .create(conn.getService())
                                                            .name((String) settings.get("name"));
                                            if (settings.containsKey("duration")) {
                                                builder =
                                                        builder.duration(
                                                                TimeUnit.SECONDS.toMillis(
                                                                        (Long)
                                                                                settings.get(
                                                                                        "duration")));
                                            }
                                            if (settings.containsKey("toDisk")) {
                                                builder =
                                                        builder.toDisk(
                                                                (Boolean) settings.get("toDisk"));
                                            }
                                            if (settings.containsKey("maxAge")) {
                                                builder =
                                                        builder.maxAge(
                                                                (Long) settings.get("maxAge"));
                                            }
                                            if (settings.containsKey("maxSize")) {
                                                builder =
                                                        builder.maxSize(
                                                                (Long) settings.get("maxSize"));
                                            }
                                            return recordingTargetHelper.startRecording(
                                                    cd,
                                                    builder.build(),
                                                    (String) settings.get("template"),
                                                    TemplateType.valueOf(
                                                            ((String) settings.get("templateType"))
                                                                    .toUpperCase()));
                                        },
                                        true);
                            } catch (Exception e) {
                                logger.error(e);
                                exceptions.add(e);
                            }
                        });
        if (!exceptions.isEmpty()) {
            throw new BatchedExceptions(exceptions);
        }

        return matches;
    }

    static List<AbstractNode> findNodesWithAnnotation(String key, String value, AbstractNode root) {
        List<AbstractNode> nodes = new ArrayList<>();
        if (root instanceof TargetNode) {
            Map<String, String> annotations = new HashMap<>();
            TargetNode targetNode = (TargetNode) root;
            annotations.putAll(targetNode.getTarget().getPlatformAnnotations());
            for (Map.Entry<AnnotationKey, String> annotation :
                    targetNode.getTarget().getCryostatAnnotations().entrySet()) {
                annotations.put(annotation.getKey().name(), annotation.getValue());
            }
            if (annotations.containsKey(key)) {
                if (value == null) {
                    nodes.add(root);
                } else if (value.equals(annotations.get(key))) {
                    nodes.add(root);
                }
            }
        } else if (root instanceof EnvironmentNode) {
            EnvironmentNode envNode = (EnvironmentNode) root;
            envNode.getChildren()
                    .forEach(
                            child -> {
                                nodes.addAll(findNodesWithAnnotation(key, value, child));
                            });
        }

        return nodes;
    }
}
