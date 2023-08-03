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

import static org.mockito.Mockito.lenient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.core.templates.LocalStorageTemplateService;
import io.cryostat.core.templates.MutableTemplateService.InvalidXmlException;
import io.cryostat.core.templates.Template;
import io.cryostat.core.templates.TemplateType;
import io.cryostat.messaging.notifications.Notification;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;

import io.vertx.core.http.HttpMethod;
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
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TemplatesPostHandlerTest {

    TemplatesPostHandler handler;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
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
                new TemplatesPostHandler(
                        auth, credentialsManager, templateService, fs, notificationFactory, logger);
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
        Mockito.when(upload.name()).thenReturn("template");
        Mockito.when(ctx.fileUploads()).thenReturn(List.of(upload));
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

        Mockito.when(ctx.fileUploads()).thenReturn(List.of(upload));

        Path uploadPath = Mockito.mock(Path.class);
        Mockito.when(fs.pathOf("/file-uploads/abcd-1234")).thenReturn(uploadPath);

        InputStream stream = Mockito.mock(InputStream.class);
        Mockito.when(fs.newInputStream(Mockito.any())).thenReturn(stream);

        Mockito.when(templateService.addTemplate(Mockito.any()))
                .thenThrow(InvalidXmlException.class);

        HttpException ex =
                Assertions.assertThrows(
                        HttpException.class, () -> handler.handleAuthenticated(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
        Mockito.verify(fs).deleteIfExists(uploadPath);
    }

    @Test
    void shouldThrowIfTemplateUploadNameInvalid() throws Exception {
        RoutingContext ctx = Mockito.mock(RoutingContext.class);

        FileUpload upload = Mockito.mock(FileUpload.class);
        Mockito.when(upload.name()).thenReturn("invalidUploadName");
        Mockito.when(upload.uploadedFileName()).thenReturn("/file-uploads/abcd-1234");

        Mockito.when(ctx.fileUploads()).thenReturn(List.of(upload));

        Path uploadPath = Mockito.mock(Path.class);
        Mockito.when(fs.pathOf("/file-uploads/abcd-1234")).thenReturn(uploadPath);

        HttpException ex =
                Assertions.assertThrows(
                        HttpException.class, () -> handler.handleAuthenticated(ctx));
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

        Mockito.when(ctx.fileUploads()).thenReturn(List.of(upload));

        Path uploadPath = Mockito.mock(Path.class);
        Mockito.when(fs.pathOf("/file-uploads/abcd-1234")).thenReturn(uploadPath);

        InputStream stream = Mockito.mock(InputStream.class);
        Mockito.when(fs.newInputStream(uploadPath)).thenReturn(stream);

        Template template =
                new Template(
                        "MyTemplate",
                        "some description",
                        "Cryostat unit tests",
                        TemplateType.CUSTOM);
        Mockito.when(templateService.addTemplate(Mockito.any())).thenReturn(template);

        handler.handleAuthenticated(ctx);

        Mockito.verify(templateService).addTemplate(stream);
        Mockito.verifyNoMoreInteractions(templateService);
        Mockito.verify(fs).deleteIfExists(uploadPath);
        Mockito.verify(ctx).response();
        Mockito.verify(resp).end();
    }

    @Test
    void shouldSendNotifcationOnTemplateCreation() throws Exception {
        RoutingContext ctx = Mockito.mock(RoutingContext.class);

        HttpServerResponse resp = Mockito.mock(HttpServerResponse.class);
        Mockito.when(ctx.response()).thenReturn(resp);

        FileUpload upload = Mockito.mock(FileUpload.class);
        Mockito.when(upload.name()).thenReturn("template");
        Mockito.when(upload.uploadedFileName()).thenReturn("/file-uploads/abcd-1234");

        Mockito.when(ctx.fileUploads()).thenReturn(List.of(upload));

        Path uploadPath = Mockito.mock(Path.class);
        Mockito.when(fs.pathOf("/file-uploads/abcd-1234")).thenReturn(uploadPath);

        InputStream stream = Mockito.mock(InputStream.class);
        Mockito.when(fs.newInputStream(uploadPath)).thenReturn(stream);

        Template template =
                new Template(
                        "MyTemplate",
                        "some description",
                        "Cryostat unit tests",
                        TemplateType.CUSTOM);
        Mockito.when(templateService.addTemplate(Mockito.any())).thenReturn(template);

        handler.handleAuthenticated(ctx);

        Mockito.verify(notificationFactory).createBuilder();
        Mockito.verify(notificationBuilder).metaCategory("TemplateUploaded");
        Mockito.verify(notificationBuilder).metaType(HttpMimeType.JSON);
        Mockito.verify(notificationBuilder).message(Map.of("template", template));
        Mockito.verify(notificationBuilder).build();
        Mockito.verify(notification).send();
    }
}
