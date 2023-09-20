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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.openjdk.jmc.common.unit.IConstrainedMap;
import org.openjdk.jmc.common.unit.IQuantity;
import org.openjdk.jmc.common.unit.QuantityConversionException;
import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.MainModule;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.CryostatFlightRecorderService;
import io.cryostat.core.net.JFRConnection;
import io.cryostat.core.templates.TemplateService;
import io.cryostat.core.templates.TemplateType;
import io.cryostat.net.AuthManager;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.WebServer;
import io.cryostat.recordings.RecordingMetadataManager;
import io.cryostat.recordings.RecordingMetadataManager.Metadata;
import io.cryostat.recordings.RecordingOptionsBuilderFactory;
import io.cryostat.recordings.RecordingTargetHelper;
import io.cryostat.recordings.RecordingTargetHelper.ReplacementPolicy;

import com.google.gson.Gson;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TargetRecordingsPostHandlerTest {

    TargetRecordingsPostHandler handler;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock RecordingTargetHelper recordingTargetHelper;
    @Mock RecordingOptionsBuilderFactory recordingOptionsBuilderFactory;
    @Mock WebServer webServer;
    @Mock RecordingMetadataManager recordingMetadataManager;
    @Mock Logger logger;
    Gson gson = MainModule.provideGson(logger);

    @Mock JFRConnection connection;
    @Mock CryostatFlightRecorderService service;
    @Mock TemplateService templateService;
    @Mock RoutingContext ctx;
    @Mock HttpServerRequest req;
    @Mock HttpServerResponse resp;

    @BeforeEach
    void setup() {
        this.handler =
                new TargetRecordingsPostHandler(
                        auth,
                        credentialsManager,
                        targetConnectionManager,
                        recordingTargetHelper,
                        recordingOptionsBuilderFactory,
                        () -> webServer,
                        recordingMetadataManager,
                        gson,
                        logger);
    }

    @Test
    void shouldHandlePOST() {
        MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.POST));
    }

    @Test
    void shouldHandleCorrectPath() {
        MatcherAssert.assertThat(
                handler.path(), Matchers.equalTo("/api/v1/targets/:targetId/recordings"));
    }

    @Test
    void shouldHaveExpectedRequiredPermissions() {
        MatcherAssert.assertThat(
                handler.resourceActions(),
                Matchers.equalTo(
                        Set.of(
                                ResourceAction.READ_TARGET,
                                ResourceAction.READ_TEMPLATE,
                                ResourceAction.CREATE_RECORDING,
                                ResourceAction.READ_RECORDING,
                                ResourceAction.UPDATE_TARGET)));
    }

    @Test
    void shouldStartRecording() throws Exception {
        Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        Mockito.when(targetConnectionManager.executeConnectedTask(Mockito.any(), Mockito.any()))
                .thenAnswer(
                        arg0 ->
                                ((TargetConnectionManager.ConnectedTask<Object>)
                                                arg0.getArgument(1))
                                        .execute(connection));
        Mockito.when(connection.getService()).thenReturn(service);
        IConstrainedMap<String> recordingOptions = Mockito.mock(IConstrainedMap.class);
        RecordingOptionsBuilder recordingOptionsBuilder =
                Mockito.mock(RecordingOptionsBuilder.class);
        Mockito.when(recordingOptionsBuilderFactory.create(Mockito.any()))
                .thenReturn(recordingOptionsBuilder);
        Mockito.when(recordingOptionsBuilder.name(Mockito.any()))
                .thenReturn(recordingOptionsBuilder);
        Mockito.when(recordingOptionsBuilder.duration(Mockito.anyLong()))
                .thenReturn(recordingOptionsBuilder);
        Mockito.when(recordingOptionsBuilder.toDisk(Mockito.anyBoolean()))
                .thenReturn(recordingOptionsBuilder);
        Mockito.when(recordingOptionsBuilder.maxAge(Mockito.anyLong()))
                .thenReturn(recordingOptionsBuilder);
        Mockito.when(recordingOptionsBuilder.maxSize(Mockito.anyLong()))
                .thenReturn(recordingOptionsBuilder);
        Mockito.when(recordingOptionsBuilder.build()).thenReturn(recordingOptions);

        Mockito.when(
                        webServer.getDownloadURL(
                                Mockito.any(JFRConnection.class), Mockito.anyString()))
                .thenReturn("example-download-url");
        Mockito.when(webServer.getReportURL(Mockito.any(JFRConnection.class), Mockito.anyString()))
                .thenReturn("example-report-url");

        Mockito.when(ctx.pathParam("targetId")).thenReturn("fooHost:9091");
        MultiMap attrs = MultiMap.caseInsensitiveMultiMap();
        Mockito.when(ctx.request()).thenReturn(req);
        Mockito.when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
        Mockito.when(req.formAttributes()).thenReturn(attrs);
        attrs.add("recordingName", "someRecording");
        attrs.add("events", "template=Foo,type=CUSTOM");
        attrs.add("duration", "10");
        attrs.add("toDisk", "true");
        attrs.add("maxAge", "50");
        attrs.add("maxSize", "64");
        attrs.add("archiveOnStop", "false");

        Mockito.when(ctx.response()).thenReturn(resp);
        Mockito.when(
                        resp.putHeader(
                                Mockito.any(CharSequence.class), Mockito.any(CharSequence.class)))
                .thenReturn(resp);

        IRecordingDescriptor descriptor = createDescriptor("someRecording");
        Mockito.when(
                        recordingTargetHelper.startRecording(
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.anyBoolean()))
                .thenReturn(descriptor);

        Mockito.when(recordingMetadataManager.getMetadata(Mockito.any(), Mockito.anyString()))
                .thenReturn(new Metadata());

        handler.handle(ctx);

        Mockito.verify(recordingOptionsBuilder).name("someRecording");
        Mockito.verify(recordingOptionsBuilder).duration(TimeUnit.SECONDS.toMillis(10));
        Mockito.verify(recordingOptionsBuilder).toDisk(true);
        Mockito.verify(recordingOptionsBuilder).maxAge(50L);
        Mockito.verify(recordingOptionsBuilder).maxSize(64L);

        ArgumentCaptor<ReplacementPolicy> replaceCaptor =
                ArgumentCaptor.forClass(ReplacementPolicy.class);

        ArgumentCaptor<ConnectionDescriptor> connectionDescriptorCaptor =
                ArgumentCaptor.forClass(ConnectionDescriptor.class);

        ArgumentCaptor<IConstrainedMap<String>> recordingOptionsCaptor =
                ArgumentCaptor.forClass(IConstrainedMap.class);

        ArgumentCaptor<String> templateNameCaptor = ArgumentCaptor.forClass(String.class);

        ArgumentCaptor<TemplateType> templateTypeCaptor =
                ArgumentCaptor.forClass(TemplateType.class);

        ArgumentCaptor<Metadata> metadataCaptor = ArgumentCaptor.forClass(Metadata.class);

        ArgumentCaptor<Boolean> archiveOnStopCaptor = ArgumentCaptor.forClass(Boolean.class);

        Mockito.verify(recordingTargetHelper)
                .startRecording(
                        replaceCaptor.capture(),
                        connectionDescriptorCaptor.capture(),
                        recordingOptionsCaptor.capture(),
                        templateNameCaptor.capture(),
                        templateTypeCaptor.capture(),
                        metadataCaptor.capture(),
                        archiveOnStopCaptor.capture());

        MatcherAssert.assertThat(
                replaceCaptor.getValue(), Matchers.equalTo(ReplacementPolicy.NEVER));

        ConnectionDescriptor connectionDescriptor = connectionDescriptorCaptor.getValue();
        MatcherAssert.assertThat(
                connectionDescriptor.getTargetId(), Matchers.equalTo("fooHost:9091"));
        MatcherAssert.assertThat(
                connectionDescriptor.getCredentials().isEmpty(), Matchers.is(true));

        IConstrainedMap<String> actualRecordingOptions = recordingOptionsCaptor.getValue();
        MatcherAssert.assertThat(actualRecordingOptions, Matchers.sameInstance(recordingOptions));

        MatcherAssert.assertThat(templateNameCaptor.getValue(), Matchers.equalTo("Foo"));

        MatcherAssert.assertThat(
                templateTypeCaptor.getValue(), Matchers.equalTo(TemplateType.CUSTOM));

        MatcherAssert.assertThat(metadataCaptor.getValue(), Matchers.equalTo(new Metadata()));

        Mockito.verify(resp).setStatusCode(201);
        Mockito.verify(resp).putHeader(HttpHeaders.LOCATION, "/someRecording");
        Mockito.verify(resp).putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        Mockito.verify(resp)
                .end(
                        "{\"downloadUrl\":\"example-download-url\",\"reportUrl\":\"example-report-url\",\"metadata\":{\"labels\":{}},\"archiveOnStop\":false,\"id\":1,\"name\":\"someRecording\",\"state\":\"STOPPED\",\"startTime\":0,\"duration\":0,\"continuous\":false,\"toDisk\":false,\"maxSize\":0,\"maxAge\":0}");
    }

    @ParameterizedTest
    @MethodSource("getRestartOptions")
    void shouldRestartRecording(String restart, String replace) throws Exception {
        Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        Mockito.when(targetConnectionManager.executeConnectedTask(Mockito.any(), Mockito.any()))
                .thenAnswer(
                        arg0 ->
                                ((TargetConnectionManager.ConnectedTask<Object>)
                                                arg0.getArgument(1))
                                        .execute(connection));

        IConstrainedMap<String> recordingOptions = Mockito.mock(IConstrainedMap.class);
        RecordingOptionsBuilder recordingOptionsBuilder =
                Mockito.mock(RecordingOptionsBuilder.class);
        Mockito.when(recordingOptionsBuilderFactory.create(Mockito.any()))
                .thenReturn(recordingOptionsBuilder);
        Mockito.when(recordingOptionsBuilder.name(Mockito.any()))
                .thenReturn(recordingOptionsBuilder);
        Mockito.when(recordingOptionsBuilder.build()).thenReturn(recordingOptions);

        Mockito.when(
                        webServer.getDownloadURL(
                                Mockito.any(JFRConnection.class), Mockito.anyString()))
                .thenReturn("example-download-url");
        Mockito.when(webServer.getReportURL(Mockito.any(JFRConnection.class), Mockito.anyString()))
                .thenReturn("example-report-url");

        IRecordingDescriptor descriptor = createDescriptor("someRecording");
        Mockito.when(
                        recordingTargetHelper.startRecording(
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.anyBoolean()))
                .thenReturn(descriptor);

        Mockito.when(recordingMetadataManager.getMetadata(Mockito.any(), Mockito.anyString()))
                .thenReturn(new Metadata());

        Mockito.when(ctx.pathParam("targetId")).thenReturn("fooHost:9091");
        MultiMap attrs = MultiMap.caseInsensitiveMultiMap();
        Mockito.when(ctx.request()).thenReturn(req);
        Mockito.when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
        Mockito.when(req.formAttributes()).thenReturn(attrs);
        Mockito.when(ctx.response()).thenReturn(resp);
        Mockito.when(
                        resp.putHeader(
                                Mockito.any(CharSequence.class), Mockito.any(CharSequence.class)))
                .thenReturn(resp);
        if (restart != null) {
            attrs.add("restart", restart);
        }
        if (replace != null) {
            attrs.add("replace", replace);
        }
        attrs.add("recordingName", "someRecording");
        attrs.add("events", "template=Foo");

        handler.handle(ctx);

        Mockito.verify(recordingOptionsBuilder).name("someRecording");

        ArgumentCaptor<ReplacementPolicy> replaceCaptor =
                ArgumentCaptor.forClass(ReplacementPolicy.class);

        ArgumentCaptor<ConnectionDescriptor> connectionDescriptorCaptor =
                ArgumentCaptor.forClass(ConnectionDescriptor.class);

        ArgumentCaptor<IConstrainedMap<String>> recordingOptionsCaptor =
                ArgumentCaptor.forClass(IConstrainedMap.class);

        ArgumentCaptor<String> templateNameCaptor = ArgumentCaptor.forClass(String.class);

        ArgumentCaptor<TemplateType> templateTypeCaptor =
                ArgumentCaptor.forClass(TemplateType.class);

        ArgumentCaptor<Metadata> metadataCaptor = ArgumentCaptor.forClass(Metadata.class);

        ArgumentCaptor<Boolean> archiveOnStopCaptor = ArgumentCaptor.forClass(Boolean.class);

        Mockito.verify(recordingTargetHelper)
                .startRecording(
                        replaceCaptor.capture(),
                        connectionDescriptorCaptor.capture(),
                        recordingOptionsCaptor.capture(),
                        templateNameCaptor.capture(),
                        templateTypeCaptor.capture(),
                        metadataCaptor.capture(),
                        archiveOnStopCaptor.capture());

        MatcherAssert.assertThat(
                replaceCaptor.getValue(), Matchers.equalTo(ReplacementPolicy.ALWAYS));

        ConnectionDescriptor connectionDescriptor = connectionDescriptorCaptor.getValue();
        MatcherAssert.assertThat(
                connectionDescriptor.getTargetId(), Matchers.equalTo("fooHost:9091"));
        MatcherAssert.assertThat(
                connectionDescriptor.getCredentials().isEmpty(), Matchers.is(true));

        IConstrainedMap<String> actualRecordingOptions = recordingOptionsCaptor.getValue();
        MatcherAssert.assertThat(actualRecordingOptions, Matchers.sameInstance(recordingOptions));

        MatcherAssert.assertThat(templateNameCaptor.getValue(), Matchers.equalTo("Foo"));

        MatcherAssert.assertThat(templateTypeCaptor.getValue(), Matchers.nullValue());

        MatcherAssert.assertThat(metadataCaptor.getValue(), Matchers.equalTo(new Metadata()));

        Mockito.verify(resp).setStatusCode(201);
        Mockito.verify(resp).putHeader(HttpHeaders.LOCATION, "/someRecording");
        Mockito.verify(resp).putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        Mockito.verify(resp)
                .end(
                        "{\"downloadUrl\":\"example-download-url\",\"reportUrl\":\"example-report-url\",\"metadata\":{\"labels\":{}},\"archiveOnStop\":false,\"id\":1,\"name\":\"someRecording\",\"state\":\"STOPPED\",\"startTime\":0,\"duration\":0,\"continuous\":false,\"toDisk\":false,\"maxSize\":0,\"maxAge\":0}");
    }

    private static Stream<Arguments> getRestartOptions() {
        return Stream.of(
                Arguments.of("true", null),
                Arguments.of(null, "always"),
                Arguments.of("true", "always"),
                Arguments.of("false", "always"),
                Arguments.of("anything", "always"));
    }

    @Test
    void shouldHandleNameCollision() throws Exception {
        Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        Mockito.when(targetConnectionManager.executeConnectedTask(Mockito.any(), Mockito.any()))
                .thenAnswer(
                        arg0 ->
                                ((TargetConnectionManager.ConnectedTask<Object>)
                                                arg0.getArgument(1))
                                        .execute(connection));

        IConstrainedMap<String> recordingOptions = Mockito.mock(IConstrainedMap.class);
        RecordingOptionsBuilder recordingOptionsBuilder =
                Mockito.mock(RecordingOptionsBuilder.class);
        Mockito.when(recordingOptionsBuilderFactory.create(Mockito.any()))
                .thenReturn(recordingOptionsBuilder);
        Mockito.when(recordingOptionsBuilder.name(Mockito.any()))
                .thenReturn(recordingOptionsBuilder);
        Mockito.when(recordingOptionsBuilder.build()).thenReturn(recordingOptions);
        Mockito.when(
                        recordingTargetHelper.startRecording(
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.anyBoolean()))
                .thenThrow(IllegalArgumentException.class);

        Mockito.when(ctx.pathParam("targetId")).thenReturn("fooHost:9091");
        MultiMap attrs = MultiMap.caseInsensitiveMultiMap();
        Mockito.when(ctx.request()).thenReturn(req);
        Mockito.when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
        Mockito.when(req.formAttributes()).thenReturn(attrs);
        Mockito.when(ctx.response()).thenReturn(resp);
        Mockito.when(
                        resp.putHeader(
                                Mockito.any(CharSequence.class), Mockito.any(CharSequence.class)))
                .thenReturn(resp);
        attrs.add("recordingName", "someRecording");
        attrs.add("events", "template=Foo");

        HttpException ex = Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
    }

    @Test
    void shouldHandleException() throws Exception {
        HttpException ex = Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(500));
    }

    private static IRecordingDescriptor createDescriptor(String name)
            throws QuantityConversionException {
        IQuantity zeroQuantity = Mockito.mock(IQuantity.class);
        IRecordingDescriptor descriptor = Mockito.mock(IRecordingDescriptor.class);
        Mockito.lenient().when(descriptor.getId()).thenReturn(1L);
        Mockito.lenient().when(descriptor.getName()).thenReturn(name);
        Mockito.lenient()
                .when(descriptor.getState())
                .thenReturn(IRecordingDescriptor.RecordingState.STOPPED);
        Mockito.lenient().when(descriptor.getStartTime()).thenReturn(zeroQuantity);
        Mockito.lenient().when(descriptor.getDuration()).thenReturn(zeroQuantity);
        Mockito.lenient().when(descriptor.isContinuous()).thenReturn(false);
        Mockito.lenient().when(descriptor.getToDisk()).thenReturn(false);
        Mockito.lenient().when(descriptor.getMaxSize()).thenReturn(zeroQuantity);
        Mockito.lenient().when(descriptor.getMaxAge()).thenReturn(zeroQuantity);
        return descriptor;
    }

    @ParameterizedTest
    @MethodSource("getRequestMaps")
    void shouldThrowInvalidOptionException(Map<String, String> requestValues) throws Exception {
        Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        IRecordingDescriptor existingRecording = createDescriptor("someRecording");

        Mockito.when(targetConnectionManager.executeConnectedTask(Mockito.any(), Mockito.any()))
                .thenAnswer(
                        arg0 ->
                                ((TargetConnectionManager.ConnectedTask<Object>)
                                                arg0.getArgument(1))
                                        .execute(connection));
        Mockito.when(connection.getService()).thenReturn(service);
        RecordingOptionsBuilder recordingOptionsBuilder =
                Mockito.mock(RecordingOptionsBuilder.class);
        Mockito.when(recordingOptionsBuilderFactory.create(Mockito.any()))
                .thenReturn(recordingOptionsBuilder);
        Mockito.when(recordingOptionsBuilder.name(Mockito.any()))
                .thenReturn(recordingOptionsBuilder);

        Mockito.when(ctx.pathParam("targetId")).thenReturn("fooHost:9091");
        MultiMap attrs = MultiMap.caseInsensitiveMultiMap();
        Mockito.when(ctx.request()).thenReturn(req);
        Mockito.when(ctx.response()).thenReturn(resp);
        Mockito.when(
                        resp.putHeader(
                                Mockito.any(CharSequence.class), Mockito.any(CharSequence.class)))
                .thenReturn(resp);
        Mockito.when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
        attrs.addAll(requestValues);
        attrs.add("recordingName", "someRecording");
        attrs.add("events", "template=Foo");
        Mockito.when(req.formAttributes()).thenReturn(attrs);

        HttpException ex = Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
    }

    private static Stream<Map<String, String>> getRequestMaps() {
        return Stream.of(
                Map.of("duration", ""),
                Map.of("duration", "t"),
                Map.of("duration", "90s"),
                Map.of("toDisk", ""),
                Map.of("toDisk", "5"),
                Map.of("toDisk", "T"),
                Map.of("toDisk", "false1"),
                Map.of("maxAge", ""),
                Map.of("maxAge", "true"),
                Map.of("maxAge", "1e3"),
                Map.of("maxAge", "5s"),
                Map.of("maxSize", ""),
                Map.of("maxSize", "0.5"),
                Map.of("maxSize", "s1"));
    }

    @Test
    void shouldStartRecordingAndArchiveOnStop() throws Exception {
        Mockito.when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        Mockito.when(targetConnectionManager.executeConnectedTask(Mockito.any(), Mockito.any()))
                .thenAnswer(
                        arg0 ->
                                ((TargetConnectionManager.ConnectedTask<Object>)
                                                arg0.getArgument(1))
                                        .execute(connection));
        Mockito.when(connection.getService()).thenReturn(service);
        IConstrainedMap<String> recordingOptions = Mockito.mock(IConstrainedMap.class);
        RecordingOptionsBuilder recordingOptionsBuilder =
                Mockito.mock(RecordingOptionsBuilder.class);
        Mockito.when(recordingOptionsBuilderFactory.create(Mockito.any()))
                .thenReturn(recordingOptionsBuilder);
        Mockito.when(recordingOptionsBuilder.name(Mockito.any()))
                .thenReturn(recordingOptionsBuilder);
        Mockito.when(recordingOptionsBuilder.duration(Mockito.anyLong()))
                .thenReturn(recordingOptionsBuilder);
        Mockito.when(recordingOptionsBuilder.toDisk(Mockito.anyBoolean()))
                .thenReturn(recordingOptionsBuilder);
        Mockito.when(recordingOptionsBuilder.maxAge(Mockito.anyLong()))
                .thenReturn(recordingOptionsBuilder);
        Mockito.when(recordingOptionsBuilder.maxSize(Mockito.anyLong()))
                .thenReturn(recordingOptionsBuilder);
        Mockito.when(recordingOptionsBuilder.build()).thenReturn(recordingOptions);

        Mockito.when(
                        webServer.getDownloadURL(
                                Mockito.any(JFRConnection.class), Mockito.anyString()))
                .thenReturn("example-download-url");
        Mockito.when(webServer.getReportURL(Mockito.any(JFRConnection.class), Mockito.anyString()))
                .thenReturn("example-report-url");

        Mockito.when(ctx.pathParam("targetId")).thenReturn("fooHost:9091");
        MultiMap attrs = MultiMap.caseInsensitiveMultiMap();
        Mockito.when(ctx.request()).thenReturn(req);
        Mockito.when(req.headers()).thenReturn(MultiMap.caseInsensitiveMultiMap());
        Mockito.when(req.formAttributes()).thenReturn(attrs);
        attrs.add("recordingName", "someRecording");
        attrs.add("events", "template=Foo,type=CUSTOM");
        attrs.add("duration", "10");
        attrs.add("toDisk", "true");
        attrs.add("maxAge", "50");
        attrs.add("maxSize", "64");
        attrs.add("archiveOnStop", "true");
        Mockito.when(ctx.response()).thenReturn(resp);
        Mockito.when(
                        resp.putHeader(
                                Mockito.any(CharSequence.class), Mockito.any(CharSequence.class)))
                .thenReturn(resp);

        IRecordingDescriptor descriptor = createDescriptor("someRecording");
        Mockito.when(
                        recordingTargetHelper.startRecording(
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.any(),
                                Mockito.anyBoolean()))
                .thenReturn(descriptor);

        Mockito.when(recordingMetadataManager.getMetadata(Mockito.any(), Mockito.anyString()))
                .thenReturn(new Metadata());

        handler.handle(ctx);

        Mockito.verify(recordingOptionsBuilder).name("someRecording");
        Mockito.verify(recordingOptionsBuilder).duration(TimeUnit.SECONDS.toMillis(10));
        Mockito.verify(recordingOptionsBuilder).toDisk(true);
        Mockito.verify(recordingOptionsBuilder).maxAge(50L);
        Mockito.verify(recordingOptionsBuilder).maxSize(64L);

        ArgumentCaptor<ReplacementPolicy> replaceCaptor =
                ArgumentCaptor.forClass(ReplacementPolicy.class);

        ArgumentCaptor<ConnectionDescriptor> connectionDescriptorCaptor =
                ArgumentCaptor.forClass(ConnectionDescriptor.class);

        ArgumentCaptor<IConstrainedMap<String>> recordingOptionsCaptor =
                ArgumentCaptor.forClass(IConstrainedMap.class);

        ArgumentCaptor<String> templateNameCaptor = ArgumentCaptor.forClass(String.class);

        ArgumentCaptor<TemplateType> templateTypeCaptor =
                ArgumentCaptor.forClass(TemplateType.class);

        ArgumentCaptor<Metadata> metadataCaptor = ArgumentCaptor.forClass(Metadata.class);

        ArgumentCaptor<Boolean> archiveOnStopCaptor = ArgumentCaptor.forClass(Boolean.class);

        Mockito.verify(recordingTargetHelper)
                .startRecording(
                        replaceCaptor.capture(),
                        connectionDescriptorCaptor.capture(),
                        recordingOptionsCaptor.capture(),
                        templateNameCaptor.capture(),
                        templateTypeCaptor.capture(),
                        metadataCaptor.capture(),
                        archiveOnStopCaptor.capture());

        MatcherAssert.assertThat(
                replaceCaptor.getValue(), Matchers.equalTo(ReplacementPolicy.NEVER));

        ConnectionDescriptor connectionDescriptor = connectionDescriptorCaptor.getValue();
        MatcherAssert.assertThat(
                connectionDescriptor.getTargetId(), Matchers.equalTo("fooHost:9091"));
        MatcherAssert.assertThat(
                connectionDescriptor.getCredentials().isEmpty(), Matchers.is(true));

        IConstrainedMap<String> actualRecordingOptions = recordingOptionsCaptor.getValue();
        MatcherAssert.assertThat(actualRecordingOptions, Matchers.sameInstance(recordingOptions));

        MatcherAssert.assertThat(templateNameCaptor.getValue(), Matchers.equalTo("Foo"));

        MatcherAssert.assertThat(
                templateTypeCaptor.getValue(), Matchers.equalTo(TemplateType.CUSTOM));

        MatcherAssert.assertThat(metadataCaptor.getValue(), Matchers.equalTo(new Metadata()));

        Mockito.verify(resp).setStatusCode(201);
        Mockito.verify(resp).putHeader(HttpHeaders.LOCATION, "/someRecording");
        Mockito.verify(resp).putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        Mockito.verify(resp)
                .end(
                        "{\"downloadUrl\":\"example-download-url\",\"reportUrl\":\"example-report-url\",\"metadata\":{\"labels\":{}},\"archiveOnStop\":true,\"id\":1,\"name\":\"someRecording\",\"state\":\"STOPPED\",\"startTime\":0,\"duration\":0,\"continuous\":false,\"toDisk\":false,\"maxSize\":0,\"maxAge\":0}");
    }
}
