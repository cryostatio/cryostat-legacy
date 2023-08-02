/*
 * Copyright The Cryostat Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.cryostat.net.web.http.api.v2.graph;

import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.net.Credentials;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.platform.ServiceRef;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.rules.ArchivedRecordingInfo;

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
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
class ArchiveRecordingMutatorTest {
    ArchiveRecordingMutator mutator;

    @Mock AuthManager auth;
    @Mock RecordingArchiveHelper recordingArchiveHelper;
    @Mock CredentialsManager credentialsManager;

    @Mock DataFetchingEnvironment env;
    @Mock GraphQLContext graphCtx;
    @Mock RoutingContext ctx;
    @Mock Credentials credentials;
    @Mock URI uri;
    @Mock Future<ArchivedRecordingInfo> future;

    @BeforeEach
    void setup() {
        this.mutator =
                new ArchiveRecordingMutator(auth, recordingArchiveHelper, credentialsManager);
    }

    @Test
    void shouldHaveExpectedRequiredPermissions() {
        MatcherAssert.assertThat(
                mutator.resourceActions(),
                Matchers.equalTo(
                        Set.of(
                                ResourceAction.READ_TARGET,
                                ResourceAction.CREATE_RECORDING,
                                ResourceAction.READ_RECORDING,
                                ResourceAction.READ_CREDENTIALS)));
    }

    @Test
    void shouldArchiveAndReturnRecording() throws Exception {
        when(env.getGraphQlContext()).thenReturn(graphCtx);
        when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        ArchivedRecordingInfo mockRecording = Mockito.mock(ArchivedRecordingInfo.class);
        GraphRecordingDescriptor source = Mockito.mock(GraphRecordingDescriptor.class);
        ServiceRef target = Mockito.mock(ServiceRef.class);
        source.target = target;

        when(env.getSource()).thenReturn(source);
        when(source.getName()).thenReturn("foo");
        when(target.getServiceUri()).thenReturn(uri);
        when(credentialsManager.getCredentials(Mockito.any(ServiceRef.class)))
                .thenReturn(credentials);
        when(recordingArchiveHelper.saveRecording(Mockito.any(), Mockito.any())).thenReturn(future);
        when(future.get()).thenReturn(mockRecording);

        ArchivedRecordingInfo recording = mutator.get(env);

        MatcherAssert.assertThat(recording, Matchers.notNullValue());
        MatcherAssert.assertThat(recording, Matchers.equalTo(mockRecording));

        Mockito.verify(recordingArchiveHelper)
                .saveRecording(Mockito.any(ConnectionDescriptor.class), Mockito.anyString());
    }
}
