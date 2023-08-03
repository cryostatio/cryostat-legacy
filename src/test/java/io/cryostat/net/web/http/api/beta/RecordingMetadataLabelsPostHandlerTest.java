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

import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.net.web.http.api.v2.ApiException;
import io.cryostat.net.web.http.api.v2.IntermediateResponse;
import io.cryostat.net.web.http.api.v2.RequestParameters;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingMetadataManager;
import io.cryostat.recordings.RecordingMetadataManager.Metadata;
import io.cryostat.recordings.RecordingNotFoundException;

import com.google.gson.Gson;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.exception.ExceptionUtils;
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
public class RecordingMetadataLabelsPostHandlerTest {
    RecordingMetadataLabelsPostHandler handler;
    @Mock AuthManager authManager;
    @Mock CredentialsManager credentialsManager;
    @Mock Gson gson;
    @Mock RecordingArchiveHelper recordingArchiveHelper;
    @Mock RecordingMetadataManager recordingMetadataManager;
    @Mock RequestParameters params;
    @Mock ConnectionDescriptor connectionDescriptor;
    @Mock IRecordingDescriptor descriptor;
    @Mock JFRConnection connection;
    @Mock IFlightRecorderService service;
    @Mock RequestParameters requestParameters;

    @BeforeEach
    void setup() {
        this.handler =
                new RecordingMetadataLabelsPostHandler(
                        authManager,
                        credentialsManager,
                        gson,
                        recordingArchiveHelper,
                        recordingMetadataManager);
    }

    @Nested
    class ApiSpec {

        @Test
        void shouldRequireAuthentication() {
            Assertions.assertTrue(handler.requiresAuthentication());
        }

        @Test
        void shouldBeBetaAPI() {
            MatcherAssert.assertThat(handler.apiVersion(), Matchers.equalTo(ApiVersion.BETA));
        }

        @Test
        void shouldHavePOSTMethod() {
            MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.POST));
        }

        @Test
        void shouldHaveTargetsPath() {
            MatcherAssert.assertThat(
                    handler.path(),
                    Matchers.equalTo(
                            "/api/beta/recordings/:sourceTarget/:recordingName/metadata/labels"));
        }

        @Test
        void shouldHaveExpectedRequiredPermissions() {
            MatcherAssert.assertThat(
                    handler.resourceActions(),
                    Matchers.equalTo(
                            Set.of(
                                    ResourceAction.READ_RECORDING,
                                    ResourceAction.UPDATE_RECORDING)));
        }

        @Test
        void shouldProduceJson() {
            MatcherAssert.assertThat(
                    handler.produces(), Matchers.equalTo(List.of(HttpMimeType.JSON)));
        }

        @Test
        void shouldNotBeAsync() {
            Assertions.assertFalse(handler.isAsync());
        }

        @Test
        void shouldBeOrdered() {
            Assertions.assertTrue(handler.isOrdered());
        }
    }

    @Nested
    class Behaviour {
        @Test
        void shouldUpdateLabels() throws Exception {
            String recordingName = "someRecording";
            String sourceTarget = "someTarget";
            Map<String, String> labels = Map.of("key", "value");
            Metadata metadata = new Metadata(labels);
            String requestLabels = labels.toString();
            Map<String, String> params = Mockito.mock(Map.class);

            when(requestParameters.getPathParams()).thenReturn(params);
            when(params.get("recordingName")).thenReturn(recordingName);
            when(params.get("sourceTarget")).thenReturn(sourceTarget);
            when(requestParameters.getBody()).thenReturn(requestLabels);

            when(recordingArchiveHelper.getRecordingPath(recordingName))
                    .thenReturn(CompletableFuture.completedFuture(Path.of(recordingName)));

            when(recordingMetadataManager.parseRecordingLabels(requestLabels)).thenReturn(labels);

            when(recordingMetadataManager.setRecordingMetadata(
                            new ConnectionDescriptor(sourceTarget), recordingName, metadata, true))
                    .thenReturn(CompletableFuture.completedFuture(metadata));

            IntermediateResponse<Metadata> response = handler.handle(requestParameters);
            MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(200));
            MatcherAssert.assertThat(response.getBody(), Matchers.equalTo(metadata));
        }

        @Test
        void shouldThrow400OnEmptyLabels() throws Exception {
            Map<String, String> params = Mockito.mock(Map.class);
            when(requestParameters.getPathParams()).thenReturn(params);
            when(params.get("recordingName")).thenReturn("someRecording");
            when(params.get("sourceTarget")).thenReturn("someTarget");
            when(requestParameters.getBody()).thenReturn("invalid");
            Mockito.doThrow(new IllegalArgumentException())
                    .when(recordingMetadataManager)
                    .parseRecordingLabels("invalid");
            ApiException ex =
                    Assertions.assertThrows(
                            ApiException.class, () -> handler.handle(requestParameters));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
        }

        @Test
        void shouldThrowWhenRecordingNotFound() throws Exception {
            String recordingName = "someNonExistentRecording";
            String labels = Map.of("key", "value").toString();
            Map<String, String> params = Mockito.mock(Map.class);

            when(requestParameters.getPathParams()).thenReturn(params);
            when(params.get("recordingName")).thenReturn(recordingName);
            when(params.get("sourceTarget")).thenReturn("someTarget");
            when(requestParameters.getBody()).thenReturn(labels);

            when(recordingArchiveHelper.getRecordingPath(recordingName))
                    .thenReturn(
                            CompletableFuture.failedFuture(
                                    new RecordingNotFoundException(
                                            RecordingArchiveHelper.ARCHIVES, recordingName)));

            ApiException ex =
                    Assertions.assertThrows(
                            ApiException.class, () -> handler.handle(requestParameters));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(404));
            Assertions.assertTrue(
                    ExceptionUtils.getRootCause(ex) instanceof RecordingNotFoundException);
        }
    }
}
