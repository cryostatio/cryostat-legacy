/*-
 * #%L
 * Container JFR
 * %%
 * Copyright (C) 2020 Red Hat, Inc.
 * %%
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
 * #L%
 */
package com.redhat.rhjmc.containerjfr.net.web.handlers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.redhat.rhjmc.containerjfr.MainModule;
import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.net.AuthManager;
import com.redhat.rhjmc.containerjfr.net.HttpServer;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;

@ExtendWith(MockitoExtension.class)
class RecordingsPostHandlerTest {

    RecordingsPostHandler handler;
    @Mock AuthManager authManager;
    @Mock HttpServer httpServer;
    @Mock Vertx vertx;
    @Mock FileSystem cjfrFs;
    @Mock Path recordingsPath;
    @Mock Logger logger;

    @BeforeEach
    void setup() {
        when(httpServer.getVertx()).thenReturn(vertx);
        this.handler =
                new RecordingsPostHandler(
                        authManager,
                        httpServer,
                        cjfrFs,
                        recordingsPath,
                        new ResponseUtils(MainModule.provideGson()),
                        logger);
    }

    @Test
    void shouldBeLowerPriority() {
        MatcherAssert.assertThat(
                handler.getPriority(), Matchers.greaterThan(RequestHandler.DEFAULT_PRIORITY));
    }

    @Test
    void shouldHandleRecordingUploadRequest() throws Exception {
        String basename = "localhost_test_20191219T213834Z";
        String filename = basename + ".jfr";
        String savePath = "/some/path/";

        RoutingContext ctx = mock(RoutingContext.class);

        when(authManager.validateHttpHeader(any()))
                .thenReturn(CompletableFuture.completedFuture(true));
        HttpServerRequest req = mock(HttpServerRequest.class);
        when(ctx.request()).thenReturn(req);

        when(cjfrFs.isDirectory(recordingsPath)).thenReturn(true);

        Set<FileUpload> uploads = new HashSet<>();
        FileUpload upload = mock(FileUpload.class);
        uploads.add(upload);
        when(ctx.fileUploads()).thenReturn(uploads);
        when(upload.name()).thenReturn("recording");
        when(upload.fileName()).thenReturn(filename);
        when(upload.uploadedFileName()).thenReturn("foo");

        Path filePath = mock(Path.class);
        when(filePath.toString()).thenReturn(savePath + filename);
        when(recordingsPath.resolve(filename)).thenReturn(filePath);

        io.vertx.core.file.FileSystem vertxFs = mock(io.vertx.core.file.FileSystem.class);
        when(vertx.fileSystem()).thenReturn(vertxFs);

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
                .when(vertx)
                .executeBlocking(any(Handler.class), any(Handler.class));

        when(vertxFs.exists(Mockito.eq(savePath + filename), any(Handler.class)))
                .thenAnswer(
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
                        });

        when(vertxFs.move(Mockito.eq("foo"), Mockito.eq(savePath + filename), any(Handler.class)))
                .thenAnswer(
                        invocation -> {
                            Handler<AsyncResult<Boolean>> handler = invocation.getArgument(2);
                            handler.handle(
                                    new AsyncResult<>() {
                                        @Override
                                        public Boolean result() {
                                            return true;
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
                        });

        HttpServerResponse rep = mock(HttpServerResponse.class);
        when(ctx.response()).thenReturn(rep);

        handler.handle(ctx);

        InOrder inOrder = Mockito.inOrder(rep);
        inOrder.verify(rep).putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
        inOrder.verify(rep).end("{\"name\":\"" + filename + "\"}");
    }
}
