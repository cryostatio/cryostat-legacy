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
import java.util.List;

import javax.inject.Provider;
import javax.inject.Singleton;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.RequestHandler;
import io.cryostat.platform.PlatformClient;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingOptionsBuilderFactory;
import io.cryostat.recordings.RecordingTargetHelper;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import graphql.GraphQL;
import graphql.Scalars;
import graphql.scalars.ExtendedScalars;
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
            NodeTypeResolver nodeTypeResolver,
            RecordingTypeResolver recordingTypeResolver,
            DiscoveryFetcher discoveryFetcher,
            EnvironmentNodesFetcher environmentNodesFetcher,
            TargetNodesFetcher targetNodesFetcher,
            EnvironmentNodeChildrenFetcher nodeChildrenFetcher,
            TargetNodeRecurseFetcher targetNodeRecurseFetcher,
            RecordingsFetcher recordingsFetcher,
            StartRecordingOnTargetMutator startRecordingOnTargetMutator,
            StopRecordingsOnTargetMutator stopRecordingsOnTargetMutator,
            DeleteRecordingsOnTargetMutator deleteRecordingsOnTargetMutator) {
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
                                        .dataFetcher("environmentNodes", environmentNodesFetcher))
                        .type(
                                TypeRuntimeWiring.newTypeWiring("Query")
                                        .dataFetcher("targetNodes", targetNodesFetcher))
                        .type(
                                TypeRuntimeWiring.newTypeWiring("EnvironmentNode")
                                        .dataFetcher("children", nodeChildrenFetcher))
                        .type(
                                TypeRuntimeWiring.newTypeWiring("EnvironmentNode")
                                        .dataFetcher("descendantTargets", targetNodeRecurseFetcher))
                        .type(
                                TypeRuntimeWiring.newTypeWiring("TargetNode")
                                        .dataFetcher("recordings", recordingsFetcher))
                        .type(
                                TypeRuntimeWiring.newTypeWiring("TargetNode")
                                        .dataFetcher(
                                                "startRecording", startRecordingOnTargetMutator))
                        .type(
                                TypeRuntimeWiring.newTypeWiring("TargetNode")
                                        .dataFetcher(
                                                "stopRecordings", stopRecordingsOnTargetMutator))
                        .type(
                                TypeRuntimeWiring.newTypeWiring("TargetNode")
                                        .dataFetcher(
                                                "deleteRecordings",
                                                deleteRecordingsOnTargetMutator))
                        .type(
                                TypeRuntimeWiring.newTypeWiring("Node")
                                        .typeResolver(nodeTypeResolver))
                        .type(
                                TypeRuntimeWiring.newTypeWiring("Recording")
                                        .typeResolver(recordingTypeResolver))
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
    static DiscoveryFetcher provideDiscoveryFetcher(PlatformClient client) {
        return new DiscoveryFetcher(client);
    }

    @Provides
    static RecordingsFetcher provideRecordingsFetcher(
            TargetConnectionManager tcm,
            RecordingArchiveHelper archiveHelper,
            CredentialsManager credentialsManager,
            Provider<WebServer> webServer,
            Logger logger) {
        return new RecordingsFetcher(tcm, archiveHelper, credentialsManager, webServer, logger);
    }

    @Provides
    static TargetDescendentsFetcher provideTargetsDescendedFromFetcher(
            TargetNodeRecurseFetcher recurseFetcher, NodeFetcher nodeFetcher) {
        return new TargetDescendentsFetcher(recurseFetcher, nodeFetcher);
    }

    @Provides
    static EnvironmentNodeChildrenFetcher provideEnvironmentNodeChildrenFetcher() {
        return new EnvironmentNodeChildrenFetcher();
    }

    @Provides
    static TargetNodeRecurseFetcher provideTargetNodeRecurseFetcher() {
        return new TargetNodeRecurseFetcher();
    }

    @Provides
    static EnvironmentNodeRecurseFetcher provideEnvironmentNodeRecurseFetcher() {
        return new EnvironmentNodeRecurseFetcher();
    }

    @Provides
    static NodeFetcher provideNodeFetcher(DiscoveryFetcher discoveryFetcher) {
        return new NodeFetcher(discoveryFetcher);
    }

    @Provides
    static EnvironmentNodesFetcher provideEnvironmentNodesFetcher(
            DiscoveryFetcher discoveryFetcher, EnvironmentNodeRecurseFetcher recurseFetcher) {
        return new EnvironmentNodesFetcher(discoveryFetcher, recurseFetcher);
    }

    @Provides
    static TargetNodesFetcher provideTargetNodesFetcher(
            DiscoveryFetcher discoveryFetcher, TargetNodeRecurseFetcher recurseFetcher) {
        return new TargetNodesFetcher(discoveryFetcher, recurseFetcher);
    }

    @Provides
    static StartRecordingOnTargetMutator provideStartRecordingOnTargetMutator(
            TargetConnectionManager targetConnectionManager,
            RecordingTargetHelper recordingTargetHelper,
            RecordingOptionsBuilderFactory recordingOptionsBuilderFactory,
            CredentialsManager credentialsManager,
            Provider<WebServer> webServer) {
        return new StartRecordingOnTargetMutator(
                targetConnectionManager,
                recordingTargetHelper,
                recordingOptionsBuilderFactory,
                credentialsManager,
                webServer);
    }

    @Provides
    static StopRecordingsOnTargetMutator provideStopRecordingsOnTargetMutator(
            TargetConnectionManager targetConnectionManager,
            RecordingTargetHelper recordingTargetHelper,
            CredentialsManager credentialsManager,
            Provider<WebServer> webServer) {
        return new StopRecordingsOnTargetMutator(
                targetConnectionManager, recordingTargetHelper, credentialsManager, webServer);
    }

    @Provides
    static DeleteRecordingsOnTargetMutator provideDeleteRecordingsOnTargetMutator(
            TargetConnectionManager targetConnectionManager,
            RecordingTargetHelper recordingTargetHelper,
            CredentialsManager credentialsManager,
            Provider<WebServer> webServer) {
        return new DeleteRecordingsOnTargetMutator(
                targetConnectionManager, recordingTargetHelper, credentialsManager, webServer);
    }
}
