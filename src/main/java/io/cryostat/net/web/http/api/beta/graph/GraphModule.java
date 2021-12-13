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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Named;
import javax.inject.Singleton;

import io.cryostat.net.web.http.RequestHandler;
import io.cryostat.platform.PlatformClient;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.discovery.AbstractNode;
import io.cryostat.platform.discovery.EnvironmentNode;
import io.cryostat.platform.internal.CustomTargetPlatformClient;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import graphql.GraphQL;
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
            @Named("customTargets") DataFetcher<List<ServiceRef>> customTargetsFetcher,
            @Named("customTargetsWithAnnotation")
                    DataFetcher<List<ServiceRef>> customTargetsWithAnnotationFetcher,
            @Named("discovery") DataFetcher<EnvironmentNode> discoveryFetcher,
            @Named("nodeChildren") DataFetcher<List<AbstractNode>> nodeChildrenFetcher) {
        RuntimeWiring wiring =
                RuntimeWiring.newRuntimeWiring()
                        .scalar(ExtendedScalars.Object)
                        .type(
                                TypeRuntimeWiring.newTypeWiring("Query")
                                        .dataFetcher("customTargets", customTargetsFetcher))
                        .type(
                                TypeRuntimeWiring.newTypeWiring("Query")
                                        .dataFetcher(
                                                "customTargetsWithAnnotation",
                                                customTargetsWithAnnotationFetcher))
                        .type(
                                TypeRuntimeWiring.newTypeWiring("Query")
                                        .dataFetcher("discovery", discoveryFetcher))
                        .type(
                                TypeRuntimeWiring.newTypeWiring("EnvironmentNode")
                                        .dataFetcher("children", nodeChildrenFetcher))
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
                        .build();
        try (InputStream is = GraphModule.class.getResourceAsStream("/schema.graphqls")) {
            TypeDefinitionRegistry tdr = new SchemaParser().parse(is);
            return GraphQL.newGraphQL(new SchemaGenerator().makeExecutableSchema(tdr, wiring))
                    .build();
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    @Provides
    @Singleton
    @Named("customTargets")
    static DataFetcher<List<ServiceRef>> provideCustomTargetsFetcher(
            CustomTargetPlatformClient client) {
        return env -> client.listDiscoverableServices();
    }

    @Provides
    @Singleton
    @Named("customTargetsWithAnnotation")
    static DataFetcher<List<ServiceRef>> provideCustomTargetsWithAnnotationFetcher(
            CustomTargetPlatformClient client) {
        return env -> {
            String annotationKey = env.getArgument("annotation");
            List<ServiceRef> withPlatformAnnotation =
                    client.listDiscoverableServices().stream()
                            .filter(c -> c.getPlatformAnnotations().containsKey(annotationKey))
                            .collect(Collectors.toList());
            List<ServiceRef> withCryostatAnnotation =
                    client.listDiscoverableServices().stream()
                            .filter(
                                    c ->
                                            c.getCryostatAnnotations().keySet().stream()
                                                    .map(Enum::name)
                                                    .anyMatch(k -> k.equals(annotationKey)))
                            .collect(Collectors.toList());

            List<ServiceRef> result = new ArrayList<>();
            result.addAll(withPlatformAnnotation);
            result.addAll(withCryostatAnnotation);
            return result;
        };
    }

    @Provides
    @Singleton
    @Named("discovery")
    static DataFetcher<EnvironmentNode> provideDiscoveryFetcher(PlatformClient client) {
        return env -> client.getDiscoveryTree();
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
}
