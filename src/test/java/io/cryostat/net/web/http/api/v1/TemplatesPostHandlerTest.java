/*-
 * #%L
 * Cryostat
 * %%
 * Copyright (C) 2020 - 2021 The Cryostat Authors
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
package io.cryostat.net.web.http.api.v1;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Set;

import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.core.templates.LocalStorageTemplateService;
import io.cryostat.core.templates.MutableTemplateService.InvalidXmlException;
import io.cryostat.net.AuthManager;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.impl.HttpStatusException;
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
class TemplatesPostHandlerTest {

    TemplatesPostHandler handler;
    @Mock AuthManager auth;
    @Mock LocalStorageTemplateService templateService;
    @Mock FileSystem fs;
    @Mock Logger logger;

    @BeforeEach
    void setup() {
        this.handler = new TemplatesPostHandler(auth, templateService, fs, logger);
    }

    @Test
    void shouldHandlePOST() {
        MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.POST));
    }

    @Test
    void shouldHandleCorrectPath() {
        MatcherAssert.assertThat(handler.path(), Matchers.equalTo("/api/v1/templates"));
    }

    @Test
    void shouldThrowIfWriteFails() throws Exception {
        RoutingContext ctx = Mockito.mock(RoutingContext.class);
        FileUpload upload = Mockito.mock(FileUpload.class);
        Mockito.when(ctx.fileUploads()).thenReturn(Set.of(upload));
        Mockito.when(upload.uploadedFileName()).thenReturn("/file-uploads/abcd-1234");

        Path uploadPath = Mockito.mock(Path.class);
        Mockito.when(fs.pathOf("/file-uploads/abcd-1234")).thenReturn(uploadPath);

        Mockito.when(fs.newInputStream(Mockito.any())).thenThrow(IOException.class);

        Assertions.assertThrows(IOException.class, () -> handler.handleAuthenticated(ctx));
    }

    @Test
    void shouldThrowIfXmlInvalid() throws Exception {
        RoutingContext ctx = Mockito.mock(RoutingContext.class);

        FileUpload upload = Mockito.mock(FileUpload.class);
        Mockito.when(upload.name()).thenReturn("template");
        Mockito.when(upload.uploadedFileName()).thenReturn("/file-uploads/abcd-1234");

        Mockito.when(ctx.fileUploads()).thenReturn(Set.of(upload));

        Path uploadPath = Mockito.mock(Path.class);
        Mockito.when(fs.pathOf("/file-uploads/abcd-1234")).thenReturn(uploadPath);

        InputStream stream = Mockito.mock(InputStream.class);
        Mockito.when(fs.newInputStream(Mockito.any())).thenReturn(stream);

        Mockito.doThrow(InvalidXmlException.class).when(templateService).addTemplate(stream);

        HttpStatusException ex =
                Assertions.assertThrows(
                        HttpStatusException.class, () -> handler.handleAuthenticated(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
        Mockito.verify(fs).deleteIfExists(uploadPath);
    }

    @Test
    void shouldProcessGoodRequest() throws Exception {
        RoutingContext ctx = Mockito.mock(RoutingContext.class);

        HttpServerResponse resp = Mockito.mock(HttpServerResponse.class);
        Mockito.when(ctx.response()).thenReturn(resp);

        FileUpload upload1 = Mockito.mock(FileUpload.class);
        Mockito.when(upload1.name()).thenReturn("template");
        Mockito.when(upload1.uploadedFileName()).thenReturn("/file-uploads/abcd-1234");

        FileUpload upload2 = Mockito.mock(FileUpload.class);
        Mockito.when(upload2.name()).thenReturn("unused");
        Mockito.when(upload2.uploadedFileName()).thenReturn("/file-uploads/wxyz-9999");

        Mockito.when(ctx.fileUploads()).thenReturn(Set.of(upload1, upload2));

        Path uploadPath1 = Mockito.mock(Path.class);
        Path uploadPath2 = Mockito.mock(Path.class);
        Mockito.when(fs.pathOf("/file-uploads/abcd-1234")).thenReturn(uploadPath1);
        Mockito.when(fs.pathOf("/file-uploads/wxyz-9999")).thenReturn(uploadPath2);

        InputStream stream1 = Mockito.mock(InputStream.class);
        InputStream stream2 = Mockito.mock(InputStream.class);
        Mockito.when(fs.newInputStream(uploadPath1)).thenReturn(stream1);
        Mockito.when(fs.newInputStream(uploadPath2)).thenReturn(stream2);

        handler.handleAuthenticated(ctx);

        Mockito.verify(templateService).addTemplate(stream1);
        Mockito.verifyNoMoreInteractions(templateService);
        Mockito.verify(fs).deleteIfExists(uploadPath1);
        Mockito.verify(fs).deleteIfExists(uploadPath2);
        Mockito.verify(ctx).response();
        Mockito.verify(resp).end();
    }
}
