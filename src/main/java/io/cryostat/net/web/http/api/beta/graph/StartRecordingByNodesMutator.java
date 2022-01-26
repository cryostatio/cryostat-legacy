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
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.templates.TemplateType;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.platform.discovery.AbstractNode;
import io.cryostat.platform.discovery.TargetNode;
import io.cryostat.recordings.RecordingOptionsBuilderFactory;
import io.cryostat.recordings.RecordingTargetHelper;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;

class StartRecordingByNodesMutator implements DataFetcher<List<TargetNode>> {

    private final TargetConnectionManager targetConnectionManager;
    private final RecordingTargetHelper recordingTargetHelper;
    private final RecordingOptionsBuilderFactory recordingOptionsBuilderFactory;
    private final CredentialsManager credentialsManager;
    private final NodeFetcher nodeFetcher;
    private final TargetNodeRecurseFetcher recurseFetcher;
    private final Logger logger;

    @Inject
    StartRecordingByNodesMutator(
            TargetConnectionManager targetConnectionManager,
            RecordingTargetHelper recordingTargetHelper,
            RecordingOptionsBuilderFactory recordingOptionsBuilderFactory,
            CredentialsManager credentialsManager,
            NodeFetcher nodeFetcher,
            TargetNodeRecurseFetcher recurseFetcher,
            Logger logger) {
        this.targetConnectionManager = targetConnectionManager;
        this.recordingTargetHelper = recordingTargetHelper;
        this.recordingOptionsBuilderFactory = recordingOptionsBuilderFactory;
        this.credentialsManager = credentialsManager;
        this.nodeFetcher = nodeFetcher;
        this.recurseFetcher = recurseFetcher;
        this.logger = logger;
    }

    @Override
    public List<TargetNode> get(DataFetchingEnvironment environment) throws Exception {
        Map<String, Object> settings = environment.getArgument("recording");
        List<Map<String, String>> selectors = environment.getArgument("nodes");

        List<AbstractNode> parents = new ArrayList<>();
        for (Map<String, String> selector : selectors) {
            String name = selector.get("name");
            String nodeType = selector.get("nodeType");
            AbstractNode parent =
                    nodeFetcher.get(
                            DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
                                    .arguments(Map.of("name", name, "nodeType", nodeType))
                                    .build());
            parents.add(parent);
        }
        List<Exception> exceptions = new ArrayList<>();
        List<TargetNode> children =
                parents.parallelStream()
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
                        .collect(Collectors.toList());
        children.parallelStream()
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

        return children;
    }
}
