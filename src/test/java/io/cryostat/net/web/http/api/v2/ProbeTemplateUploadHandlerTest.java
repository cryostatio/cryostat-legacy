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
package io.cryostat.net.web.http.api.v2;

import static org.mockito.Mockito.lenient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.cryostat.MainModule;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.agent.LocalProbeTemplateService;
import io.cryostat.core.agent.ProbeTemplate;
import io.cryostat.core.agent.ProbeValidationException;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.messaging.notifications.Notification;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;

import com.google.gson.Gson;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.FileUpload;
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
public class ProbeTemplateUploadHandlerTest {

    ProbeTemplateUploadHandler handler;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock LocalProbeTemplateService templateService;
    @Mock FileSystem fs;
    @Mock Logger logger;
    @Mock NotificationFactory notificationFactory;
    @Mock Notification notification;
    @Mock Notification.Builder notificationBuilder;
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
        this.handler =
                new ProbeTemplateUploadHandler(
                        auth,
                        credentialsManager,
                        notificationFactory,
                        templateService,
                        logger,
                        fs,
                        gson);
    }

    @Nested
    class BasicHandlerDefinition {
        @Test
        void shouldBePOSTHandler() {
            MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.POST));
        }

        @Test
        void shouldBeAPIV2() {
            MatcherAssert.assertThat(handler.apiVersion(), Matchers.equalTo(ApiVersion.V2));
        }

        @Test
        void shouldHaveExpectedPath() {
            MatcherAssert.assertThat(
                    handler.path(), Matchers.equalTo("/api/v2/probes/:probetemplateName"));
        }

        @Test
        void shouldHaveExpectedRequiredPermissions() {
            MatcherAssert.assertThat(
                    handler.resourceActions(),
                    Matchers.equalTo(Set.of(ResourceAction.CREATE_PROBE_TEMPLATE)));
        }

        @Test
        void shouldProducePlaintext() {
            MatcherAssert.assertThat(
                    handler.produces(), Matchers.equalTo(List.of(HttpMimeType.PLAINTEXT)));
        }

        @Test
        void shouldRequireAuthentication() {
            MatcherAssert.assertThat(handler.requiresAuthentication(), Matchers.is(true));
        }
    }

    @Nested
    class RequestHandling {

        @Mock RequestParameters requestParams;

        @Test
        void shouldRespond500WhenUploadFails() throws Exception {
            FileUpload upload = Mockito.mock(FileUpload.class);
            Mockito.when(upload.name()).thenReturn("probeTemplate");
            Mockito.when(requestParams.getFileUploads()).thenReturn(Set.of(upload));
            Mockito.when(requestParams.getPathParams())
                    .thenReturn(Map.of("probetemplateName", "foo.xml"));

            Mockito.when(upload.uploadedFileName()).thenReturn("/file-uploads/abcd-1234");
            Path uploadPath = Mockito.mock(Path.class);
            Mockito.when(fs.pathOf("/file-uploads/abcd-1234")).thenReturn(uploadPath);

            Mockito.when(fs.newInputStream(Mockito.any())).thenThrow(IOException.class);
            ApiException ex =
                    Assertions.assertThrows(
                            ApiException.class, () -> handler.handle(requestParams));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(500));
            Mockito.verify(fs).deleteIfExists(uploadPath);
        }

        @Test
        void shouldRespond400IfXmlInvalid() throws Exception {
            FileUpload upload = Mockito.mock(FileUpload.class);
            Mockito.when(upload.name()).thenReturn("probeTemplate");
            Mockito.when(upload.uploadedFileName()).thenReturn("/file-uploads/abcd-1234");

            Mockito.when(requestParams.getFileUploads()).thenReturn(Set.of(upload));
            Mockito.when(requestParams.getPathParams())
                    .thenReturn(Map.of("probetemplateName", "foo.xml"));

            Path uploadPath = Mockito.mock(Path.class);
            Mockito.when(fs.pathOf("/file-uploads/abcd-1234")).thenReturn(uploadPath);

            InputStream stream = Mockito.mock(InputStream.class);
            Mockito.when(fs.newInputStream(Mockito.any())).thenReturn(stream);

            Mockito.doThrow(ProbeValidationException.class)
                    .when(templateService)
                    .addTemplate(stream, "foo.xml");

            ApiException ex =
                    Assertions.assertThrows(
                            ApiException.class, () -> handler.handle(requestParams));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
            Mockito.verify(fs).deleteIfExists(uploadPath);
        }

        @Test
        void shouldRespond400IfFileExists() throws Exception {
            FileUpload upload = Mockito.mock(FileUpload.class);
            Mockito.when(upload.name()).thenReturn("probeTemplate");
            Mockito.when(upload.uploadedFileName()).thenReturn("/file-uploads/abcd-1234");

            Mockito.when(requestParams.getFileUploads()).thenReturn(Set.of(upload));
            Mockito.when(requestParams.getPathParams())
                    .thenReturn(Map.of("probetemplateName", "foo.xml"));

            Path uploadPath = Mockito.mock(Path.class);
            Mockito.when(fs.pathOf("/file-uploads/abcd-1234")).thenReturn(uploadPath);

            InputStream stream = Mockito.mock(InputStream.class);
            Mockito.when(fs.newInputStream(Mockito.any())).thenReturn(stream);

            Mockito.doThrow(FileAlreadyExistsException.class)
                    .when(templateService)
                    .addTemplate(stream, "foo.xml");

            ApiException ex =
                    Assertions.assertThrows(
                            ApiException.class, () -> handler.handle(requestParams));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
            Mockito.verify(fs).deleteIfExists(uploadPath);
        }

        @Test
        void shouldProcessGoodRequest() throws Exception {
            String templateName = "foo.xml";
            FileUpload upload = Mockito.mock(FileUpload.class);
            Mockito.when(upload.name()).thenReturn("probeTemplate");
            Mockito.when(upload.uploadedFileName()).thenReturn("/file-uploads/abcd-1234");

            Mockito.when(requestParams.getFileUploads()).thenReturn(Set.of(upload));
            Mockito.when(requestParams.getPathParams())
                    .thenReturn(Map.of("probetemplateName", templateName));

            Path uploadPath = Mockito.mock(Path.class);
            Mockito.when(fs.pathOf("/file-uploads/abcd-1234")).thenReturn(uploadPath);

            InputStream stream = Mockito.mock(InputStream.class);
            Mockito.when(fs.newInputStream(uploadPath)).thenReturn(stream);

            ProbeTemplate template = Mockito.mock(ProbeTemplate.class);
            String templateContent = "someContent";
            Mockito.when(templateService.addTemplate(stream, templateName)).thenReturn(template);
            Mockito.when(template.serialize()).thenReturn(templateContent);

            IntermediateResponse<Void> response = handler.handle(requestParams);

            Mockito.verify(templateService).addTemplate(stream, templateName);
            Mockito.verify(notificationBuilder)
                    .message(
                            Map.of(
                                    "probeTemplate",
                                    templateName,
                                    "templateContent",
                                    templateContent));
            Mockito.verifyNoMoreInteractions(templateService);
            Mockito.verify(fs).deleteIfExists(uploadPath);

            MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(200));
            MatcherAssert.assertThat(response.getBody(), Matchers.nullValue());
        }
    }
}
