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
import java.util.Set;
import java.util.function.Function;

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

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.Gson;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import graphql.ExecutionInput;
import graphql.GraphQL;
import graphql.Scalars;
import graphql.execution.preparsed.PreparsedDocumentEntry;
import graphql.execution.preparsed.PreparsedDocumentProvider;
import graphql.scalars.ExtendedScalars;
import graphql.schema.AsyncDataFetcher;
import graphql.schema.DataFetcher;
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
            Set<AbstractPermissionedDataFetcher<?>> fetchers, Set<AbstractTypeResolver> resolvers) {
        RuntimeWiring.Builder wiringBuilder =
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
                                        .build());
        for (AbstractPermissionedDataFetcher<?> fetcher : fetchers) {
            for (String ctx : fetcher.applicableContexts()) {
                DataFetcher<?> df = fetcher;
                if (fetcher.blocking()) {
                    df = AsyncDataFetcher.async(df);
                }
                wiringBuilder =
                        wiringBuilder.type(
                                TypeRuntimeWiring.newTypeWiring(ctx)
                                        .dataFetcher(fetcher.name(), df));
            }
        }
        for (AbstractTypeResolver typeResolver : resolvers) {
            wiringBuilder =
                    wiringBuilder.type(
                            TypeRuntimeWiring.newTypeWiring(typeResolver.typeName())
                                    .typeResolver(typeResolver));
        }
        RuntimeWiring wiring = wiringBuilder.build();
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

        Cache<String, PreparsedDocumentEntry> cache =
                Caffeine.newBuilder().maximumSize(1_000).build();
        PreparsedDocumentProvider preparsedCache =
                new PreparsedDocumentProvider() {
                    @Override
                    public PreparsedDocumentEntry getDocument(
                            ExecutionInput executionInput,
                            Function<ExecutionInput, PreparsedDocumentEntry> computeFunction) {
                        Function<String, PreparsedDocumentEntry> mapCompute =
                                key -> computeFunction.apply(executionInput);
                        return cache.get(executionInput.getQuery(), mapCompute);
                    }
                };
        return GraphQL.newGraphQL(new SchemaGenerator().makeExecutableSchema(tdr, wiring))
                .preparsedDocumentProvider(preparsedCache)
                .build();
    }

    @Binds
    @IntoSet
    abstract AbstractTypeResolver bindNodeTypeResolver(NodeTypeResolver typeResolver);

    @Binds
    @IntoSet
    abstract AbstractTypeResolver bindRecordingResolver(RecordingTypeResolver typeResolver);

    @Provides
    static RootNodeFetcher provideRootNodeFetcher(AuthManager auth, DiscoveryStorage storage) {
        return new RootNodeFetcher(auth, storage);
    }

    @Binds
    @IntoSet
    abstract AbstractPermissionedDataFetcher<?> bindRootNodeFetcher(RootNodeFetcher apdf);

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

    @Binds
    @IntoSet
    abstract AbstractPermissionedDataFetcher<?> bindRecordingsFetcher(RecordingsFetcher apdf);

    @Provides
    static ActiveRecordingsFetcher provideActiveRecordingsFetcher(AuthManager auth) {
        return new ActiveRecordingsFetcher(auth);
    }

    @Binds
    @IntoSet
    abstract AbstractPermissionedDataFetcher<?> bindActiveRecordingsFetcher(
            ActiveRecordingsFetcher apdf);

    @Provides
    static AllArchivedRecordingsFetcher provideAllArchivedRecordingsFetcher(
            AuthManager auth, RecordingArchiveHelper recordingArchiveHelper, Logger logger) {
        return new AllArchivedRecordingsFetcher(auth, recordingArchiveHelper, logger);
    }

    @Binds
    @IntoSet
    abstract AbstractPermissionedDataFetcher<?> bindAllArchivedRecordingsFetcher(
            AllArchivedRecordingsFetcher apdf);

    @Provides
    static ArchivedRecordingsFetcher provideArchivedRecordingsFetcher(AuthManager auth) {
        return new ArchivedRecordingsFetcher(auth);
    }

    @Binds
    @IntoSet
    abstract AbstractPermissionedDataFetcher<?> bindArchivedRecordingsFetcher(
            ArchivedRecordingsFetcher apdf);

    @Provides
    static EnvironmentNodeChildrenFetcher provideEnvironmentNodeChildrenFetcher(AuthManager auth) {
        return new EnvironmentNodeChildrenFetcher(auth);
    }

    @Binds
    @IntoSet
    abstract AbstractPermissionedDataFetcher<?> bindEnvironmentNodeChildrenFetcher(
            EnvironmentNodeChildrenFetcher apdf);

    @Provides
    static TargetNodeRecurseFetcher provideTargetNodeRecurseFetcher(AuthManager auth) {
        return new TargetNodeRecurseFetcher(auth);
    }

    @Binds
    @IntoSet
    abstract AbstractPermissionedDataFetcher<?> bindTargetNodeRecurseFetcher(
            TargetNodeRecurseFetcher apdf);

    @Provides
    static NodeFetcher provideNodeFetcher(AuthManager auth, RootNodeFetcher rootNodeFetcher) {
        return new NodeFetcher(auth, rootNodeFetcher);
    }

    @Binds
    @IntoSet
    abstract AbstractPermissionedDataFetcher<?> bindNodeFetcher(NodeFetcher apdf);

    @Provides
    static EnvironmentNodesFetcher provideEnvironmentNodesFetcher(
            AuthManager auth, RootNodeFetcher rootNodeFetcher) {
        return new EnvironmentNodesFetcher(auth, rootNodeFetcher);
    }

    @Binds
    @IntoSet
    abstract AbstractPermissionedDataFetcher<?> bindEnvironmentNodesFetcher(
            EnvironmentNodesFetcher apdf);

    @Provides
    static TargetNodesFetcher provideTargetNodesFetcher(
            AuthManager auth,
            RootNodeFetcher rootNodeFetcher,
            TargetNodeRecurseFetcher recurseFetcher) {
        return new TargetNodesFetcher(auth, rootNodeFetcher, recurseFetcher);
    }

    @Binds
    @IntoSet
    abstract AbstractPermissionedDataFetcher<?> bindTargetNodesFetcher(TargetNodesFetcher apdf);

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

    @Binds
    @IntoSet
    abstract AbstractPermissionedDataFetcher<?> bindStartRecordingOnTargetMutator(
            StartRecordingOnTargetMutator apdf);

    @Provides
    static SnapshotOnTargetMutator provideSnapshotOnTargetMutator(
            AuthManager auth,
            RecordingTargetHelper recordingTargetHelper,
            CredentialsManager credentialsManager) {
        return new SnapshotOnTargetMutator(auth, recordingTargetHelper, credentialsManager);
    }

    @Binds
    @IntoSet
    abstract AbstractPermissionedDataFetcher<?> bindSnapshotOnTargetMutator(
            SnapshotOnTargetMutator apdf);

    @Provides
    static ArchiveRecordingMutator provideArchiveRecordingMutator(
            AuthManager auth,
            RecordingArchiveHelper recordingArchiveHelper,
            CredentialsManager credentialsManager) {
        return new ArchiveRecordingMutator(auth, recordingArchiveHelper, credentialsManager);
    }

    @Binds
    @IntoSet
    abstract AbstractPermissionedDataFetcher<?> bindArchiveRecordingMutator(
            ArchiveRecordingMutator apdf);

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

    @Binds
    @IntoSet
    abstract AbstractPermissionedDataFetcher<?> bindStopRecordingMutator(StopRecordingMutator apdf);

    @Provides
    static PutActiveRecordingMetadataMutator providePutActiveRecordingMetadataMutator(
            AuthManager auth,
            CredentialsManager credentialsManager,
            TargetConnectionManager targetConnectionManager,
            RecordingTargetHelper recordingTargetHelper,
            RecordingMetadataManager metadataManager,
            Provider<WebServer> webServer,
            Gson gson) {
        return new PutActiveRecordingMetadataMutator(
                auth,
                credentialsManager,
                targetConnectionManager,
                recordingTargetHelper,
                metadataManager,
                webServer,
                gson);
    }

    @Binds
    @IntoSet
    abstract AbstractPermissionedDataFetcher<?> bindPutActiveRecordingMetadataMutator(
            PutActiveRecordingMetadataMutator apdf);

    @Provides
    static PutArchivedRecordingMetadataMutator providePutArchivedRecordingMetadataMutator(
            AuthManager auth,
            RecordingMetadataManager metadataManager,
            Provider<WebServer> webServer,
            Gson gson,
            Base32 base32) {
        return new PutArchivedRecordingMetadataMutator(
                auth, metadataManager, webServer, gson, base32);
    }

    @Binds
    @IntoSet
    abstract AbstractPermissionedDataFetcher<?> bindPutArchivedRecordingMetadataMutator(
            PutArchivedRecordingMetadataMutator apdf);

    @Provides
    static DeleteActiveRecordingMutator provideDeleteActiveRecordingMutator(
            AuthManager auth,
            RecordingTargetHelper recordingTargetHelper,
            CredentialsManager credentialsManager) {
        return new DeleteActiveRecordingMutator(auth, recordingTargetHelper, credentialsManager);
    }

    @Binds
    @IntoSet
    abstract AbstractPermissionedDataFetcher<?> bindDeleteActiveRecordingMutator(
            DeleteActiveRecordingMutator apdf);

    @Provides
    static DeleteArchivedRecordingMutator provideDeleteArchivedRecordingMutator(
            AuthManager auth, RecordingArchiveHelper recordingArchiveHelper) {
        return new DeleteArchivedRecordingMutator(auth, recordingArchiveHelper);
    }

    @Binds
    @IntoSet
    abstract AbstractPermissionedDataFetcher<?> bindDeleteArchivedRecordingMutator(
            DeleteArchivedRecordingMutator apdf);


        @Provides
        static JvmDetailsFetcher provideJvmDetailsFetcher(AuthManager auth, TargetConnectionManager tcm, Logger logger) {
            return new JvmDetailsFetcher(auth, tcm, logger);
        }

        @Binds
        @IntoSet
        abstract AbstractPermissionedDataFetcher<?> bindJvmDetailsFetcher(JvmDetailsFetcher apdf);
}
