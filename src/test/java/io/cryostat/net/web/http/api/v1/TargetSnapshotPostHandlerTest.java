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
import io.vertx.ext.web.handler.impl.HttpStatusException;
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

//     @Test
//     void shouldCreateSnapshot() throws Exception {
//         Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
//                 .thenReturn(CompletableFuture.completedFuture(true));

//         RoutingContext ctx = Mockito.mock(RoutingContext.class);
//         HttpServerRequest req = Mockito.mock(HttpServerRequest.class);
//         Mockito.when(ctx.request()).thenReturn(req);
//         Mockito.when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
//         HttpServerResponse resp = Mockito.mock(HttpServerResponse.class);
//         Mockito.when(ctx.response()).thenReturn(resp);
//         Mockito.when(ctx.pathParam("targetId")).thenReturn("someHost");

//         HyperlinkedSerializableRecordingDescriptor snapshotDescriptor =
//                 Mockito.mock(HyperlinkedSerializableRecordingDescriptor.class);
//         CompletableFuture<HyperlinkedSerializableRecordingDescriptor> future1 =
//                 Mockito.mock(CompletableFuture.class);
//         Mockito.when(recordingTargetHelper.createSnapshot(Mockito.any(ConnectionDescriptor.class)))
//                 .thenReturn(future1);
//         Mockito.when(future1.get()).thenReturn(snapshotDescriptor);
//         Mockito.when(snapshotDescriptor.getName()).thenReturn("snapshot-1");

//         CompletableFuture<Boolean> future2 = Mockito.mock(CompletableFuture.class);
//         Mockito.when(
//                         recordingTargetHelper.verifySnapshot(
//                                 Mockito.any(ConnectionDescriptor.class), Mockito.eq("snapshot-1")))
//                 .thenReturn(future2);
//         Mockito.when(future2.get()).thenReturn(true);

//         handler.handle(ctx);

//         Mockito.verify(resp).setStatusCode(200);
//         Mockito.verify(resp).end("snapshot-1");
//     }

//     @Test
//     void shouldHandleSnapshotCreationExceptionDuringCreation() throws Exception {
//         Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
//                 .thenReturn(CompletableFuture.completedFuture(true));

//         RoutingContext ctx = Mockito.mock(RoutingContext.class);
//         HttpServerRequest req = Mockito.mock(HttpServerRequest.class);
//         Mockito.when(ctx.request()).thenReturn(req);
//         Mockito.when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
//         HttpServerResponse resp = Mockito.mock(HttpServerResponse.class);
//         Mockito.when(ctx.response()).thenReturn(resp);
//         Mockito.when(ctx.pathParam("targetId")).thenReturn("someHost");

//         CompletableFuture<HyperlinkedSerializableRecordingDescriptor> future1 =
//                 Mockito.mock(CompletableFuture.class);
//         Mockito.when(recordingTargetHelper.createSnapshot(Mockito.any(ConnectionDescriptor.class)))
//                 .thenReturn(future1);
//         Mockito.when(future1.get())
//                 .thenThrow(
//                         new ExecutionException(
//                                 new SnapshotCreationException("some error message")));

//         HttpStatusException ex =
//                 Assertions.assertThrows(HttpStatusException.class, () -> handler.handle(ctx));
//         MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(500));
//         MatcherAssert.assertThat(ex.getPayload(), Matchers.equalTo("some error message"));
//     }

//     @Test
//     void shouldHandleSnapshotCreationExceptionDuringVerification() throws Exception {
//         Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
//                 .thenReturn(CompletableFuture.completedFuture(true));

//         RoutingContext ctx = Mockito.mock(RoutingContext.class);
//         HttpServerRequest req = Mockito.mock(HttpServerRequest.class);
//         Mockito.when(ctx.request()).thenReturn(req);
//         Mockito.when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
//         HttpServerResponse resp = Mockito.mock(HttpServerResponse.class);
//         Mockito.when(ctx.response()).thenReturn(resp);
//         Mockito.when(ctx.pathParam("targetId")).thenReturn("someHost");

//         HyperlinkedSerializableRecordingDescriptor snapshotDescriptor =
//                 Mockito.mock(HyperlinkedSerializableRecordingDescriptor.class);
//         CompletableFuture<HyperlinkedSerializableRecordingDescriptor> future1 =
//                 Mockito.mock(CompletableFuture.class);
//         Mockito.when(recordingTargetHelper.createSnapshot(Mockito.any(ConnectionDescriptor.class)))
//                 .thenReturn(future1);
//         Mockito.when(future1.get()).thenReturn(snapshotDescriptor);
//         Mockito.when(snapshotDescriptor.getName()).thenReturn("snapshot-1");

//         CompletableFuture<Boolean> future2 = Mockito.mock(CompletableFuture.class);
//         Mockito.when(
//                         recordingTargetHelper.verifySnapshot(
//                                 Mockito.any(ConnectionDescriptor.class), Mockito.eq("snapshot-1")))
//                 .thenReturn(future2);
//         Mockito.when(future2.get())
//                 .thenThrow(
//                         new ExecutionException(
//                                 new SnapshotCreationException("some error message")));

//         HttpStatusException ex =
//                 Assertions.assertThrows(HttpStatusException.class, () -> handler.handle(ctx));
//         MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(500));
//         MatcherAssert.assertThat(ex.getPayload(), Matchers.equalTo("some error message"));
//     }

//     @Test
//     void shouldHandleFailedSnapshotVerification() throws Exception {
//         Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
//                 .thenReturn(CompletableFuture.completedFuture(true));

//         RoutingContext ctx = Mockito.mock(RoutingContext.class);
//         HttpServerRequest req = Mockito.mock(HttpServerRequest.class);
//         Mockito.when(ctx.request()).thenReturn(req);
//         Mockito.when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
//         HttpServerResponse resp = Mockito.mock(HttpServerResponse.class);
//         Mockito.when(ctx.response()).thenReturn(resp);
//         Mockito.when(ctx.pathParam("targetId")).thenReturn("someHost");

//         HyperlinkedSerializableRecordingDescriptor snapshotDescriptor =
//                 Mockito.mock(HyperlinkedSerializableRecordingDescriptor.class);
//         CompletableFuture<HyperlinkedSerializableRecordingDescriptor> future1 =
//                 Mockito.mock(CompletableFuture.class);
//         Mockito.when(recordingTargetHelper.createSnapshot(Mockito.any(ConnectionDescriptor.class)))
//                 .thenReturn(future1);
//         Mockito.when(future1.get()).thenReturn(snapshotDescriptor);
//         Mockito.when(snapshotDescriptor.getName()).thenReturn("snapshot-1");

//         CompletableFuture<Boolean> future2 = Mockito.mock(CompletableFuture.class);
//         Mockito.when(
//                         recordingTargetHelper.verifySnapshot(
//                                 Mockito.any(ConnectionDescriptor.class), Mockito.eq("snapshot-1")))
//                 .thenReturn(future2);
//         Mockito.when(future2.get()).thenReturn(false);

//         handler.handle(ctx);

//         Mockito.verify(resp).setStatusCode(202);
//         Mockito.verify(resp)
//                 .end(
//                         "Snapshot snapshot-1 failed to create: The resultant recording was unreadable for some reason, likely due to a lack of Active, non-Snapshot source recordings to take event data from.");
//     }
}
