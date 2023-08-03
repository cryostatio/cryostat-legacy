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
package io.cryostat.net.web.http.api.v1;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.jmc.serialization.HyperlinkedSerializableRecordingDescriptor;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.recordings.RecordingTargetHelper;
import io.cryostat.recordings.RecordingTargetHelper.SnapshotCreationException;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TargetSnapshotPostHandlerTest {

    TargetSnapshotPostHandler handler;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock RecordingTargetHelper recordingTargetHelper;
    @Mock Logger logger;

    @BeforeEach
    void setup() {
        this.handler =
                new TargetSnapshotPostHandler(
                        auth, credentialsManager, recordingTargetHelper, logger);
    }

    @Test
    void shouldHaveExpectedRequiredPermissions() {
        MatcherAssert.assertThat(
                handler.resourceActions(),
                Matchers.equalTo(
                        Set.of(ResourceAction.READ_TARGET, ResourceAction.UPDATE_RECORDING)));
    }

    @Test
    void shouldCreateSnapshot() throws Exception {
        Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        RoutingContext ctx = Mockito.mock(RoutingContext.class);
        HttpServerRequest req = Mockito.mock(HttpServerRequest.class);
        Mockito.when(ctx.request()).thenReturn(req);
        Mockito.when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
        HttpServerResponse resp = Mockito.mock(HttpServerResponse.class);
        Mockito.when(ctx.response()).thenReturn(resp);
        Mockito.when(ctx.pathParam("targetId")).thenReturn("someHost");

        HyperlinkedSerializableRecordingDescriptor snapshotDescriptor =
                Mockito.mock(HyperlinkedSerializableRecordingDescriptor.class);
        CompletableFuture<HyperlinkedSerializableRecordingDescriptor> future1 =
                Mockito.mock(CompletableFuture.class);
        Mockito.when(recordingTargetHelper.createSnapshot(Mockito.any(ConnectionDescriptor.class)))
                .thenReturn(future1);
        Mockito.when(future1.get()).thenReturn(snapshotDescriptor);
        Mockito.when(snapshotDescriptor.getName()).thenReturn("snapshot-1");

        CompletableFuture<Boolean> future2 = Mockito.mock(CompletableFuture.class);
        Mockito.when(
                        recordingTargetHelper.verifySnapshot(
                                Mockito.any(ConnectionDescriptor.class),
                                Mockito.any(HyperlinkedSerializableRecordingDescriptor.class)))
                .thenReturn(future2);
        Mockito.when(future2.get()).thenReturn(true);

        handler.handle(ctx);

        Mockito.verify(resp).setStatusCode(200);
        Mockito.verify(resp).end("snapshot-1");
    }

    @Test
    void shouldHandleSnapshotCreationExceptionDuringCreation() throws Exception {
        Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        RoutingContext ctx = Mockito.mock(RoutingContext.class);
        HttpServerRequest req = Mockito.mock(HttpServerRequest.class);
        Mockito.when(ctx.request()).thenReturn(req);
        Mockito.when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
        HttpServerResponse resp = Mockito.mock(HttpServerResponse.class);
        Mockito.when(ctx.response()).thenReturn(resp);
        Mockito.when(ctx.pathParam("targetId")).thenReturn("someHost");

        CompletableFuture<HyperlinkedSerializableRecordingDescriptor> future1 =
                Mockito.mock(CompletableFuture.class);
        Mockito.when(recordingTargetHelper.createSnapshot(Mockito.any(ConnectionDescriptor.class)))
                .thenReturn(future1);
        Mockito.when(future1.get())
                .thenThrow(
                        new ExecutionException(
                                new SnapshotCreationException("some error message")));

        HttpException ex = Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(500));
        MatcherAssert.assertThat(ex.getPayload(), Matchers.equalTo("some error message"));
    }

    @Test
    void shouldHandleSnapshotCreationExceptionDuringVerification() throws Exception {
        Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        RoutingContext ctx = Mockito.mock(RoutingContext.class);
        HttpServerRequest req = Mockito.mock(HttpServerRequest.class);
        Mockito.when(ctx.request()).thenReturn(req);
        Mockito.when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
        HttpServerResponse resp = Mockito.mock(HttpServerResponse.class);
        Mockito.when(ctx.response()).thenReturn(resp);
        Mockito.when(ctx.pathParam("targetId")).thenReturn("someHost");

        HyperlinkedSerializableRecordingDescriptor snapshotDescriptor =
                Mockito.mock(HyperlinkedSerializableRecordingDescriptor.class);
        CompletableFuture<HyperlinkedSerializableRecordingDescriptor> future1 =
                Mockito.mock(CompletableFuture.class);
        Mockito.when(recordingTargetHelper.createSnapshot(Mockito.any(ConnectionDescriptor.class)))
                .thenReturn(future1);
        Mockito.when(future1.get()).thenReturn(snapshotDescriptor);
        Mockito.when(snapshotDescriptor.getName()).thenReturn("snapshot-1");

        CompletableFuture<Boolean> future2 = Mockito.mock(CompletableFuture.class);
        Mockito.when(
                        recordingTargetHelper.verifySnapshot(
                                Mockito.any(ConnectionDescriptor.class),
                                Mockito.any(HyperlinkedSerializableRecordingDescriptor.class)))
                .thenReturn(future2);
        Mockito.when(future2.get())
                .thenThrow(
                        new ExecutionException(
                                new SnapshotCreationException("some error message")));

        HttpException ex = Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(500));
        MatcherAssert.assertThat(ex.getPayload(), Matchers.equalTo("some error message"));
    }

    @Test
    void shouldHandleFailedSnapshotVerification() throws Exception {
        Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        RoutingContext ctx = Mockito.mock(RoutingContext.class);
        HttpServerRequest req = Mockito.mock(HttpServerRequest.class);
        Mockito.when(ctx.request()).thenReturn(req);
        Mockito.when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
        HttpServerResponse resp = Mockito.mock(HttpServerResponse.class);
        Mockito.when(ctx.response()).thenReturn(resp);
        Mockito.when(ctx.pathParam("targetId")).thenReturn("someHost");

        HyperlinkedSerializableRecordingDescriptor snapshotDescriptor =
                Mockito.mock(HyperlinkedSerializableRecordingDescriptor.class);
        CompletableFuture<HyperlinkedSerializableRecordingDescriptor> future1 =
                Mockito.mock(CompletableFuture.class);
        Mockito.when(recordingTargetHelper.createSnapshot(Mockito.any(ConnectionDescriptor.class)))
                .thenReturn(future1);
        Mockito.when(future1.get()).thenReturn(snapshotDescriptor);
        Mockito.when(snapshotDescriptor.getName()).thenReturn("snapshot-1");

        CompletableFuture<Boolean> future2 = Mockito.mock(CompletableFuture.class);
        Mockito.when(
                        recordingTargetHelper.verifySnapshot(
                                Mockito.any(ConnectionDescriptor.class),
                                Mockito.any(HyperlinkedSerializableRecordingDescriptor.class)))
                .thenReturn(future2);
        Mockito.when(future2.get()).thenReturn(false);

        handler.handle(ctx);

        Mockito.verify(resp).setStatusCode(202);
        Mockito.verify(resp)
                .end(
                        "Snapshot snapshot-1 failed to create: The resultant recording was"
                                + " unreadable for some reason, likely due to a lack of Active,"
                                + " non-Snapshot source recordings to take event data from.");
    }
}
