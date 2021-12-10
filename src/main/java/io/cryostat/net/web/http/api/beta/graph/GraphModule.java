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
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.inject.Named;
import javax.inject.Singleton;

import io.cryostat.net.web.http.RequestHandler;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import graphql.GraphQL;
import graphql.schema.DataFetcher;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;

@Module
public abstract class GraphModule {
    private static List<Map<String, String>> books =
            Arrays.asList(
                    Map.of(
                            "id",
                            "book-1",
                            "name",
                            "Harry Potter and the Philosopher's Stone",
                            "pageCount",
                            "223",
                            "authorId",
                            "author-1"),
                    Map.of(
                            "id",
                            "book-2",
                            "name",
                            "Moby Dick",
                            "pageCount",
                            "635",
                            "authorId",
                            "author-2"),
                    Map.of(
                            "id",
                            "book-3",
                            "name",
                            "Interview with the vampire",
                            "pageCount",
                            "371",
                            "authorId",
                            "author-3"));

    private static List<Map<String, String>> authors =
            Arrays.asList(
                    Map.of("id", "author-1", "firstName", "Joanne", "lastName", "Rowling"),
                    Map.of("id", "author-2", "firstName", "Herman", "lastName", "Melville"),
                    Map.of("id", "author-3", "firstName", "Anne", "lastName", "Rice"));

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
            @Named("bookById") DataFetcher<Map<String, String>> bookByIdFetcher,
            @Named("author") DataFetcher<Map<String, String>> authorFetcher) {
        try (InputStream is = GraphModule.class.getResourceAsStream("/schema.graphql")) {
            String sdl = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            TypeDefinitionRegistry tdr = new SchemaParser().parse(sdl);
            RuntimeWiring wiring =
                    RuntimeWiring.newRuntimeWiring()
                            .type(
                                    TypeRuntimeWiring.newTypeWiring("Query")
                                            .dataFetcher("bookById", bookByIdFetcher))
                            .type(
                                    TypeRuntimeWiring.newTypeWiring("Book")
                                            .dataFetcher("author", authorFetcher))
                            .build();

            return GraphQL.newGraphQL(new SchemaGenerator().makeExecutableSchema(tdr, wiring))
                    .build();
        } catch (IOException ioe) {
            throw new RuntimeException(ioe);
        }
    }

    @Provides
    @Singleton
    @Named("bookById")
    static DataFetcher<Map<String, String>> provideBookByIdFetcher() {
        return env ->
                books.stream()
                        .filter(v -> v.get("id").equals(env.getArgument("id")))
                        .findFirst()
                        .orElse(null);
    }

    @Provides
    @Singleton
    @Named("author")
    static DataFetcher<Map<String, String>> provideAuthorFetcher() {
        return env -> {
            Map<String, String> src = env.getSource();
            var id = src.get("authorId");
            return authors.stream().filter(v -> v.get("id").equals(id)).findFirst().orElse(null);
        };
    }
}
