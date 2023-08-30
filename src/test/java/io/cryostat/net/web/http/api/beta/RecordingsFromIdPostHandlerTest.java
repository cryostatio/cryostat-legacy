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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import io.cryostat.MainModule;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.messaging.notifications.Notification;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.v2.ApiException;
import io.cryostat.platform.ServiceRef;
import io.cryostat.recordings.JvmIdHelper;
import io.cryostat.recordings.JvmIdHelper.JvmIdDoesNotExistException;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingMetadataManager;
import io.cryostat.recordings.RecordingMetadataManager.Metadata;
import io.cryostat.rules.ArchivedRecordingInfo;

import com.google.gson.Gson;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class RecordingsFromIdPostHandlerTest {

    RecordingsFromIdPostHandler handler;
    @Mock AuthManager authManager;
    @Mock CredentialsManager credentialsManager;
    @Mock FileSystem cryoFs;
    @Mock Path recordingsPath;
    int globalMaxFiles = 10;
    @Mock WebServer webServer;
    @Mock NotificationFactory notificationFactory;
    @Mock Notification notification;
    @Mock Notification.Builder notificationBuilder;
    @Mock JvmIdHelper jvmIdHelper;
    @Mock RecordingArchiveHelper recordingArchiveHelper;
    @Mock RecordingMetadataManager recordingMetadataManager;
    @Mock Logger logger;
    Gson gson = MainModule.provideGson(logger);

    // this is the basename file name timestamp (December 19, 2019)
    long expectedArchivedTime = Instant.parse("2019-12-19T21:38:34.00Z").toEpochMilli();
    String mockJvmId = "someJvmId";
    @Mock ServiceRef mockServiceRef;
    @Mock URI mockConnectUri;
    String mockConnectUrl = "someConnectUrl";

    @BeforeEach
    void setup() {
        lenient().when(notificationFactory.createBuilder()).thenReturn(notificationBuilder);
        lenient()
                .when(notificationBuilder.metaCategory(Mockito.any()))
                .thenReturn(notificationBuilder);
        lenient()
                .when(notificationBuilder.metaType(Mockito.any(Notification.MetaType.class)))
                .thenReturn(notificationBuilder);
        lenient()
                .when(notificationBuilder.metaType(Mockito.any(HttpMimeType.class)))
                .thenReturn(notificationBuilder);
        lenient().when(notificationBuilder.message(Mockito.any())).thenReturn(notificationBuilder);
        lenient().when(notificationBuilder.build()).thenReturn(notification);
        lenient().when(mockServiceRef.getServiceUri()).thenReturn(mockConnectUri);
        lenient().when(mockConnectUri.toString()).thenReturn(mockConnectUrl);

        this.handler =
                new RecordingsFromIdPostHandler(
                        authManager,
                        credentialsManager,
                        gson,
                        cryoFs,
                        jvmIdHelper,
                        notificationFactory,
                        recordingArchiveHelper,
                        recordingMetadataManager,
                        recordingsPath,
                        globalMaxFiles,
                        () -> webServer,
                        logger);
    }

    @Nested
    class ApiSpec {

        @Test
        void shouldHandlePOSTRequest() {
            MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.POST));
        }

        @Test
        void shouldHandleCorrectPath() {
            MatcherAssert.assertThat(
                    handler.path(), Matchers.equalTo("/api/beta/recordings/:jvmId"));
        }

        @Test
        void shouldHaveExpectedRequiredPermissions() {
            MatcherAssert.assertThat(
                    handler.resourceActions(),
                    Matchers.equalTo(
                            Set.of(
                                    ResourceAction.CREATE_RECORDING,
                                    ResourceAction.READ_RECORDING,
                                    ResourceAction.DELETE_RECORDING,
                                    ResourceAction.DELETE_REPORT)));
        }

        @Test
        void shouldNotBeAsyncHandler() {
            Assertions.assertTrue(handler.isAsync());
        }

        @Test
        void shouldBeOrderedHandler() {
            Assertions.assertTrue(handler.isOrdered());
        }

        @Test
        void shouldProduceJson() {
            MatcherAssert.assertThat(
                    handler.produces(), Matchers.equalTo(List.of(HttpMimeType.JSON)));
        }

        @Test
        void shouldConsumeMultipartForm() {
            MatcherAssert.assertThat(
                    handler.consumes(), Matchers.equalTo(List.of(HttpMimeType.MULTIPART_FORM)));
        }
    }

    @Test
    void shouldHandleRecordingUploadRequest() throws Exception {
        String basename = "localhost_test_20191219T213834Z";
        String filename = basename + ".jfr";
        String subdirectoryName = "mockSubdirectory";

        RoutingContext ctx = mock(RoutingContext.class);

        when(authManager.validateHttpHeader(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        HttpServerRequest req = mock(HttpServerRequest.class);
        MultiMap attrs = MultiMap.caseInsensitiveMultiMap();
        when(req.formAttributes()).thenReturn(attrs);

        when(ctx.request()).thenReturn(req);

        when(cryoFs.isDirectory(recordingsPath)).thenReturn(true);
        when(req.getParam(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(String.valueOf(globalMaxFiles));

        FileUpload upload = mock(FileUpload.class);
        when(ctx.fileUploads()).thenReturn(List.of(upload));
        when(webServer.getTempFileUpload(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(upload);
        when(upload.fileName()).thenReturn(filename);
        when(upload.uploadedFileName()).thenReturn("foo");

        when(ctx.pathParam("jvmId")).thenReturn(mockJvmId);
        when(jvmIdHelper.reverseLookup(mockJvmId)).thenReturn(Optional.of(mockServiceRef));
        when(jvmIdHelper.jvmIdToSubdirectoryName(mockJvmId)).thenReturn(subdirectoryName);
        when(recordingArchiveHelper.getArchivedTimeFromTimestamp(Mockito.anyString()))
                .thenReturn(expectedArchivedTime);

        doAnswer(
                        invocation -> {
                            Handler<AsyncResult<Void>> handler = invocation.getArgument(1);
                            handler.handle(
                                    new AsyncResult<>() {
                                        @Override
                                        public Void result() {
                                            return null;
                                        }

                                        @Override
                                        public Throwable cause() {
                                            return null;
                                        }

                                        @Override
                                        public boolean succeeded() {
                                            return true;
                                        }

                                        @Override
                                        public boolean failed() {
                                            return false;
                                        }
                                    });

                            return null;
                        })
                .when(recordingArchiveHelper)
                .validateRecording(Mockito.any(String.class), Mockito.any(Handler.class));

        doAnswer(
                        invocation -> {
                            Handler<AsyncResult<String>> handler = invocation.getArgument(5);
                            handler.handle(
                                    new AsyncResult<>() {
                                        @Override
                                        public String result() {
                                            return filename;
                                        }

                                        @Override
                                        public Throwable cause() {
                                            return null;
                                        }

                                        @Override
                                        public boolean succeeded() {
                                            return true;
                                        }

                                        @Override
                                        public boolean failed() {
                                            return false;
                                        }
                                    });

                            return null;
                        })
                .when(recordingArchiveHelper)
                .saveUploadedRecording(
                        Mockito.any(String.class),
                        Mockito.any(String.class),
                        Mockito.any(String.class),
                        Mockito.any(String.class),
                        Mockito.any(Integer.class),
                        Mockito.any(Handler.class));

        HttpServerResponse rep = mock(HttpServerResponse.class);
        when(ctx.response()).thenReturn(rep);
        when(rep.putHeader(Mockito.any(CharSequence.class), Mockito.anyString())).thenReturn(rep);

        when(webServer.getArchivedDownloadURL(Mockito.anyString(), Mockito.anyString()))
                .then(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                return "/some/download/path/" + invocation.getArgument(1);
                            }
                        });
        when(webServer.getArchivedReportURL(Mockito.anyString(), Mockito.anyString()))
                .then(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                return "/some/report/path/" + invocation.getArgument(1);
                            }
                        });

        handler.handle(ctx);

        InOrder inOrder = Mockito.inOrder(rep);
        inOrder.verify(rep).putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime());
        inOrder.verify(rep).end(gson.toJson(Map.of("name", filename, "metadata", new Metadata())));

        ArchivedRecordingInfo recordingInfo =
                new ArchivedRecordingInfo(
                        mockConnectUrl,
                        filename,
                        "/some/download/path/" + filename,
                        "/some/report/path/" + filename,
                        new Metadata(),
                        0,
                        expectedArchivedTime);
        ArgumentCaptor<Map<String, Object>> messageCaptor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(notificationFactory).createBuilder();
        Mockito.verify(notificationBuilder).metaCategory("ArchivedRecordingCreated");
        Mockito.verify(notificationBuilder).metaType(HttpMimeType.JSON);
        Mockito.verify(notificationBuilder).message(messageCaptor.capture());
        Mockito.verify(notificationBuilder).build();
        Mockito.verify(notification).send();

        MatcherAssert.assertThat(
                messageCaptor.getValue(),
                Matchers.equalTo(Map.of("recording", recordingInfo, "target", mockConnectUrl)));
    }

    @Test
    void shouldHandleRecordingUploadRequestWithLabels() throws Exception {
        String basename = "localhost_test_20191219T213834Z";
        String filename = basename + ".jfr";
        String subdirectoryName = "mockSubdirectory";
        Map<String, String> labels = Map.of("key", "value", "key1", "value1");
        Metadata metadata = new Metadata(labels);

        RoutingContext ctx = mock(RoutingContext.class);

        when(authManager.validateHttpHeader(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        HttpServerRequest req = mock(HttpServerRequest.class);
        MultiMap attrs = MultiMap.caseInsensitiveMultiMap();
        attrs.add("labels", labels.toString());
        when(req.formAttributes()).thenReturn(attrs);
        when(req.getParam(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(String.valueOf(globalMaxFiles));

        when(recordingMetadataManager.parseRecordingLabels(labels.toString())).thenReturn(labels);

        when(ctx.request()).thenReturn(req);

        when(cryoFs.isDirectory(recordingsPath)).thenReturn(true);

        FileUpload upload = mock(FileUpload.class);
        when(ctx.fileUploads()).thenReturn(List.of(upload));
        when(webServer.getTempFileUpload(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(upload);
        when(ctx.pathParam("jvmId")).thenReturn(mockJvmId);
        when(jvmIdHelper.reverseLookup(mockJvmId)).thenReturn(Optional.of(mockServiceRef));

        when(upload.fileName()).thenReturn(filename);
        when(upload.uploadedFileName()).thenReturn("foo");
        when(jvmIdHelper.jvmIdToSubdirectoryName(mockJvmId)).thenReturn(subdirectoryName);
        when(recordingArchiveHelper.getArchivedTimeFromTimestamp(Mockito.anyString()))
                .thenReturn(expectedArchivedTime);

        doAnswer(
                        invocation -> {
                            Handler<AsyncResult<Void>> handler = invocation.getArgument(1);
                            handler.handle(
                                    new AsyncResult<>() {
                                        @Override
                                        public Void result() {
                                            return null;
                                        }

                                        @Override
                                        public Throwable cause() {
                                            return null;
                                        }

                                        @Override
                                        public boolean succeeded() {
                                            return true;
                                        }

                                        @Override
                                        public boolean failed() {
                                            return false;
                                        }
                                    });

                            return null;
                        })
                .when(recordingArchiveHelper)
                .validateRecording(Mockito.any(String.class), Mockito.any(Handler.class));

        doAnswer(
                        invocation -> {
                            Handler<AsyncResult<String>> handler = invocation.getArgument(5);
                            handler.handle(
                                    new AsyncResult<>() {
                                        @Override
                                        public String result() {
                                            return filename;
                                        }

                                        @Override
                                        public Throwable cause() {
                                            return null;
                                        }

                                        @Override
                                        public boolean succeeded() {
                                            return true;
                                        }

                                        @Override
                                        public boolean failed() {
                                            return false;
                                        }
                                    });

                            return null;
                        })
                .when(recordingArchiveHelper)
                .saveUploadedRecording(
                        Mockito.any(String.class),
                        Mockito.any(String.class),
                        Mockito.any(String.class),
                        Mockito.any(String.class),
                        Mockito.any(Integer.class),
                        Mockito.any(Handler.class));

        HttpServerResponse rep = mock(HttpServerResponse.class);
        when(ctx.response()).thenReturn(rep);
        when(rep.putHeader(Mockito.any(CharSequence.class), Mockito.anyString())).thenReturn(rep);

        when(webServer.getArchivedDownloadURL(Mockito.anyString(), Mockito.anyString()))
                .then(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                return "/some/download/path/" + invocation.getArgument(1);
                            }
                        });
        when(webServer.getArchivedReportURL(Mockito.anyString(), Mockito.anyString()))
                .then(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                return "/some/report/path/" + invocation.getArgument(1);
                            }
                        });

        when(recordingMetadataManager.setRecordingMetadataFromPath(
                        subdirectoryName, filename, metadata))
                .thenReturn(CompletableFuture.completedFuture(metadata));

        handler.handle(ctx);

        InOrder inOrder = Mockito.inOrder(rep);
        inOrder.verify(rep).putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime());
        inOrder.verify(rep).end(gson.toJson(Map.of("name", filename, "metadata", metadata)));

        ArchivedRecordingInfo recordingInfo =
                new ArchivedRecordingInfo(
                        mockConnectUrl,
                        filename,
                        "/some/download/path/" + filename,
                        "/some/report/path/" + filename,
                        metadata,
                        0,
                        expectedArchivedTime);
        ArgumentCaptor<Map<String, Object>> messageCaptor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(notificationFactory).createBuilder();
        Mockito.verify(notificationBuilder).metaCategory("ArchivedRecordingCreated");
        Mockito.verify(notificationBuilder).metaType(HttpMimeType.JSON);
        Mockito.verify(notificationBuilder).message(messageCaptor.capture());
        Mockito.verify(notificationBuilder).build();
        Mockito.verify(notification).send();

        MatcherAssert.assertThat(
                messageCaptor.getValue(),
                Matchers.equalTo(Map.of("recording", recordingInfo, "target", mockConnectUrl)));
    }

    @Test
    void shouldHandleNoRecordingSubmission() throws Exception {
        RoutingContext ctx = mock(RoutingContext.class);

        when(authManager.validateHttpHeader(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        HttpServerRequest req = mock(HttpServerRequest.class);
        when(ctx.request()).thenReturn(req);
        HttpServerResponse rep = mock(HttpServerResponse.class);
        when(ctx.response()).thenReturn(rep);
        when(rep.putHeader(Mockito.any(CharSequence.class), Mockito.anyString())).thenReturn(rep);

        when(cryoFs.isDirectory(recordingsPath)).thenReturn(true);
        when(req.getParam(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(String.valueOf(globalMaxFiles));

        when(ctx.fileUploads()).thenReturn(List.of());

        ApiException ex = Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(
                ex.getFailureReason(), Matchers.equalTo("No recording submission."));
    }

    @Test
    void shouldHandleIncorrectFormField() throws Exception {
        RoutingContext ctx = mock(RoutingContext.class);

        when(authManager.validateHttpHeader(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        HttpServerRequest req = mock(HttpServerRequest.class);
        when(ctx.request()).thenReturn(req);
        HttpServerResponse rep = mock(HttpServerResponse.class);
        when(ctx.response()).thenReturn(rep);
        when(rep.putHeader(Mockito.any(CharSequence.class), Mockito.anyString())).thenReturn(rep);

        when(cryoFs.isDirectory(recordingsPath)).thenReturn(true);
        when(req.getParam(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(String.valueOf(globalMaxFiles));

        FileUpload upload = mock(FileUpload.class);
        when(ctx.fileUploads()).thenReturn(List.of(upload));
        when(webServer.getTempFileUpload(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(null);

        ApiException ex = Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(
                ex.getFailureReason(), Matchers.equalTo("No recording submission."));
    }

    @ParameterizedTest()
    @ValueSource(strings = {"foo", "123max", "Integer.MAX_VALUE"})
    void shouldHandleBadParameter(String maxFiles) throws Exception {
        RoutingContext ctx = mock(RoutingContext.class);

        when(authManager.validateHttpHeader(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        HttpServerRequest req = mock(HttpServerRequest.class);
        when(ctx.request()).thenReturn(req);
        HttpServerResponse rep = mock(HttpServerResponse.class);
        when(ctx.response()).thenReturn(rep);
        when(rep.putHeader(Mockito.any(CharSequence.class), Mockito.anyString())).thenReturn(rep);

        when(cryoFs.isDirectory(recordingsPath)).thenReturn(true);
        when(req.getParam(Mockito.anyString(), Mockito.anyString())).thenReturn(maxFiles);

        ApiException ex = Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(
                ex.getFailureReason(), Matchers.equalTo("maxFiles must be a positive integer."));
    }

    @Test
    void shouldHandleEmptyRecordingName() throws Exception {
        RoutingContext ctx = mock(RoutingContext.class);

        when(authManager.validateHttpHeader(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        HttpServerRequest req = mock(HttpServerRequest.class);
        when(ctx.request()).thenReturn(req);
        HttpServerResponse rep = mock(HttpServerResponse.class);
        when(ctx.response()).thenReturn(rep);
        when(rep.putHeader(Mockito.any(CharSequence.class), Mockito.anyString())).thenReturn(rep);

        when(cryoFs.isDirectory(recordingsPath)).thenReturn(true);
        when(req.getParam(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(String.valueOf(globalMaxFiles));

        FileUpload upload = mock(FileUpload.class);
        when(ctx.fileUploads()).thenReturn(List.of(upload));
        when(webServer.getTempFileUpload(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(upload);
        when(upload.fileName()).thenReturn("");
        when(ctx.pathParam("jvmId")).thenReturn(mockJvmId);
        when(jvmIdHelper.reverseLookup(mockJvmId)).thenReturn(Optional.of(mockServiceRef));

        ApiException ex = Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(
                ex.getFailureReason(), Matchers.equalTo("Recording name must not be empty."));

        verify(recordingArchiveHelper).deleteTempFileUpload(upload);
    }

    @Test
    void shouldHandleIncorrectFileNamePattern() throws JvmIdDoesNotExistException {
        String basename = "incorrect_file_name";
        String filename = basename + ".jfr";

        RoutingContext ctx = mock(RoutingContext.class);

        when(authManager.validateHttpHeader(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        HttpServerRequest req = mock(HttpServerRequest.class);
        when(ctx.request()).thenReturn(req);
        HttpServerResponse rep = mock(HttpServerResponse.class);
        when(ctx.response()).thenReturn(rep);
        when(rep.putHeader(Mockito.any(CharSequence.class), Mockito.anyString())).thenReturn(rep);

        when(cryoFs.isDirectory(recordingsPath)).thenReturn(true);
        when(req.getParam(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(String.valueOf(globalMaxFiles));

        FileUpload upload = mock(FileUpload.class);
        when(ctx.fileUploads()).thenReturn(List.of(upload));
        when(webServer.getTempFileUpload(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(upload);

        when(upload.fileName()).thenReturn(filename);
        when(ctx.pathParam("jvmId")).thenReturn(mockJvmId);
        when(jvmIdHelper.reverseLookup(mockJvmId)).thenReturn(Optional.of(mockServiceRef));

        ApiException ex = Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(
                ex.getFailureReason(),
                Matchers.equalTo("This is not a valid file name for the recording."));

        verify(recordingArchiveHelper).deleteTempFileUpload(upload);
    }

    @Test
    void shouldHandleInvalidLabels() throws JvmIdDoesNotExistException {
        String basename = "localhost_test_20191219T213834Z";
        String filename = basename + ".jfr";
        String labels = "invalid";

        RoutingContext ctx = mock(RoutingContext.class);

        when(authManager.validateHttpHeader(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        HttpServerRequest req = mock(HttpServerRequest.class);
        when(ctx.request()).thenReturn(req);
        HttpServerResponse rep = mock(HttpServerResponse.class);
        when(ctx.response()).thenReturn(rep);
        when(rep.putHeader(Mockito.any(CharSequence.class), Mockito.anyString())).thenReturn(rep);

        when(cryoFs.isDirectory(recordingsPath)).thenReturn(true);
        when(req.getParam(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(String.valueOf(globalMaxFiles));

        FileUpload upload = mock(FileUpload.class);
        when(ctx.fileUploads()).thenReturn(List.of(upload));
        when(webServer.getTempFileUpload(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(upload);
        when(upload.fileName()).thenReturn(filename);
        when(ctx.pathParam("jvmId")).thenReturn(mockJvmId);
        when(jvmIdHelper.reverseLookup(mockJvmId)).thenReturn(Optional.of(mockServiceRef));

        MultiMap attrs = MultiMap.caseInsensitiveMultiMap();
        attrs.add("labels", labels);
        when(req.formAttributes()).thenReturn(attrs);

        Mockito.doThrow(new IllegalArgumentException())
                .when(recordingMetadataManager)
                .parseRecordingLabels(labels);

        ApiException ex = Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(
                ex.getFailureReason(),
                Matchers.equalTo("Invalid metadata labels for the recording."));

        verify(recordingArchiveHelper).deleteTempFileUpload(upload);
    }

    @Test
    void shouldHandleInvalidJvmId() throws Exception {
        RoutingContext ctx = mock(RoutingContext.class);

        when(authManager.validateHttpHeader(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        HttpServerRequest req = mock(HttpServerRequest.class);
        when(ctx.request()).thenReturn(req);
        HttpServerResponse rep = mock(HttpServerResponse.class);
        when(ctx.response()).thenReturn(rep);
        when(rep.putHeader(Mockito.any(CharSequence.class), Mockito.anyString())).thenReturn(rep);

        when(cryoFs.isDirectory(recordingsPath)).thenReturn(true);
        when(req.getParam(Mockito.anyString(), Mockito.anyString()))
                .thenReturn(String.valueOf(globalMaxFiles));

        FileUpload upload = mock(FileUpload.class);
        when(ctx.fileUploads()).thenReturn(List.of(upload));
        when(webServer.getTempFileUpload(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(upload);
        when(ctx.pathParam("jvmId")).thenReturn(mockJvmId);
        when(jvmIdHelper.reverseLookup(mockJvmId)).thenReturn(Optional.empty());

        ApiException ex = Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(
                ex.getFailureReason(),
                Matchers.equalTo(String.format("jvmId must be valid: %s", mockJvmId)));

        verify(recordingArchiveHelper).deleteTempFileUpload(upload);
    }
}
