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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import io.cryostat.MainModule;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.messaging.notifications.Notification;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.HttpServer;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.WebServer;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.RequestHandler;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingMetadataManager;
import io.cryostat.recordings.RecordingMetadataManager.Metadata;
import io.cryostat.rules.ArchivedRecordingInfo;

import com.google.gson.Gson;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.HttpException;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class RecordingsPostHandlerTest {

    RecordingsPostHandler handler;
    @Mock AuthManager authManager;
    @Mock CredentialsManager credentialsManager;
    @Mock HttpServer httpServer;
    @Mock Vertx vertx;
    @Mock FileSystem cryoFs;
    @Mock Path recordingsPath;
    @Mock WebServer webServer;
    @Mock NotificationFactory notificationFactory;
    @Mock Notification notification;
    @Mock Notification.Builder notificationBuilder;
    @Mock RecordingMetadataManager recordingMetadataManager;
    @Mock RecordingArchiveHelper recordingArchiveHelper;
    @Mock Logger logger;
    Gson gson = MainModule.provideGson(logger);

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
        when(httpServer.getVertx()).thenReturn(vertx);
        this.handler =
                new RecordingsPostHandler(
                        authManager,
                        credentialsManager,
                        httpServer,
                        cryoFs,
                        recordingsPath,
                        gson,
                        notificationFactory,
                        () -> webServer,
                        recordingMetadataManager,
                        recordingArchiveHelper,
                        logger);
    }

    @Test
    void shouldBeLowerPriority() {
        MatcherAssert.assertThat(
                handler.getPriority(), Matchers.greaterThan(RequestHandler.DEFAULT_PRIORITY));
    }

    @Test
    void shouldHaveExpectedRequiredPermissions() {
        MatcherAssert.assertThat(
                handler.resourceActions(),
                Matchers.equalTo(Set.of(ResourceAction.CREATE_RECORDING)));
    }

    @Test
    void shouldHandleRecordingUploadRequest() throws Exception {
        String basename = "localhost_test_20191219T213834Z";
        String filename = basename + ".jfr";

        RoutingContext ctx = mock(RoutingContext.class);

        when(authManager.validateHttpHeader(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        HttpServerRequest req = mock(HttpServerRequest.class);
        MultiMap attrs = MultiMap.caseInsensitiveMultiMap();
        Mockito.when(req.formAttributes()).thenReturn(attrs);
        when(ctx.request()).thenReturn(req);

        when(cryoFs.isDirectory(recordingsPath)).thenReturn(true);

        FileUpload upload = mock(FileUpload.class);
        when(ctx.fileUploads()).thenReturn(List.of(upload));
        when(upload.name()).thenReturn("recording");
        when(upload.fileName()).thenReturn(filename);
        when(upload.uploadedFileName()).thenReturn("foo");

        HttpServerResponse rep = mock(HttpServerResponse.class);
        when(ctx.response()).thenReturn(rep);
        when(rep.putHeader(Mockito.any(CharSequence.class), Mockito.anyString())).thenReturn(rep);

        doAnswer(
                        invocation -> {
                            Handler<AsyncResult<Boolean>> handler = invocation.getArgument(1);
                            handler.handle(
                                    new AsyncResult<>() {
                                        @Override
                                        public Boolean result() {
                                            return false;
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
                            Handler<AsyncResult<String>> handler = invocation.getArgument(4);
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
                .saveRecording(
                        Mockito.any(String.class),
                        Mockito.any(String.class),
                        Mockito.any(String.class),
                        Mockito.any(Integer.class),
                        Mockito.any(Handler.class));

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
                        RecordingArchiveHelper.UPLOADED_RECORDINGS_SUBDIRECTORY,
                        filename,
                        "/some/download/path/" + filename,
                        "/some/report/path/" + filename,
                        new Metadata(),
                        0);
        ArgumentCaptor<Map<String, Object>> messageCaptor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(notificationFactory).createBuilder();
        Mockito.verify(notificationBuilder).metaCategory("ArchivedRecordingCreated");
        Mockito.verify(notificationBuilder).metaType(HttpMimeType.JSON);
        Mockito.verify(notificationBuilder).message(messageCaptor.capture());
        Mockito.verify(notificationBuilder).build();
        Mockito.verify(notification).send();

        MatcherAssert.assertThat(
                messageCaptor.getValue(),
                Matchers.equalTo(
                        Map.of(
                                "recording",
                                recordingInfo,
                                "target",
                                RecordingArchiveHelper.UPLOADED_RECORDINGS_SUBDIRECTORY)));
    }

    @Test
    void shouldHandleRecordingUploadRequestWithLabels() throws Exception {
        String basename = "localhost_test_20191219T213834Z";
        String filename = basename + ".jfr";
        String savePath = "/some/path/";
        Map<String, String> labels = Map.of("key", "value", "key1", "value1");
        Metadata metadata = new Metadata(labels);

        RoutingContext ctx = mock(RoutingContext.class);

        when(authManager.validateHttpHeader(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        HttpServerRequest req = mock(HttpServerRequest.class);
        MultiMap attrs = MultiMap.caseInsensitiveMultiMap();
        attrs.add("labels", labels.toString());
        Mockito.when(req.formAttributes()).thenReturn(attrs);

        Mockito.when(recordingMetadataManager.parseRecordingLabels(labels.toString()))
                .thenReturn(labels);

        when(ctx.request()).thenReturn(req);

        when(cryoFs.isDirectory(recordingsPath)).thenReturn(true);

        FileUpload upload = mock(FileUpload.class);
        when(ctx.fileUploads()).thenReturn(List.of(upload));
        when(upload.name()).thenReturn("recording");
        when(upload.fileName()).thenReturn(filename);
        when(upload.uploadedFileName()).thenReturn("foo");

        doAnswer(
                        invocation -> {
                            Handler<AsyncResult<Boolean>> handler = invocation.getArgument(1);
                            handler.handle(
                                    new AsyncResult<>() {
                                        @Override
                                        public Boolean result() {
                                            return false;
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
                            Handler<AsyncResult<String>> handler = invocation.getArgument(4);
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
                .saveRecording(
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

        Mockito.when(recordingMetadataManager.setRecordingMetadata(filename, metadata))
                .thenReturn(CompletableFuture.completedFuture(metadata));

        handler.handle(ctx);

        InOrder inOrder = Mockito.inOrder(rep);
        inOrder.verify(rep).putHeader(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime());
        inOrder.verify(rep).end(gson.toJson(Map.of("name", filename, "metadata", metadata)));

        ArchivedRecordingInfo recordingInfo =
                new ArchivedRecordingInfo(
                        RecordingArchiveHelper.UPLOADED_RECORDINGS_SUBDIRECTORY,
                        filename,
                        "/some/download/path/" + filename,
                        "/some/report/path/" + filename,
                        metadata,
                        0);
        ArgumentCaptor<Map<String, Object>> messageCaptor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(notificationFactory).createBuilder();
        Mockito.verify(notificationBuilder).metaCategory("ArchivedRecordingCreated");
        Mockito.verify(notificationBuilder).metaType(HttpMimeType.JSON);
        Mockito.verify(notificationBuilder).message(messageCaptor.capture());
        Mockito.verify(notificationBuilder).build();
        Mockito.verify(notification).send();
        MatcherAssert.assertThat(
                messageCaptor.getValue(),
                Matchers.equalTo(
                        Map.of(
                                "recording",
                                recordingInfo,
                                "target",
                                RecordingArchiveHelper.UPLOADED_RECORDINGS_SUBDIRECTORY)));
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

        when(ctx.fileUploads()).thenReturn(List.of());

        HttpException ex = Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(ex.getPayload(), Matchers.equalTo("No recording submission"));
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

        FileUpload upload = mock(FileUpload.class);
        when(ctx.fileUploads()).thenReturn(List.of(upload));
        when(upload.name()).thenReturn("incorrect_field_name");

        HttpException ex = Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(ex.getPayload(), Matchers.equalTo("No recording submission"));
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

        FileUpload upload = mock(FileUpload.class);
        when(ctx.fileUploads()).thenReturn(List.of(upload));
        when(upload.name()).thenReturn("recording");
        when(upload.fileName()).thenReturn("");

        HttpException ex = Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(
                ex.getPayload(), Matchers.equalTo("Recording name must not be empty"));
    }

    @Test
    void shouldHandleIncorectFileNamePattern() {
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

        FileUpload upload = mock(FileUpload.class);
        when(ctx.fileUploads()).thenReturn(List.of(upload));
        when(upload.name()).thenReturn("recording");
        when(upload.fileName()).thenReturn(filename);

        HttpException ex = Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(
                ex.getPayload(), Matchers.equalTo("Incorrect recording file name pattern"));
    }

    @Test
    void shouldHandleInvalidLabels() {

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

        FileUpload upload = mock(FileUpload.class);
        when(ctx.fileUploads()).thenReturn(List.of(upload));
        when(upload.name()).thenReturn("recording");
        when(upload.fileName()).thenReturn(filename);

        MultiMap attrs = MultiMap.caseInsensitiveMultiMap();
        attrs.add("labels", labels);
        Mockito.when(req.formAttributes()).thenReturn(attrs);

        Mockito.doThrow(new IllegalArgumentException())
                .when(recordingMetadataManager)
                .parseRecordingLabels(labels);

        HttpException ex = Assertions.assertThrows(HttpException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(ex.getPayload(), Matchers.equalTo("Invalid labels"));
    }
}
