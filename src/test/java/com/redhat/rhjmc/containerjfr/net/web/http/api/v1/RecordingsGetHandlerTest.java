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
package com.redhat.rhjmc.containerjfr.net.web.http.api.v1;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.redhat.rhjmc.containerjfr.MainModule;
import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.net.AuthManager;
import com.redhat.rhjmc.containerjfr.net.web.WebServer;

import com.google.gson.Gson;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class RecordingsGetHandlerTest {

    RecordingsGetHandler handler;
    @Mock AuthManager auth;
    @Mock Path savedRecordingsPath;
    @Mock FileSystem fs;
    @Mock WebServer webServer;
    @Mock Logger logger;
    Gson gson = MainModule.provideGson(logger);

    @BeforeEach
    void setup() {
        this.handler =
                new RecordingsGetHandler(
                        auth, savedRecordingsPath, fs, () -> webServer, gson, logger);
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
    void shouldRespondWith501IfDirectoryDoesNotExist() throws IOException {
        RoutingContext ctx = Mockito.mock(RoutingContext.class);

        Mockito.when(fs.exists(Mockito.any())).thenReturn(false);

        HttpStatusException httpEx =
                Assertions.assertThrows(
                        HttpStatusException.class, () -> handler.handleAuthenticated(ctx));
        MatcherAssert.assertThat(httpEx.getStatusCode(), Matchers.equalTo(501));
    }

    @Test
    void shouldResponseWith501IfDirectoryNotReadable() throws IOException {
        RoutingContext ctx = Mockito.mock(RoutingContext.class);

        Mockito.when(fs.exists(Mockito.any())).thenReturn(true);
        Mockito.when(fs.isReadable(Mockito.any())).thenReturn(false);

        HttpStatusException httpEx =
                Assertions.assertThrows(
                        HttpStatusException.class, () -> handler.handleAuthenticated(ctx));
        MatcherAssert.assertThat(httpEx.getStatusCode(), Matchers.equalTo(501));
    }

    @Test
    void shouldRespondWith501IfPathNotDirectory() throws IOException {
        RoutingContext ctx = Mockito.mock(RoutingContext.class);

        Mockito.when(fs.exists(Mockito.any())).thenReturn(true);
        Mockito.when(fs.isReadable(Mockito.any())).thenReturn(true);
        Mockito.when(fs.isDirectory(Mockito.any())).thenReturn(false);

        HttpStatusException httpEx =
                Assertions.assertThrows(
                        HttpStatusException.class, () -> handler.handleAuthenticated(ctx));
        MatcherAssert.assertThat(httpEx.getStatusCode(), Matchers.equalTo(501));
    }

    @Test
    void shouldRespondWithInternalErrorIfExceptionThrown() throws IOException {
        RoutingContext ctx = Mockito.mock(RoutingContext.class);

        Mockito.when(fs.exists(Mockito.any())).thenReturn(true);
        Mockito.when(fs.isReadable(Mockito.any())).thenReturn(true);
        Mockito.when(fs.isDirectory(Mockito.any())).thenReturn(true);
        Mockito.when(fs.listDirectoryChildren(Mockito.any())).thenThrow(IOException.class);

        Assertions.assertThrows(IOException.class, () -> handler.handleAuthenticated(ctx));
    }

    @Test
    void shouldRespondWithListOfRecordings() throws Exception {
        RoutingContext ctx = Mockito.mock(RoutingContext.class);
        HttpServerResponse resp = Mockito.mock(HttpServerResponse.class);
        Mockito.when(ctx.response()).thenReturn(resp);

        Mockito.when(fs.exists(Mockito.any())).thenReturn(true);
        Mockito.when(fs.isReadable(Mockito.any())).thenReturn(true);
        Mockito.when(fs.isDirectory(Mockito.any())).thenReturn(true);
        List<String> names = List.of("recordingA", "123recording");
        Mockito.when(fs.listDirectoryChildren(Mockito.any())).thenReturn(names);

        Mockito.when(webServer.getArchivedReportURL(Mockito.anyString()))
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                String name = invocation.getArgument(0);
                                return "/some/path/archive/" + name;
                            }
                        });
        Mockito.when(webServer.getArchivedDownloadURL(Mockito.anyString()))
                .thenAnswer(
                        new Answer<String>() {
                            @Override
                            public String answer(InvocationOnMock invocation) throws Throwable {
                                String name = invocation.getArgument(0);
                                return "/some/path/download/" + name;
                            }
                        });

        handler.handleAuthenticated(ctx);

        List<Map<String, String>> expected =
                List.of(
                        Map.of(
                                "name", "recordingA",
                                "downloadUrl", "/some/path/download/recordingA",
                                "reportUrl", "/some/path/archive/recordingA"),
                        Map.of(
                                "name", "123recording",
                                "downloadUrl", "/some/path/download/123recording",
                                "reportUrl", "/some/path/archive/123recording"));

        ArgumentCaptor<String> responseCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(resp).end(responseCaptor.capture());
        String rawResult = responseCaptor.getValue();
        List result = gson.fromJson(rawResult, List.class);
        MatcherAssert.assertThat(result, Matchers.equalTo(expected));
    }
}
