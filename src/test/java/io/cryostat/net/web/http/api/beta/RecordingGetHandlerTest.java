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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.net.web.http.api.v2.ApiException;
import io.cryostat.net.web.http.api.v2.IntermediateResponse;
import io.cryostat.net.web.http.api.v2.RequestParameters;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingNotFoundException;

import com.google.gson.Gson;
import io.vertx.core.http.HttpMethod;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordingGetHandlerTest {

    RecordingGetHandler handler;
    @Mock AuthManager authManager;
    @Mock CredentialsManager credentialsManager;
    @Mock Gson gson;
    @Mock RecordingArchiveHelper recordingArchiveHelper;

    @BeforeEach
    void setup() {
        this.handler =
                new RecordingGetHandler(
                        authManager, credentialsManager, gson, recordingArchiveHelper);
    }

    @Nested
    class ApiSpec {

        @Test
        void shouldRequireAuthentication() {
            Assertions.assertTrue(handler.requiresAuthentication());
        }

        @Test
        void shouldBeV2Handler() {
            MatcherAssert.assertThat(handler.apiVersion(), Matchers.equalTo(ApiVersion.BETA));
        }

        @Test
        void shouldHandleGETRequest() {
            MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.GET));
        }

        @Test
        void shouldHaveExpectedRequiredPermissions() {
            MatcherAssert.assertThat(
                    handler.resourceActions(),
                    Matchers.equalTo(Set.of(ResourceAction.READ_RECORDING)));
        }

        @Test
        void shouldHandleCorrectPath() {
            MatcherAssert.assertThat(
                    handler.path(),
                    Matchers.equalTo("/api/beta/recordings/:sourceTarget/:recordingName"));
        }

        @Test
        void shouldProduceOctetStream() {
            MatcherAssert.assertThat(
                    handler.produces(), Matchers.equalTo(List.of(HttpMimeType.OCTET_STREAM)));
        }

        @Test
        void shouldNotBeAsync() {
            Assertions.assertFalse(handler.isAsync());
        }
    }

    @Nested
    class Behaviour {

        @Mock RequestParameters params;

        @Test
        void shouldThrow404IfNoMatchingRecordingFound() throws Exception {
            String recordingName = "someRecording";
            String sourceTarget = "someTarget";
            when(params.getPathParams())
                    .thenReturn(
                            Map.of("sourceTarget", sourceTarget, "recordingName", recordingName));

            Future<Path> future =
                    CompletableFuture.failedFuture(
                            new RecordingNotFoundException(sourceTarget, recordingName));
            when(recordingArchiveHelper.getRecordingPath(Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(future);

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(params));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(404));
        }

        @Test
        void shouldHandleSuccessfulGETRequest() throws Exception {
            String recordingName = "someRecording";
            String sourceTarget = "someTarget";
            when(params.getPathParams())
                    .thenReturn(
                            Map.of("recordingName", recordingName, "sourceTarget", sourceTarget));

            CompletableFuture<Path> future = Mockito.mock(CompletableFuture.class);
            when(recordingArchiveHelper.getRecordingPath(Mockito.anyString(), Mockito.anyString()))
                    .thenReturn(future);
            Path archivedRecording = Mockito.mock(Path.class);
            when(future.get()).thenReturn(archivedRecording);

            IntermediateResponse<Path> response = handler.handle(params);

            MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(200));
            MatcherAssert.assertThat(response.getBody(), Matchers.equalTo(archivedRecording));

            verify(recordingArchiveHelper)
                    .getRecordingPath(Mockito.eq(sourceTarget), Mockito.eq(recordingName));
        }
    }
}
