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
package io.cryostat.net.web.http.api.v2.graph;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.inject.Provider;
import javax.inject.Singleton;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.net.AuthManager;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.RequestHandler;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingMetadataManager;
import io.cryostat.recordings.RecordingOptionsBuilderFactory;
import io.cryostat.recordings.RecordingTargetHelper;

import com.google.gson.Gson;
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
import org.apache.commons.codec.binary.Base32;

@Module
public abstract class GraphModule {

    @Binds
    @IntoSet
    abstract RequestHandler bindGraphPostBodyHandler(GraphQLPostBodyHandler handler);

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
            RootNodeFetcher rootNodeFetcher,
            EnvironmentNodesFetcher environmentNodesFetcher,
            TargetNodesFetcher targetNodesFetcher,
            EnvironmentNodeChildrenFetcher nodeChildrenFetcher,
            TargetNodeRecurseFetcher targetNodeRecurseFetcher,
            RecordingsFetcher recordingsFetcher,
            ActiveRecordingsFetcher activeRecordingsFetcher,
            ArchivedRecordingsFetcher archivedRecordingsFetcher,
            AllArchivedRecordingsFetcher allArchivedRecordingsFetcher,
            StartRecordingOnTargetMutator startRecordingOnTargetMutator,
            SnapshotOnTargetMutator snapshotOnTargetMutator,
            StopRecordingMutator stopRecordingMutator,
            ArchiveRecordingMutator archiveRecordingMutator,
            PutActiveRecordingMetadataMutator putActiveRecordingMetadataMutator,
            PutArchivedRecordingMetadataMutator putArchivedRecordingMetadataMutator,
            DeleteActiveRecordingMutator deleteActiveRecordingMutator,
            DeleteArchivedRecordingMutator deleteArchivedRecordingMutator) {
        RuntimeWiring wiring =
                RuntimeWiring.newRuntimeWiring()
                        .scalar(ExtendedScalars.Object)
                        .scalar(ExtendedScalars.GraphQLLong)
                        .scalar(ExtendedScalars.Url)
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
                                        .dataFetcher("rootNode", rootNodeFetcher))
                        .type(
                                TypeRuntimeWiring.newTypeWiring("Query")
                                        .dataFetcher("environmentNodes", environmentNodesFetcher))
                        .type(
                                TypeRuntimeWiring.newTypeWiring("Query")
                                        .dataFetcher("targetNodes", targetNodesFetcher))
                        .type(
                                TypeRuntimeWiring.newTypeWiring("Query")
                                        .dataFetcher(
                                                "archivedRecordings", allArchivedRecordingsFetcher))
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
                                TypeRuntimeWiring.newTypeWiring("Recordings")
                                        .dataFetcher("active", activeRecordingsFetcher))
                        .type(
                                TypeRuntimeWiring.newTypeWiring("Recordings")
                                        .dataFetcher("archived", archivedRecordingsFetcher))
                        .type(
                                TypeRuntimeWiring.newTypeWiring("TargetNode")
                                        .dataFetcher(
                                                "doStartRecording", startRecordingOnTargetMutator))
                        .type(
                                TypeRuntimeWiring.newTypeWiring("TargetNode")
                                        .dataFetcher("doSnapshot", snapshotOnTargetMutator))
                        .type(
                                TypeRuntimeWiring.newTypeWiring("ActiveRecording")
                                        .dataFetcher("doArchive", archiveRecordingMutator))
                        .type(
                                TypeRuntimeWiring.newTypeWiring("ActiveRecording")
                                        .dataFetcher("doStop", stopRecordingMutator))
                        .type(
                                TypeRuntimeWiring.newTypeWiring("ActiveRecording")
                                        .dataFetcher(
                                                "doPutMetadata", putActiveRecordingMetadataMutator))
                        .type(
                                TypeRuntimeWiring.newTypeWiring("ArchivedRecording")
                                        .dataFetcher(
                                                "doPutMetadata",
                                                putArchivedRecordingMetadataMutator))
                        .type(
                                TypeRuntimeWiring.newTypeWiring("ActiveRecording")
                                        .dataFetcher("doDelete", deleteActiveRecordingMutator))
                        .type(
                                TypeRuntimeWiring.newTypeWiring("ArchivedRecording")
                                        .dataFetcher("doDelete", deleteArchivedRecordingMutator))
                        .type(
                                TypeRuntimeWiring.newTypeWiring("Node")
                                        .typeResolver(nodeTypeResolver))
                        .type(
                                TypeRuntimeWiring.newTypeWiring("Recording")
                                        .typeResolver(recordingTypeResolver))
                        .build();
        SchemaParser parser = new SchemaParser();
        TypeDefinitionRegistry tdr = new TypeDefinitionRegistry();
        List<String> schemaFilenames = List.of("types", "queries");
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
    static RootNodeFetcher provideRootNodeFetcher(AuthManager auth, DiscoveryStorage storage) {
        return new RootNodeFetcher(auth, storage);
    }

    @Provides
    static RecordingsFetcher provideRecordingsFetcher(
            AuthManager auth,
            TargetConnectionManager tcm,
            RecordingArchiveHelper archiveHelper,
            CredentialsManager credentialsManager,
            RecordingMetadataManager metadataManager,
            Provider<WebServer> webServer,
            Logger logger) {
        return new RecordingsFetcher(
                auth, tcm, archiveHelper, credentialsManager, metadataManager, webServer, logger);
    }

    @Provides
    static ActiveRecordingsFetcher provideActiveRecordingsFetcher(AuthManager auth) {
        return new ActiveRecordingsFetcher(auth);
    }

    @Provides
    static AllArchivedRecordingsFetcher provideAllArchivedRecordingsFetcher(
            AuthManager auth, RecordingArchiveHelper recordingArchiveHelper, Logger logger) {
        return new AllArchivedRecordingsFetcher(auth, recordingArchiveHelper, logger);
    }

    @Provides
    static ArchivedRecordingsFetcher provideArchivedRecordingsFetcher(AuthManager auth) {
        return new ArchivedRecordingsFetcher(auth);
    }

    @Provides
    static EnvironmentNodeChildrenFetcher provideEnvironmentNodeChildrenFetcher(AuthManager auth) {
        return new EnvironmentNodeChildrenFetcher(auth);
    }

    @Provides
    static TargetNodeRecurseFetcher provideTargetNodeRecurseFetcher(AuthManager auth) {
        return new TargetNodeRecurseFetcher(auth);
    }

    @Provides
    static EnvironmentNodeRecurseFetcher provideEnvironmentNodeRecurseFetcher(AuthManager auth) {
        return new EnvironmentNodeRecurseFetcher(auth);
    }

    @Provides
    static NodeFetcher provideNodeFetcher(AuthManager auth, RootNodeFetcher rootNodeFetcher) {
        return new NodeFetcher(auth, rootNodeFetcher);
    }

    @Provides
    static EnvironmentNodesFetcher provideEnvironmentNodesFetcher(
            AuthManager auth, RootNodeFetcher rootNodeFetcher) {
        return new EnvironmentNodesFetcher(auth, rootNodeFetcher);
    }

    @Provides
    static TargetNodesFetcher provideTargetNodesFetcher(
            AuthManager auth,
            RootNodeFetcher rootNodeFetcher,
            TargetNodeRecurseFetcher recurseFetcher) {
        return new TargetNodesFetcher(auth, rootNodeFetcher, recurseFetcher);
    }

    @Provides
    static StartRecordingOnTargetMutator provideStartRecordingOnTargetMutator(
            AuthManager auth,
            TargetConnectionManager targetConnectionManager,
            RecordingTargetHelper recordingTargetHelper,
            RecordingOptionsBuilderFactory recordingOptionsBuilderFactory,
            CredentialsManager credentialsManager,
            RecordingMetadataManager metadataManager,
            Provider<WebServer> webServer,
            Gson gson) {
        return new StartRecordingOnTargetMutator(
                auth,
                targetConnectionManager,
                recordingTargetHelper,
                recordingOptionsBuilderFactory,
                credentialsManager,
                metadataManager,
                webServer,
                gson);
    }

    @Provides
    static SnapshotOnTargetMutator provideSnapshotOnTargetMutator(
            AuthManager auth,
            RecordingTargetHelper recordingTargetHelper,
            CredentialsManager credentialsManager) {
        return new SnapshotOnTargetMutator(auth, recordingTargetHelper, credentialsManager);
    }

    @Provides
    static ArchiveRecordingMutator provideArchiveRecordingMutator(
            AuthManager auth,
            RecordingArchiveHelper recordingArchiveHelper,
            CredentialsManager credentialsManager) {
        return new ArchiveRecordingMutator(auth, recordingArchiveHelper, credentialsManager);
    }

    @Provides
    static StopRecordingMutator provideStopRecordingsOnTargetMutator(
            AuthManager auth,
            TargetConnectionManager targetConnectionManager,
            RecordingTargetHelper recordingTargetHelper,
            CredentialsManager credentialsManager,
            RecordingMetadataManager metadataManager,
            Provider<WebServer> webServer) {
        return new StopRecordingMutator(
                auth,
                targetConnectionManager,
                recordingTargetHelper,
                credentialsManager,
                metadataManager,
                webServer);
    }

    @Provides
    static PutActiveRecordingMetadataMutator providePutActiveRecordingMetadataMutator(
            CredentialsManager credentialsManager,
            TargetConnectionManager targetConnectionManager,
            RecordingTargetHelper recordingTargetHelper,
            RecordingMetadataManager metadataManager,
            Provider<WebServer> webServer,
            Gson gson) {
        return new PutActiveRecordingMetadataMutator(
                credentialsManager,
                targetConnectionManager,
                recordingTargetHelper,
                metadataManager,
                webServer,
                gson);
    }

    @Provides
    static PutArchivedRecordingMetadataMutator providePutArchivedRecordingMetadataMutator(
            RecordingMetadataManager metadataManager,
            Provider<WebServer> webServer,
            Gson gson,
            Base32 base32) {
        return new PutArchivedRecordingMetadataMutator(metadataManager, webServer, gson, base32);
    }

    @Provides
    static DeleteActiveRecordingMutator provideDeleteActiveRecordingMutator(
            AuthManager auth,
            RecordingTargetHelper recordingTargetHelper,
            CredentialsManager credentialsManager) {
        return new DeleteActiveRecordingMutator(auth, recordingTargetHelper, credentialsManager);
    }

    @Provides
    static DeleteArchivedRecordingMutator provideDeleteArchivedRecordingMutator(
            AuthManager auth, RecordingArchiveHelper recordingArchiveHelper) {
        return new DeleteArchivedRecordingMutator(auth, recordingArchiveHelper);
    }
}
