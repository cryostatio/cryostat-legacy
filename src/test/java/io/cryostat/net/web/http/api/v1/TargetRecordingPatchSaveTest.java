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

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;

import io.cryostat.core.net.JFRConnection;
import io.cryostat.core.sys.Clock;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.platform.PlatformClient;
import io.cryostat.recordings.EmptyRecordingException;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.rules.ArchivedRecordingInfo;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TargetRecordingPatchSaveTest {

    TargetRecordingPatchSave patchSave;
    @Mock FileSystem fs;
    @Mock Path recordingsPath;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock Clock clock;
    @Mock PlatformClient platformClient;
    @Mock RecordingArchiveHelper recordingArchiveHelper;

    @Mock RoutingContext ctx;
    @Mock HttpServerResponse resp;
    @Mock JFRConnection jfrConnection;
    @Mock IFlightRecorderService service;

    String targetId = "fooTarget";
    String recordingName = "someRecording";

    @BeforeEach
    void setup() {
        this.patchSave = new TargetRecordingPatchSave(recordingArchiveHelper);
    }

    @Test
    void shouldSaveRecordingWithAlias() throws Exception {
        Mockito.when(ctx.pathParam("recordingName")).thenReturn(recordingName);
        Mockito.when(ctx.response()).thenReturn(resp);

        Instant now = Instant.now();
        String timestamp = now.truncatedTo(ChronoUnit.SECONDS).toString().replaceAll("[-:]+", "");

        CompletableFuture<ArchivedRecordingInfo> future = new CompletableFuture<>();
        ArchivedRecordingInfo info = Mockito.mock(ArchivedRecordingInfo.class);
        Mockito.when(info.getName()).thenReturn("some-Alias-2_someRecording_" + timestamp + ".jfr");
        future.complete(info);
        Mockito.when(recordingArchiveHelper.saveRecording(Mockito.any(), Mockito.any()))
                .thenReturn(future);

        patchSave.handle(ctx, new ConnectionDescriptor(targetId));

        InOrder inOrder = Mockito.inOrder(resp);
        inOrder.verify(resp).end("some-Alias-2_someRecording_" + timestamp + ".jfr");
    }

    @Test
    void shouldNotSaveEmptyRecording() throws Exception {
        Mockito.when(ctx.response()).thenReturn(resp);

        Mockito.when(recordingArchiveHelper.saveRecording(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.failedFuture(new EmptyRecordingException()));

        Assertions.assertThrows(
                ExecutionException.class,
                () -> {
                    try {
                        patchSave.handle(ctx, new ConnectionDescriptor(targetId));
                    } catch (ExecutionException ee) {
                        Assertions.assertTrue(ee.getCause() instanceof EmptyRecordingException);
                        throw ee;
                    }
                });

        InOrder inOrder = Mockito.inOrder(resp);
        inOrder.verify(resp).setStatusCode(204);
        inOrder.verify(resp).end();
    }
}
