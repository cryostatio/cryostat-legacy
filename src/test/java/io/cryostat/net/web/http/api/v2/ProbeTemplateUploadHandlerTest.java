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
package io.cryostat.net.web.http.api.v2;

import static org.mockito.Mockito.lenient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.cryostat.MainModule;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.agent.LocalProbeTemplateService;
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
        void shouldProcessGoodRequest() throws Exception {
            FileUpload upload = Mockito.mock(FileUpload.class);
            Mockito.when(upload.name()).thenReturn("probeTemplate");
            Mockito.when(upload.uploadedFileName()).thenReturn("/file-uploads/abcd-1234");

            Mockito.when(requestParams.getFileUploads()).thenReturn(Set.of(upload));
            Mockito.when(requestParams.getPathParams())
                    .thenReturn(Map.of("probetemplateName", "foo.xml"));

            Path uploadPath = Mockito.mock(Path.class);
            Mockito.when(fs.pathOf("/file-uploads/abcd-1234")).thenReturn(uploadPath);

            InputStream stream = Mockito.mock(InputStream.class);
            Mockito.when(fs.newInputStream(uploadPath)).thenReturn(stream);
            Mockito.when(templateService.getTemplate(Mockito.anyString()))
                    .thenReturn("someContent");

            IntermediateResponse<Void> response = handler.handle(requestParams);

            Mockito.verify(templateService).addTemplate(stream, "foo.xml");
            Mockito.verify(notificationBuilder)
                    .message(
                            Map.of(
                                    "probeTemplate",
                                    "/file-uploads/abcd-1234",
                                    "templateName",
                                    "foo.xml",
                                    "templateContent",
                                    "someContent"));
            Mockito.verifyNoMoreInteractions(templateService);
            Mockito.verify(fs).deleteIfExists(uploadPath);

            MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(200));
            MatcherAssert.assertThat(response.getBody(), Matchers.nullValue());
        }
    }
}
