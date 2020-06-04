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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.net.AuthManager;
import com.redhat.rhjmc.containerjfr.net.HttpServer;

import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.RoutingContext;

@ExtendWith(MockitoExtension.class)
class ReportGetCacheHandlerTest {

    ReportGetCacheHandler handler;
    @Mock AuthManager authManager;
    @Mock Path webserverTempPath;
    @Mock HttpServer httpServer;
    @Mock Vertx vertx;
    @Mock FileSystem fs;
    @Mock Logger logger;

    @BeforeEach
    void setup() {
        when(httpServer.getVertx()).thenReturn(vertx);
        when(vertx.fileSystem()).thenReturn(fs);
        when(webserverTempPath.toString()).thenReturn("/example/");
        this.handler =
                new ReportGetCacheHandler(authManager, webserverTempPath, httpServer, logger);
    }

    @Test
    void shouldBeHigherPriority() {
        MatcherAssert.assertThat(
                handler.getPriority(), Matchers.lessThan(RequestHandler.DEFAULT_PRIORITY));
    }

    @Test
    void shouldHandleGETRequest() {
        MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.GET));
    }

    @Test
    void shouldHandleCorrectPath() {
        MatcherAssert.assertThat(
                handler.path(), Matchers.equalTo("/api/v1/reports/:recordingName"));
    }

    @Test
    void shouldBeAsync() {
        Assertions.assertTrue(handler.isAsync());
    }

    @Test
    void shouldSetHeaders() {
        when(authManager.validateHttpHeader(Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerResponse res = mock(HttpServerResponse.class);
        when(ctx.response()).thenReturn(res);
        when(ctx.pathParam("recordingName")).thenReturn("someRecording");

        handler.handle(ctx);

        Mockito.verify(res).putHeader(HttpHeaders.CONTENT_TYPE, "text/html");
    }

    @Test
    void shouldSendFileIfExists() {
        when(authManager.validateHttpHeader(Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerResponse res = mock(HttpServerResponse.class);
        when(ctx.response()).thenReturn(res);

        HttpServerRequest req = mock(HttpServerRequest.class);
        when(ctx.request()).thenReturn(req);
        SocketAddress addr = mock(SocketAddress.class);
        when(req.remoteAddress()).thenReturn(addr);
        when(req.method()).thenReturn(handler.httpMethod());
        when(req.path()).thenReturn(handler.path());

        when(ctx.pathParam("recordingName")).thenReturn("someRecording");
        when(fs.existsBlocking(Mockito.anyString())).thenReturn(true);

        handler.handle(ctx);

        Mockito.verify(res).sendFile(Mockito.anyString());
    }

    @Test
    void shouldCallNextHandlerIfCachedFileDoesNotExist() {
        when(authManager.validateHttpHeader(Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));

        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerResponse res = mock(HttpServerResponse.class);
        when(ctx.response()).thenReturn(res);
        when(ctx.pathParam("recordingName")).thenReturn("someRecording");
        when(fs.existsBlocking(Mockito.anyString())).thenReturn(false);

        handler.handle(ctx);

        Mockito.verify(ctx).next();
    }
}
