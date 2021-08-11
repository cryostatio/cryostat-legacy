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
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;

import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;

import io.cryostat.core.net.JFRConnection;
import io.cryostat.core.sys.Clock;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.platform.PlatformClient;
import io.cryostat.recordings.EmptyRecordingException;
import io.cryostat.recordings.RecordingArchiveHelper;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
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

        CompletableFuture<String> future = new CompletableFuture<>();
        future.complete("some-Alias-2_someRecording_" + timestamp + ".jfr");
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
                .thenThrow(new EmptyRecordingException(new IOException()));

        patchSave.handle(ctx, new ConnectionDescriptor(targetId));

        InOrder inOrder = Mockito.inOrder(resp);
        inOrder.verify(resp).setStatusCode(204);
        inOrder.verify(resp).end();
    }
}
