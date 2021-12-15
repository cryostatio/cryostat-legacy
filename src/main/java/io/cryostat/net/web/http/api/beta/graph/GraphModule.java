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

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.openjdk.jmc.common.unit.QuantityConversionException;
import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.templates.TemplateType;
import io.cryostat.jmc.serialization.HyperlinkedSerializableRecordingDescriptor;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.RequestHandler;
import io.cryostat.platform.PlatformClient;
import io.cryostat.platform.discovery.AbstractNode;
import io.cryostat.platform.discovery.EnvironmentNode;
import io.cryostat.platform.discovery.TargetNode;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingOptionsBuilderFactory;
import io.cryostat.recordings.RecordingTargetHelper;
import io.cryostat.rules.ArchivedRecordingInfo;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import graphql.GraphQL;
import graphql.Scalars;
import graphql.TypeResolutionEnvironment;
import graphql.scalars.ExtendedScalars;
import graphql.schema.DataFetcher;
import graphql.schema.GraphQLObjectType;
import graphql.schema.TypeResolver;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;

@Module
public abstract class GraphModule {

    @Binds
    @IntoSet
    abstract RequestHandler bindGraphPostHandler(GraphQLPostHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindGraphGetHandler(GraphQLGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindGraphiGetHandler(GraphiQLGetHandler handler);

    @Provides
    @Singleton
    static GraphQL provideGraphQL(
            @Named("discovery") DataFetcher<EnvironmentNode> discoveryFetcher,
            @Named("nodeChildren") DataFetcher<List<AbstractNode>> nodeChildrenFetcher,
            @Named("recordings") DataFetcher<Recordings> recordingsFetcher,
            @Named("targetsDescendedFrom")
                    DataFetcher<List<TargetNode>> targetsDescendedFromFetcher,
            @Named("startRecording") DataFetcher<List<TargetNode>> startRecordingFetcher) {
        RuntimeWiring wiring =
                RuntimeWiring.newRuntimeWiring()
                        .scalar(ExtendedScalars.Object)
                        .scalar(ExtendedScalars.Url)
                        .scalar(Scalars.GraphQLLong)
                        .scalar(Scalars.GraphQLBoolean)
                        .scalar(
                                ExtendedScalars.newAliasedScalar("ServiceURI")
                                        .aliasedScalar(Scalars.GraphQLString)
                                        .build())
                        .scalar(
                                ExtendedScalars.newAliasedScalar("NodeType")
                                        .aliasedScalar(Scalars.GraphQLString)
                                        .build())
                        .type(
                                TypeRuntimeWiring.newTypeWiring("Query")
                                        .dataFetcher("discovery", discoveryFetcher))
                        .type(
                                TypeRuntimeWiring.newTypeWiring("Query")
                                        .dataFetcher(
                                                "targetsDescendedFrom",
                                                targetsDescendedFromFetcher))
                        .type(
                                TypeRuntimeWiring.newTypeWiring("Mutation")
                                        .dataFetcher("startRecording", startRecordingFetcher))
                        .type(
                                TypeRuntimeWiring.newTypeWiring("EnvironmentNode")
                                        .dataFetcher("children", nodeChildrenFetcher))
                        .type(
                                TypeRuntimeWiring.newTypeWiring("TargetNode")
                                        .dataFetcher("recordings", recordingsFetcher))
                        .type(
                                TypeRuntimeWiring.newTypeWiring("Node")
                                        .typeResolver(
                                                new TypeResolver() {
                                                    @Override
                                                    public GraphQLObjectType getType(
                                                            TypeResolutionEnvironment env) {
                                                        Object o = env.getObject();
                                                        if (o instanceof EnvironmentNode) {
                                                            return env.getSchema()
                                                                    .getObjectType(
                                                                            "EnvironmentNode");
                                                        } else {
                                                            return env.getSchema()
                                                                    .getObjectType("TargetNode");
                                                        }
                                                    }
                                                }))
                        .type(
                                TypeRuntimeWiring.newTypeWiring("Recording")
                                        .typeResolver(
                                                new TypeResolver() {
                                                    @Override
                                                    public GraphQLObjectType getType(
                                                            TypeResolutionEnvironment env) {
                                                        Object o = env.getObject();
                                                        if (o
                                                                instanceof
                                                                HyperlinkedSerializableRecordingDescriptor) {
                                                            return env.getSchema()
                                                                    .getObjectType(
                                                                            "ActiveRecording");
                                                        } else {
                                                            return env.getSchema()
                                                                    .getObjectType(
                                                                            "ArchivedRecording");
                                                        }
                                                    }
                                                }))
                        .build();
        SchemaParser parser = new SchemaParser();
        TypeDefinitionRegistry tdr = new TypeDefinitionRegistry();
        List<String> schemaFilenames = List.of("types", "queries", "mutations");
        for (String schema : schemaFilenames) {
            try (InputStream is =
                    GraphModule.class.getResourceAsStream(String.format("/%s.graphqls", schema))) {
                tdr.merge(parser.parse(is));
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }
        return GraphQL.newGraphQL(new SchemaGenerator().makeExecutableSchema(tdr, wiring)).build();
    }

    @Provides
    @Singleton
    @Named("discovery")
    static DataFetcher<EnvironmentNode> provideDiscoveryFetcher(PlatformClient client) {
        return env -> client.getDiscoveryTree();
    }

    @Provides
    @Singleton
    @Named("startRecording")
    static DataFetcher<List<TargetNode>> provideStartRecordingFetcher(
            PlatformClient client,
            TargetConnectionManager tcm,
            RecordingTargetHelper helper,
            RecordingOptionsBuilderFactory recordingOptionsBuilderFactory,
            CredentialsManager credentialsManager,
            Provider<WebServer> webServer,
            Logger logger) {
        return env -> {
            Map<String, Object> settings = env.getArgument("recording");
            List<Map<String, String>> selectors = env.getArgument("nodes");

            List<AbstractNode> parents = new ArrayList<>();
            for (Map<String, String> selector : selectors) {
                String name = selector.get("name");
                String nodeType = selector.get("nodeType");
                AbstractNode parent = findNode(name, nodeType, client.getDiscoveryTree());
                if (parent == null) {
                    throw new NoSuchElementException(String.format("%s named %s", nodeType, name));
                }
                parents.add(parent);
            }
            List<Exception> exceptions = new ArrayList<>();
            List<TargetNode> children =
                    parents.parallelStream()
                            .map(GraphModule::recurseChildren)
                            .flatMap(List::stream)
                            .collect(Collectors.toList());
            children.parallelStream()
                    .forEach(
                            tn -> {
                                String uri = tn.getTarget().getServiceUri().toString();
                                ConnectionDescriptor cd =
                                        new ConnectionDescriptor(
                                                uri,
                                                credentialsManager.getCredentials(tn.getTarget()));
                                try {
                                    tcm.executeConnectedTask(
                                            cd,
                                            conn -> {
                                                RecordingOptionsBuilder builder =
                                                        recordingOptionsBuilderFactory
                                                                .create(conn.getService())
                                                                .name(
                                                                        (String)
                                                                                settings.get(
                                                                                        "name"));
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
                                                                    (Boolean)
                                                                            settings.get("toDisk"));
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
                                                IRecordingDescriptor desc =
                                                        helper.startRecording(
                                                                cd,
                                                                builder.build(),
                                                                (String) settings.get("template"),
                                                                TemplateType.valueOf(
                                                                        ((String)
                                                                                        settings
                                                                                                .get(
                                                                                                        "templateType"))
                                                                                .toUpperCase()));
                                                return new HyperlinkedSerializableRecordingDescriptor(
                                                        desc,
                                                        webServer
                                                                .get()
                                                                .getDownloadURL(
                                                                        conn, desc.getName()),
                                                        webServer
                                                                .get()
                                                                .getReportURL(
                                                                        conn, desc.getName()));
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
        };
    }

    @Provides
    @Singleton
    @Named("recordings")
    static DataFetcher<Recordings> provideRecordingsFetcher(
            TargetConnectionManager tcm,
            RecordingArchiveHelper archiveHelper,
            CredentialsManager credentialsManager,
            Provider<WebServer> webServer,
            Logger logger) {
        return env -> {
            String targetId = ((TargetNode) env.getSource()).getTarget().getServiceUri().toString();
            Recordings recordings = new Recordings();

            ConnectionDescriptor cd =
                    new ConnectionDescriptor(
                            targetId,
                            credentialsManager.getCredentials(
                                    ((TargetNode) env.getSource()).getTarget()));
            recordings.archived = archiveHelper.getRecordings(targetId).get();
            recordings.active =
                    tcm.executeConnectedTask(
                            cd,
                            conn -> {
                                return conn.getService().getAvailableRecordings().stream()
                                        .map(
                                                r -> {
                                                    try {
                                                        String downloadUrl =
                                                                webServer
                                                                        .get()
                                                                        .getDownloadURL(
                                                                                conn, r.getName());
                                                        String reportUrl =
                                                                webServer
                                                                        .get()
                                                                        .getReportURL(
                                                                                conn, r.getName());
                                                        return new HyperlinkedSerializableRecordingDescriptor(
                                                                r, downloadUrl, reportUrl);
                                                    } catch (QuantityConversionException
                                                            | URISyntaxException
                                                            | IOException e) {
                                                        logger.error(e);
                                                        return null;
                                                    }
                                                })
                                        .filter(Objects::nonNull)
                                        .collect(Collectors.toList());
                            },
                            false);

            return recordings;
        };
    }

    @Provides
    @Singleton
    @Named("targetsDescendedFrom")
    static DataFetcher<List<TargetNode>> provideTargetsDescendedFromFetcher(PlatformClient client) {
        return env -> {
            List<Map<String, String>> selectors = env.getArgument("nodes");
            List<TargetNode> result = new ArrayList<>();
            for (Map<String, String> selector : selectors) {
                String name = selector.get("name");
                String nodeType = selector.get("nodeType");

                AbstractNode parent = findNode(name, nodeType, client.getDiscoveryTree());
                if (parent == null) {
                    throw new NoSuchElementException(String.format("%s named %s", nodeType, name));
                }
                result.addAll(recurseChildren(parent));
            }
            return result;
        };
    }

    static AbstractNode findNode(String name, String nodeType, AbstractNode root) {
        if (Objects.equals(name.toLowerCase(), root.getName().toLowerCase())
                && Objects.equals(
                        nodeType.toLowerCase(), root.getNodeType().getKind().toLowerCase())) {
            return root;
        }
        if (root instanceof EnvironmentNode) {
            for (AbstractNode child : ((EnvironmentNode) root).getChildren()) {
                AbstractNode found = findNode(name, nodeType, child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    static List<TargetNode> recurseChildren(AbstractNode node) {
        if (node instanceof TargetNode) {
            return List.of((TargetNode) node);
        } else if (node instanceof EnvironmentNode) {
            List<TargetNode> result = new ArrayList<>();
            for (AbstractNode child : ((EnvironmentNode) node).getChildren()) {
                result.addAll(recurseChildren(child));
            }
            return result;
        } else {
            throw new IllegalStateException(node.getClass().toString());
        }
    }

    @Provides
    @Singleton
    @Named("nodeChildren")
    static DataFetcher<List<AbstractNode>> provideNodeChildrenFetcher() {
        return env -> {
            EnvironmentNode source = env.getSource();
            return new ArrayList<>(source.getChildren());
        };
    }

    static class Recordings {
        List<HyperlinkedSerializableRecordingDescriptor> active;
        List<ArchivedRecordingInfo> archived;
    }

    static class BatchedExceptions extends RuntimeException {
        final List<Exception> causes;

        BatchedExceptions(List<Exception> causes) {
            super(
                    String.format(
                            "Causes: %s",
                            causes.stream()
                                    .map(Exception::getMessage)
                                    .collect(Collectors.toList())));
            this.causes = causes;
        }
    }
}
