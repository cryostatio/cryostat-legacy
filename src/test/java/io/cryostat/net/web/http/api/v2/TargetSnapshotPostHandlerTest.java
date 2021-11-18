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
package io.cryostat.net.web.http.api.v2;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.common.unit.IQuantity;
import org.openjdk.jmc.common.unit.QuantityConversionException;
import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;
import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.MainModule;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.WebServer;
import io.cryostat.recordings.RecordingOptionsBuilderFactory;
import io.cryostat.recordings.RecordingTargetHelper;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class TargetSnapshotPostHandlerTest {

    TargetSnapshotPostHandler handler;
    @Mock AuthManager auth;
    @Mock WebServer webServer;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock RecordingOptionsBuilderFactory recordingOptionsBuilderFactory;
    @Mock RecordingTargetHelper recordingTargetHelper;
    @Mock Logger logger;
    Gson gson = MainModule.provideGson(logger);

    @BeforeEach
    void setup() {
        this.handler =
                new TargetSnapshotPostHandler(
                        auth,
                        targetConnectionManager,
                        () -> webServer,
                        recordingOptionsBuilderFactory,
                        recordingTargetHelper,
                        gson);
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
        Mockito.when(ctx.pathParams()).thenReturn(Map.of("targetId", "someHost"));

        IRecordingDescriptor recordingDescriptor = createDescriptor("snapshot");

        IFlightRecorderService svc = Mockito.mock(IFlightRecorderService.class);
        JFRConnection conn = Mockito.mock(JFRConnection.class);
        Mockito.when(conn.getService()).thenReturn(svc);
        Mockito.when(svc.getSnapshotRecording()).thenReturn(recordingDescriptor);

        RecordingOptionsBuilder recordingOptionsBuilder =
                Mockito.mock(RecordingOptionsBuilder.class);
        Mockito.when(recordingOptionsBuilderFactory.create(svc))
                .thenReturn(recordingOptionsBuilder);
        IConstrainedMap map = Mockito.mock(IConstrainedMap.class);
        Mockito.when(recordingOptionsBuilder.build()).thenReturn(map);

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

        Mockito.when(webServer.getDownloadURL(Mockito.any(), Mockito.any()))
                .thenReturn("http://example.com/download");
        Mockito.when(webServer.getReportURL(Mockito.any(), Mockito.any()))
                .thenReturn("http://example.com/report");

        Optional<InputStream> snapshotOptional = Mockito.mock(Optional.class);
        Mockito.when(recordingTargetHelper.getRecording(Mockito.any(), Mockito.any()).get())
                .thenReturn(snapshotOptional);
        Mockito.when(snapshotOptional.isEmpty()).thenReturn(false);
        InputStream snapshot = Mockito.mock(InputStream.class);
        Mockito.when(snapshotOptional.get()).thenReturn(snapshot);
        Mockito.when(snapshot.read()).thenReturn(0);

        handler.handle(ctx);

        Mockito.verify(svc).getSnapshotRecording();
        Mockito.verify(recordingOptionsBuilder).name("snapshot-1");
        Mockito.verify(recordingOptionsBuilder).build();
        Mockito.verify(svc).updateRecordingOptions(recordingDescriptor, map);
        Mockito.verify(resp).setStatusCode(201);
        Mockito.verify(resp).putHeader(HttpHeaders.LOCATION, "http://example.com/download");

        ArgumentCaptor<String> endCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(resp).end(endCaptor.capture());
        Map parsed =
                gson.fromJson(
                        endCaptor.getValue(), new TypeToken<Map<String, Object>>() {}.getType());

        Map<String, Object> expected = new HashMap<>();
        Map<String, Object> meta = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> result = new HashMap<>();
        expected.put("meta", meta);
        meta.put("type", "text/plain");
        meta.put("status", "OK");
        expected.put("data", data);
        data.put("result", result);
        result.put("name", "snapshot-1");
        result.put("id", 1.0);
        result.put("downloadUrl", "http://example.com/download");
        result.put("reportUrl", "http://example.com/report");
        result.put("startTime", 0.0);
        result.put("state", "STOPPED");
        result.put("duration", 0.0);
        result.put("maxAge", 0.0);
        result.put("maxSize", 0.0);
        result.put("toDisk", false);
        result.put("continuous", false);
        MatcherAssert.assertThat(parsed, Matchers.equalTo(expected));
    }

    @Test
    void shouldHandleEmptySnapshot() throws Exception {
        Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        RoutingContext ctx = Mockito.mock(RoutingContext.class);
        HttpServerRequest req = Mockito.mock(HttpServerRequest.class);
        Mockito.when(ctx.request()).thenReturn(req);
        Mockito.when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
        HttpServerResponse resp = Mockito.mock(HttpServerResponse.class);
        Mockito.when(ctx.response()).thenReturn(resp);
        Mockito.when(ctx.pathParams()).thenReturn(Map.of("targetId", "someHost"));

        IRecordingDescriptor recordingDescriptor = createDescriptor("snapshot");

        IFlightRecorderService svc = Mockito.mock(IFlightRecorderService.class);
        JFRConnection conn = Mockito.mock(JFRConnection.class);
        Mockito.when(conn.getService()).thenReturn(svc);
        Mockito.when(svc.getSnapshotRecording()).thenReturn(recordingDescriptor);

        RecordingOptionsBuilder recordingOptionsBuilder =
                Mockito.mock(RecordingOptionsBuilder.class);
        Mockito.when(recordingOptionsBuilderFactory.create(svc))
                .thenReturn(recordingOptionsBuilder);
        IConstrainedMap map = Mockito.mock(IConstrainedMap.class);
        Mockito.when(recordingOptionsBuilder.build()).thenReturn(map);

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

        Mockito.when(webServer.getDownloadURL(Mockito.any(), Mockito.any()))
                .thenReturn("http://example.com/download");
        Mockito.when(webServer.getReportURL(Mockito.any(), Mockito.any()))
                .thenReturn("http://example.com/report");

        Optional<InputStream> snapshotOptional = Mockito.mock(Optional.class);
        Mockito.when(recordingTargetHelper.getRecording(Mockito.any(), Mockito.any()).get())
                .thenReturn(snapshotOptional);
        Mockito.when(snapshotOptional.isEmpty()).thenReturn(false);
        InputStream snapshot = Mockito.mock(InputStream.class);
        Mockito.when(snapshotOptional.get()).thenReturn(snapshot);
        Mockito.when(snapshot.read()).thenReturn(-1);
        Mockito.when(recordingTargetHelper.deleteRecording(Mockito.any(), Mockito.any()).get()).thenReturn(null);

        handler.handle(ctx);

        Mockito.verify(svc).getSnapshotRecording();
        Mockito.verify(recordingOptionsBuilder).name("snapshot-1");
        Mockito.verify(recordingOptionsBuilder).build();
        Mockito.verify(svc).updateRecordingOptions(recordingDescriptor, map);
        Mockito.verify(resp).setStatusCode(202);
        Mockito.verify(resp)
                .setStatusMessage(
                        "Snapshot failed to create: Cryostat is not aware of any Active, non-Snapshot source recordings to take event data from");
    }

    @Test
    void shouldRespond500IfSnapshotCreationFails() throws Exception {
        Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        RoutingContext ctx = Mockito.mock(RoutingContext.class);
        HttpServerRequest req = Mockito.mock(HttpServerRequest.class);
        Mockito.when(ctx.request()).thenReturn(req);
        Mockito.when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
        Mockito.when(ctx.pathParams()).thenReturn(Map.of("targetId", "someHost"));

        IRecordingDescriptor recordingDescriptor = createDescriptor("snapshot");

        IFlightRecorderService svc = Mockito.mock(IFlightRecorderService.class);
        JFRConnection conn = Mockito.mock(JFRConnection.class);
        Mockito.when(conn.getService()).thenReturn(svc);
        Mockito.when(svc.getSnapshotRecording()).thenReturn(recordingDescriptor);

        RecordingOptionsBuilder recordingOptionsBuilder =
                Mockito.mock(RecordingOptionsBuilder.class);
        Mockito.when(recordingOptionsBuilderFactory.create(svc))
                .thenReturn(recordingOptionsBuilder);
        IConstrainedMap map = Mockito.mock(IConstrainedMap.class);
        Mockito.when(recordingOptionsBuilder.build()).thenReturn(map);

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

        Mockito.when(webServer.getDownloadURL(Mockito.any(), Mockito.any()))
                .thenReturn("http://example.com/download");
        Mockito.when(webServer.getReportURL(Mockito.any(), Mockito.any()))
                .thenReturn("http://example.com/report");

        Optional<InputStream> snapshotOptional = Mockito.mock(Optional.class);
        Mockito.when(recordingTargetHelper.getRecording(Mockito.any(), Mockito.any()).get())
                .thenReturn(snapshotOptional);
        Mockito.when(snapshotOptional.isEmpty()).thenReturn(true);

        ApiException ex = Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(500));
        MatcherAssert.assertThat(
                ex.getFailureReason(),
                Matchers.equalTo("Successful creation verification of snapshot snapshot-1 failed"));

        Mockito.verify(svc).getSnapshotRecording();
        Mockito.verify(recordingOptionsBuilder).name("snapshot-1");
        Mockito.verify(recordingOptionsBuilder).build();
        Mockito.verify(svc).updateRecordingOptions(recordingDescriptor, map);
    }

    private static IRecordingDescriptor createDescriptor(String name)
            throws QuantityConversionException {
        IQuantity zeroQuantity = Mockito.mock(IQuantity.class);
        IRecordingDescriptor descriptor = Mockito.mock(IRecordingDescriptor.class);
        Mockito.when(descriptor.getId()).thenReturn(1L);
        Mockito.when(descriptor.getName()).thenReturn(name);
        Mockito.when(descriptor.getState()).thenReturn(IRecordingDescriptor.RecordingState.STOPPED);
        Mockito.when(descriptor.getStartTime()).thenReturn(zeroQuantity);
        Mockito.when(descriptor.getDuration()).thenReturn(zeroQuantity);
        Mockito.when(descriptor.isContinuous()).thenReturn(false);
        Mockito.when(descriptor.getToDisk()).thenReturn(false);
        Mockito.when(descriptor.getMaxSize()).thenReturn(zeroQuantity);
        Mockito.when(descriptor.getMaxAge()).thenReturn(zeroQuantity);
        return descriptor;
    }
}
