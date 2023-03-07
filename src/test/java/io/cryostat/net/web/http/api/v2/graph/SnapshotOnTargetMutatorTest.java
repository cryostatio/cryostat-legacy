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

import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.net.Credentials;
import io.cryostat.jmc.serialization.HyperlinkedSerializableRecordingDescriptor;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.discovery.TargetNode;
import io.cryostat.recordings.RecordingTargetHelper;

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SnapshotOnTargetMutatorTest {
    SnapshotOnTargetMutator mutator;

    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock RecordingTargetHelper recordingTargetHelper;

    @Mock DataFetchingEnvironment env;
    @Mock GraphQLContext graphCtx;
    @Mock RoutingContext ctx;
    @Mock Credentials credentials;
    @Mock URI uri;
    @Mock Future<HyperlinkedSerializableRecordingDescriptor> future;

    @BeforeEach
    void setup() {
        this.mutator = new SnapshotOnTargetMutator(auth, credentialsManager, recordingTargetHelper);
    }

    @Test
    void shouldHaveExpectedRequiredPermissions() {
        MatcherAssert.assertThat(
                mutator.resourceActions(),
                Matchers.equalTo(
                        Set.of(
                                ResourceAction.READ_RECORDING,
                                ResourceAction.UPDATE_RECORDING,
                                ResourceAction.CREATE_RECORDING,
                                ResourceAction.READ_TARGET,
                                ResourceAction.UPDATE_TARGET,
                                ResourceAction.READ_CREDENTIALS)));
    }

    @Test
    void shouldDeleteAndReturnSource() throws Exception {
        when(env.getGraphQlContext()).thenReturn(graphCtx);
        when(graphCtx.get(RoutingContext.class)).thenReturn(ctx);
        HttpServerRequest req = Mockito.mock(HttpServerRequest.class);
        when(ctx.request()).thenReturn(req);
        when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
        when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        TargetNode source = Mockito.mock(TargetNode.class);
        ServiceRef target = Mockito.mock(ServiceRef.class);
        HyperlinkedSerializableRecordingDescriptor hsrd =
                Mockito.mock(HyperlinkedSerializableRecordingDescriptor.class);

        when(env.getSource()).thenReturn(source);
        when(source.getTarget()).thenReturn(target);
        when(target.getServiceUri()).thenReturn(uri);
        when(credentialsManager.getCredentials(Mockito.any(ServiceRef.class)))
                .thenReturn(credentials);
        when(recordingTargetHelper.createSnapshot(Mockito.any())).thenReturn(future);
        when(future.get()).thenReturn(hsrd);
        when(hsrd.getName()).thenReturn("foo");
        when(hsrd.getDuration()).thenReturn(100L);

        GraphRecordingDescriptor recording = mutator.get(env);

        MatcherAssert.assertThat(recording, Matchers.notNullValue());
        MatcherAssert.assertThat(recording.target, Matchers.equalTo(target));
        MatcherAssert.assertThat(recording.getName(), Matchers.equalTo("foo"));
        MatcherAssert.assertThat(recording.getDuration(), Matchers.equalTo(100L));

        Mockito.verify(recordingTargetHelper)
                .createSnapshot(Mockito.any(ConnectionDescriptor.class));
    }
}
