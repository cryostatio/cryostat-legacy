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

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.core.net.JFRConnection;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.recordings.RecordingOptionsBuilderFactory;
import io.cryostat.recordings.RecordingTargetHelper;
import io.cryostat.recordings.RecordingTargetHelper.SnapshotCreationException;
import io.cryostat.recordings.RecordingTargetHelper.SnapshotMinimalDescriptor;
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
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class TargetSnapshotPostHandlerTest {

    TargetSnapshotPostHandler handler;
    @Mock AuthManager auth;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock RecordingTargetHelper recordingTargetHelper;

    @BeforeEach
    void setup() {
        this.handler =
                new TargetSnapshotPostHandler(
                        auth,
                        targetConnectionManager,
                        recordingTargetHelper);
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

        JFRConnection conn = Mockito.mock(JFRConnection.class);
        Mockito.when(
                        targetConnectionManager.executeConnectedTask(
                                Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                .thenAnswer(
                        new Answer() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                TargetConnectionManager.ConnectedTask task =
                                        (TargetConnectionManager.ConnectedTask)
                                                invocation.getArgument(1);
                                return task.execute(conn);
                            }
                        });
        
        SnapshotMinimalDescriptor snapshot = Mockito.mock(SnapshotMinimalDescriptor.class);
        CompletableFuture<SnapshotMinimalDescriptor> future1  = Mockito.mock(CompletableFuture.class);
        Mockito.when(recordingTargetHelper.createSnapshot(conn)).thenReturn(future1);
        Mockito.when(future1.get()).thenReturn(snapshot);
        Mockito.when(snapshot.getName()).thenReturn("thesnapshot-1234");

        CompletableFuture<Boolean> future2 = Mockito.mock(CompletableFuture.class);
        Mockito.when(recordingTargetHelper.verifySnapshot(Mockito.any(ConnectionDescriptor.class), Mockito.eq("thesnapshot-1234"))).thenReturn(future2);
        Mockito.when(future2.get()).thenReturn(true);

        handler.handle(ctx);

        Mockito.verify(resp).setStatusCode(200);
        Mockito.verify(resp).end("thesnapshot-1234");
    }

    @Test
    void shouldHandleSnapshotCreationError() throws Exception {
        Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        RoutingContext ctx = Mockito.mock(RoutingContext.class);
        HttpServerRequest req = Mockito.mock(HttpServerRequest.class);
        Mockito.when(ctx.request()).thenReturn(req);
        Mockito.when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
        HttpServerResponse resp = Mockito.mock(HttpServerResponse.class);
        Mockito.when(ctx.response()).thenReturn(resp);
        Mockito.when(ctx.pathParam("targetId")).thenReturn("someHost");

        JFRConnection conn = Mockito.mock(JFRConnection.class);
        Mockito.when(
                        targetConnectionManager.executeConnectedTask(
                                Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                .thenAnswer(
                        new Answer() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                TargetConnectionManager.ConnectedTask task =
                                        (TargetConnectionManager.ConnectedTask)
                                                invocation.getArgument(1);
                                return task.execute(conn);
                            }
                        });

        SnapshotMinimalDescriptor snapshot = Mockito.mock(SnapshotMinimalDescriptor.class);
        CompletableFuture<SnapshotMinimalDescriptor> future1  = Mockito.mock(CompletableFuture.class);
        Mockito.when(recordingTargetHelper.createSnapshot(conn)).thenReturn(future1);
        Mockito.when(future1.get()).thenReturn(snapshot);
        Mockito.when(snapshot.getName()).thenReturn("thesnapshot-1234");

        CompletableFuture<Boolean> future2 = Mockito.mock(CompletableFuture.class);
        Mockito.when(recordingTargetHelper.verifySnapshot(Mockito.any(ConnectionDescriptor.class), Mockito.eq("thesnapshot-1234"))).thenReturn(future2);
        ExecutionException e = Mockito.mock(ExecutionException.class);
        Mockito.when(future2.get()).thenThrow(e);
        Mockito.when(e.getCause())
                .thenReturn(new SnapshotCreationException("thesnapshot-1234"));

        HttpStatusException ex =
                Assertions.assertThrows(HttpStatusException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(500));
        MatcherAssert.assertThat(
                ex.getPayload(),
                Matchers.equalTo(
                        "An error occured during the creation of snapshot thesnapshot-1234"));
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

        JFRConnection conn = Mockito.mock(JFRConnection.class);
        Mockito.when(
                        targetConnectionManager.executeConnectedTask(
                                Mockito.any(ConnectionDescriptor.class), Mockito.any()))
                .thenAnswer(
                        new Answer() {
                            @Override
                            public Object answer(InvocationOnMock invocation) throws Throwable {
                                TargetConnectionManager.ConnectedTask task =
                                        (TargetConnectionManager.ConnectedTask)
                                                invocation.getArgument(1);
                                return task.execute(conn);
                            }
                        });

        SnapshotMinimalDescriptor snapshot = Mockito.mock(SnapshotMinimalDescriptor.class);
        CompletableFuture<SnapshotMinimalDescriptor> future1  = Mockito.mock(CompletableFuture.class);
        Mockito.when(recordingTargetHelper.createSnapshot(conn)).thenReturn(future1);
        Mockito.when(future1.get()).thenReturn(snapshot);
        Mockito.when(snapshot.getName()).thenReturn("thesnapshot-1234");

        CompletableFuture<Boolean> future2 = Mockito.mock(CompletableFuture.class);
        Mockito.when(recordingTargetHelper.verifySnapshot(Mockito.any(ConnectionDescriptor.class), Mockito.eq("thesnapshot-1234"))).thenReturn(future2);
        Mockito.when(future2.get()).thenReturn(false);

        handler.handle(ctx);

        Mockito.verify(resp).setStatusCode(202);
        Mockito.verify(resp)
                .setStatusMessage(
                        "Snapshot thesnapshot-1234 failed to create: The resultant recording was unreadable for some reason, possibly due to a lack of Active, non-Snapshot source recordings to take event data from");
        Mockito.verify(resp).end();
    }
}
