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

import static org.mockito.Mockito.lenient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.core.templates.LocalStorageTemplateService;
import io.cryostat.core.templates.MutableTemplateService.InvalidXmlException;
import io.cryostat.messaging.notifications.Notification;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;

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
    @Mock NotificationFactory notificationFactory;
    @Mock Notification notification;
    @Mock Notification.Builder notificationBuilder;

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
        this.handler =
                new TemplatesPostHandler(auth, templateService, fs, notificationFactory, logger);
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
    void shouldHaveExpectedRequiredPermissions() {
        MatcherAssert.assertThat(
                handler.resourceActions(),
                Matchers.equalTo(Set.of(ResourceAction.CREATE_TEMPLATE)));
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
    void shouldThrowIfTemplateUploadNameInvalid() throws Exception {
        RoutingContext ctx = Mockito.mock(RoutingContext.class);

        FileUpload upload = Mockito.mock(FileUpload.class);
        Mockito.when(upload.name()).thenReturn("invalidUploadName");
        Mockito.when(upload.uploadedFileName()).thenReturn("/file-uploads/abcd-1234");

        Mockito.when(ctx.fileUploads()).thenReturn(Set.of(upload));

        Path uploadPath = Mockito.mock(Path.class);
        Mockito.when(fs.pathOf("/file-uploads/abcd-1234")).thenReturn(uploadPath);

        InputStream stream = Mockito.mock(InputStream.class);
        Mockito.when(fs.newInputStream(Mockito.any())).thenReturn(stream);

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

        FileUpload upload = Mockito.mock(FileUpload.class);
        Mockito.when(upload.name()).thenReturn("template");
        Mockito.when(upload.uploadedFileName()).thenReturn("/file-uploads/abcd-1234");

        Mockito.when(ctx.fileUploads()).thenReturn(Set.of(upload));

        Path uploadPath = Mockito.mock(Path.class);
        Mockito.when(fs.pathOf("/file-uploads/abcd-1234")).thenReturn(uploadPath);

        InputStream stream = Mockito.mock(InputStream.class);
        Mockito.when(fs.newInputStream(uploadPath)).thenReturn(stream);

        handler.handleAuthenticated(ctx);

        Mockito.verify(templateService).addTemplate(stream);
        Mockito.verifyNoMoreInteractions(templateService);
        Mockito.verify(fs).deleteIfExists(uploadPath);
        Mockito.verify(ctx).response();
        Mockito.verify(resp).end();
    }

    @Test
    void shouldSendNotifcationOnTemplateDeletion() throws Exception {
        RoutingContext ctx = Mockito.mock(RoutingContext.class);

        HttpServerResponse resp = Mockito.mock(HttpServerResponse.class);
        Mockito.when(ctx.response()).thenReturn(resp);

        FileUpload upload = Mockito.mock(FileUpload.class);
        Mockito.when(upload.name()).thenReturn("template");
        Mockito.when(upload.uploadedFileName()).thenReturn("/file-uploads/abcd-1234");

        Mockito.when(ctx.fileUploads()).thenReturn(Set.of(upload));

        Path uploadPath = Mockito.mock(Path.class);
        Mockito.when(fs.pathOf("/file-uploads/abcd-1234")).thenReturn(uploadPath);

        InputStream stream = Mockito.mock(InputStream.class);
        Mockito.when(fs.newInputStream(uploadPath)).thenReturn(stream);

        handler.handleAuthenticated(ctx);

        Mockito.verify(notificationFactory).createBuilder();
        Mockito.verify(notificationBuilder).metaCategory("TemplateUploaded");
        Mockito.verify(notificationBuilder).metaType(HttpMimeType.JSON);
        Mockito.verify(notificationBuilder).message(Map.of("template", "/file-uploads/abcd-1234"));
        Mockito.verify(notificationBuilder).build();
        Mockito.verify(notification).send();
    }
}
