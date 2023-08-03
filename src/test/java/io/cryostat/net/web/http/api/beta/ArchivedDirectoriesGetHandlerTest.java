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
package io.cryostat.net.web.http.api.beta;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.cryostat.MainModule;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingArchiveHelper.ArchiveDirectory;
import io.cryostat.recordings.RecordingMetadataManager.Metadata;
import io.cryostat.rules.ArchivePathException;
import io.cryostat.rules.ArchivedRecordingInfo;

import com.google.gson.Gson;
import io.vertx.core.http.HttpMethod;
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
class ArchivedDirectoriesGetHandlerTest {

    ArchivedDirectoriesGetHandler handler;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock RecordingArchiveHelper recordingArchiveHelper;
    @Mock Logger logger;
    Gson gson = MainModule.provideGson(logger);

    @Mock RoutingContext ctx;
    @Mock HttpServerResponse resp;

    @BeforeEach
    void setup() {
        this.handler =
                new ArchivedDirectoriesGetHandler(
                        auth, credentialsManager, recordingArchiveHelper, gson, logger);
    }

    @Test
    void shouldHandleGETRequest() {
        MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.GET));
    }

    @Test
    void shouldHandleCorrectPath() {
        MatcherAssert.assertThat(handler.path(), Matchers.equalTo("/api/beta/fs/recordings"));
    }

    @Test
    void shouldHaveExpectedRequiredPermissions() {
        MatcherAssert.assertThat(
                handler.resourceActions(), Matchers.equalTo(Set.of(ResourceAction.READ_RECORDING)));
    }

    @Test
    void shouldRespondWith500IfArchivePathException() throws Exception {
        Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        Mockito.when(ctx.response()).thenReturn(resp);
        Mockito.when(
                        resp.putHeader(
                                Mockito.any(CharSequence.class), Mockito.any(CharSequence.class)))
                .thenReturn(resp);

        CompletableFuture<List<ArchiveDirectory>> future = Mockito.mock(CompletableFuture.class);
        Mockito.when(recordingArchiveHelper.getRecordingsAndDirectories()).thenReturn(future);
        ExecutionException e = Mockito.mock(ExecutionException.class);
        Mockito.when(future.get()).thenThrow(e);
        Mockito.when(e.getCause()).thenReturn(new ArchivePathException("/some/path", "test"));

        HttpException httpEx =
                Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(httpEx.getStatusCode(), Matchers.equalTo(500));
        MatcherAssert.assertThat(
                httpEx.getCause().getCause().getMessage(),
                Matchers.equalTo("Archive path /some/path test"));
    }

    @Test
    void testCustomJsonSerialization() throws Exception {
        CompletableFuture<List<ArchiveDirectory>> listFuture = new CompletableFuture<>();

        ArchivedRecordingInfo recording =
                new ArchivedRecordingInfo(
                        "encodedServiceUriFoo",
                        "recordingFoo",
                        "/some/path/download/recordingFoo",
                        "/some/path/archive/recordingFoo",
                        new Metadata(),
                        0,
                        0);

        listFuture.complete(
                List.of(
                        new ArchiveDirectory(
                                "encodedServiceUriFoo", "someJvmId", List.of(recording))));
        Mockito.when(recordingArchiveHelper.getRecordingsAndDirectories()).thenReturn(listFuture);

        RoutingContext ctx = Mockito.mock(RoutingContext.class);
        HttpServerResponse resp = Mockito.mock(HttpServerResponse.class);
        Mockito.when(ctx.response()).thenReturn(resp);
        HttpServerRequest req = Mockito.mock(HttpServerRequest.class);
        Mockito.when(ctx.request()).thenReturn(req);
        Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        handler.handle(ctx);

        Mockito.verify(resp)
                .end(
                        "[{\"connectUrl\":\"encodedServiceUriFoo\",\"jvmId\":\"someJvmId\",\"recordings\":[{\"downloadUrl\":\"/some/path/download/recordingFoo\",\"name\":\"recordingFoo\",\"reportUrl\":\"/some/path/archive/recordingFoo\",\"metadata\":{\"labels\":{}},\"size\":0,\"archivedTime\":0}]}]");
    }
}
