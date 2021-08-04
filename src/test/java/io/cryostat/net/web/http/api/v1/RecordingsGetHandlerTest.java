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
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.cryostat.MainModule;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.WebServer;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import io.cryostat.MainModule;
import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.rules.ArchivePathException;
import io.cryostat.rules.ArchivedRecordingInfo;

import com.google.gson.Gson;
import io.vertx.core.http.HttpMethod;
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
class RecordingsGetHandlerTest {

    RecordingsGetHandler handler;
    @Mock AuthManager auth;
    @Mock RecordingArchiveHelper recordingArchiveHelper;
    @Mock Logger logger;
    Gson gson = MainModule.provideGson(logger);

    @BeforeEach
    void setup() {
        this.handler = new RecordingsGetHandler(auth, recordingArchiveHelper, gson);
    }

    @Test
    void shouldHandleGETRequest() {
        MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.GET));
    }

    @Test
    void shouldHandleCorrectPath() {
        MatcherAssert.assertThat(handler.path(), Matchers.equalTo("/api/v1/recordings"));
    }

    @Test
    void shouldHaveExpectedRequiredPermissions() {
        MatcherAssert.assertThat(
                handler.resourceActions(), Matchers.equalTo(Set.of(ResourceAction.READ_RECORDING)));
    }

    @Test
    void shouldRespondWith501IfDirectoryDoesNotExist() throws Exception {
        RoutingContext ctx = Mockito.mock(RoutingContext.class);
        ArchivePathException e = new ArchivePathException("/flightrecordings", "does not exist");

        Mockito.when(recordingArchiveHelper.getRecordings()).thenThrow(e);

        HttpStatusException httpEx =
                Assertions.assertThrows(
                        HttpStatusException.class, () -> handler.handleAuthenticated(ctx));
        MatcherAssert.assertThat(httpEx.getStatusCode(), Matchers.equalTo(501));
        MatcherAssert.assertThat(
                httpEx.getPayload(),
                Matchers.equalTo("Archive path /flightrecordings does not exist"));
    }

    @Test
    void shouldRespondWith501IfDirectoryNotReadable() throws Exception {
        RoutingContext ctx = Mockito.mock(RoutingContext.class);
        ArchivePathException e = new ArchivePathException("/flightrecordings", "is not readable");

        Mockito.when(recordingArchiveHelper.getRecordings()).thenThrow(e);

        HttpStatusException httpEx =
                Assertions.assertThrows(
                        HttpStatusException.class, () -> handler.handleAuthenticated(ctx));
        MatcherAssert.assertThat(httpEx.getStatusCode(), Matchers.equalTo(501));
        MatcherAssert.assertThat(
                httpEx.getPayload(),
                Matchers.equalTo("Archive path /flightrecordings is not readable"));
    }

    @Test
    void shouldRespondWith501IfPathNotDirectory() throws Exception {
        RoutingContext ctx = Mockito.mock(RoutingContext.class);
        ArchivePathException e =
                new ArchivePathException("/flightrecordings", "is not a directory");

        Mockito.when(recordingArchiveHelper.getRecordings()).thenThrow(e);

        HttpStatusException httpEx =
                Assertions.assertThrows(
                        HttpStatusException.class, () -> handler.handleAuthenticated(ctx));
        MatcherAssert.assertThat(httpEx.getStatusCode(), Matchers.equalTo(501));
        MatcherAssert.assertThat(
                httpEx.getPayload(),
                Matchers.equalTo("Archive path /flightrecordings is not a directory"));
    }

    @Test
    void testCustomJsonSerialization() throws Exception {
        CompletableFuture<List<ArchivedRecordingInfo>> listFuture = new CompletableFuture<>();
        listFuture.complete(
                List.of(
                        new ArchivedRecordingInfo(
                                "encodedServiceUriFoo",
                                "/some/path/download/recordingFoo",
                                "recordingFoo",
                                "/some/path/archive/recordingFoo")));
        Mockito.when(recordingArchiveHelper.getRecordings()).thenReturn(listFuture);

        RoutingContext ctx = Mockito.mock(RoutingContext.class);
        HttpServerResponse resp = Mockito.mock(HttpServerResponse.class);
        Mockito.when(ctx.response()).thenReturn(resp);
        HttpServerRequest req = Mockito.mock(HttpServerRequest.class);
        Mockito.when(ctx.request()).thenReturn(req);
        Mockito.when(auth.validateHttpHeader(Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        handler.handle(ctx);

        Mockito.verify(resp)
                .end(
                        "[{\"downloadUrl\":\"/some/path/download/recordingFoo\",\"name\":\"recordingFoo\",\"reportUrl\":\"/some/path/archive/recordingFoo\"}]");
    }
}
