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

package io.cryostat.net.web.http.api.beta;

import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.openjdk.jmc.rjmx.services.jfr.IFlightRecorderService;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.SecurityContext;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.net.web.http.api.v2.ApiException;
import io.cryostat.net.web.http.api.v2.IntermediateResponse;
import io.cryostat.net.web.http.api.v2.RequestParameters;
import io.cryostat.recordings.RecordingMetadataManager;
import io.cryostat.recordings.RecordingMetadataManager.Metadata;
import io.cryostat.recordings.RecordingNotFoundException;
import io.cryostat.recordings.RecordingTargetHelper;

import com.google.gson.Gson;
import io.vertx.core.MultiMap;
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
public class TargetRecordingMetadataLabelsPostHandlerTest {
    TargetRecordingMetadataLabelsPostHandler handler;
    @Mock AuthManager authManager;
    @Mock CredentialsManager credentialsManager;
    @Mock Gson gson;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock RecordingTargetHelper recordingTargetHelper;
    @Mock RecordingMetadataManager recordingMetadataManager;
    @Mock Logger logger;
    @Mock ConnectionDescriptor connectionDescriptor;
    @Mock JFRConnection connection;
    @Mock IFlightRecorderService service;
    @Mock RequestParameters requestParameters;

    @BeforeEach
    void setup() {
        this.handler =
                new TargetRecordingMetadataLabelsPostHandler(
                        authManager,
                        credentialsManager,
                        gson,
                        targetConnectionManager,
                        recordingTargetHelper,
                        recordingMetadataManager,
                        logger);
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
                            "/api/beta/targets/:targetId/recordings/:recordingName/metadata/labels"));
        }

        @Test
        void shouldHaveExpectedRequiredPermissions() {
            MatcherAssert.assertThat(
                    handler.resourceActions(),
                    Matchers.equalTo(
                            Set.of(
                                    ResourceAction.READ_TARGET,
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
            String targetId = "fooTarget";
            Map<String, String> labels = Map.of("key", "value");
            Metadata metadata = new Metadata(SecurityContext.DEFAULT, labels);
            String requestLabels = labels.toString();
            Map<String, String> params = Mockito.mock(Map.class);

            when(requestParameters.getPathParams()).thenReturn(params);
            when(params.get("recordingName")).thenReturn(recordingName);
            when(params.get("targetId")).thenReturn(targetId);
            when(requestParameters.getBody()).thenReturn(requestLabels);
            when(requestParameters.getHeaders()).thenReturn(MultiMap.caseInsensitiveMultiMap());

            Optional<IRecordingDescriptor> descriptor = Mockito.mock(Optional.class);
            when(recordingTargetHelper.getDescriptorByName(connection, recordingName))
                    .thenReturn(descriptor);
            when(descriptor.isPresent()).thenReturn(true);

            when(targetConnectionManager.executeConnectedTask(Mockito.any(), Mockito.any()))
                    .thenAnswer(
                            arg0 ->
                                    ((TargetConnectionManager.ConnectedTask<Object>)
                                                    arg0.getArgument(1))
                                            .execute(connection));

            when(recordingMetadataManager.parseRecordingLabels(requestLabels)).thenReturn(labels);
            when(recordingMetadataManager.setRecordingMetadata(
                            Mockito.any(),
                            Mockito.anyString(),
                            Mockito.any(),
                            Mockito.anyBoolean()))
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
            String targetId = "fooTarget";
            String labels = Map.of("key", "value").toString();
            Map<String, String> params = Mockito.mock(Map.class);
            when(requestParameters.getHeaders()).thenReturn(MultiMap.caseInsensitiveMultiMap());

            when(requestParameters.getPathParams()).thenReturn(params);
            when(params.get("recordingName")).thenReturn(recordingName);
            when(params.get("targetId")).thenReturn(targetId);
            when(requestParameters.getBody()).thenReturn(labels);

            Optional<IRecordingDescriptor> descriptor = Mockito.mock(Optional.class);
            when(recordingTargetHelper.getDescriptorByName(connection, recordingName))
                    .thenReturn(descriptor);
            when(descriptor.isPresent()).thenReturn(false);

            when(targetConnectionManager.executeConnectedTask(Mockito.any(), Mockito.any()))
                    .thenAnswer(
                            arg0 ->
                                    ((TargetConnectionManager.ConnectedTask<Object>)
                                                    arg0.getArgument(1))
                                            .execute(connection));

            ApiException ex =
                    Assertions.assertThrows(
                            ApiException.class, () -> handler.handle(requestParameters));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(404));
            Assertions.assertTrue(
                    ExceptionUtils.getRootCause(ex) instanceof RecordingNotFoundException);
        }
    }
}
