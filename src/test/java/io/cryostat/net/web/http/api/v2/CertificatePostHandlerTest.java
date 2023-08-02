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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import io.cryostat.MainModule;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Environment;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.CertificateValidator;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiData;
import io.cryostat.net.web.http.api.ApiMeta;
import io.cryostat.net.web.http.api.ApiResponse;
import io.cryostat.net.web.http.api.ApiResultData;

import com.google.gson.Gson;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.RoutingContext;
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
class CertificatePostHandlerTest {

    CertificatePostHandler handler;
    @Mock AuthManager auth;
    @Mock CredentialsManager credentialsManager;
    @Mock Environment env;
    @Mock FileSystem fs;
    @Mock Logger logger;
    Gson gson = MainModule.provideGson(logger);

    @Mock RoutingContext ctx;
    @Mock FileOutputStream outStream;
    @Mock Function<File, FileOutputStream> outputStreamFunction;
    @Mock FileUpload fu;
    @Mock Path truststorePath;
    @Mock Path fileUploadPath;
    @Mock CertificateValidator certValidator;
    @Mock Collection certificates;
    @Mock Iterator iterator;
    @Mock Certificate cert;

    @BeforeEach
    void setup() {
        this.handler =
                new CertificatePostHandler(
                        auth,
                        credentialsManager,
                        env,
                        fs,
                        gson,
                        outputStreamFunction,
                        certValidator);

        HttpServerRequest req = Mockito.mock(HttpServerRequest.class);
        MultiMap headers = MultiMap.caseInsensitiveMultiMap();
        headers.set(HttpHeaders.AUTHORIZATION, "abcd1234==");
        Mockito.lenient().when(req.headers()).thenReturn(headers);
        Mockito.lenient().when(ctx.request()).thenReturn(req);

        Mockito.lenient()
                .when(auth.validateHttpHeader(Mockito.any(), Mockito.any()))
                .thenReturn(CompletableFuture.completedFuture(true));
    }

    @Test
    void shouldHandlePOST() {
        MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.POST));
    }

    @Test
    void shouldHandleCorrectPath() {
        MatcherAssert.assertThat(handler.path(), Matchers.equalTo("/api/v2/certificates"));
    }

    @Test
    void shouldHaveExpectedRequiredPermissions() {
        MatcherAssert.assertThat(
                handler.resourceActions(),
                Matchers.equalTo(Set.of(ResourceAction.CREATE_CERTIFICATE)));
    }

    @Test
    void shouldThrow400IfNoCertInRequest() {
        Mockito.when(ctx.fileUploads()).thenReturn(List.of());
        RequestBody body = Mockito.mock(RequestBody.class);
        Mockito.when(ctx.body()).thenReturn(body);
        ApiException ex = Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
    }

    @Test
    void shouldThrow500IfNoTruststoreDirSet() {
        Mockito.when(ctx.fileUploads()).thenReturn(List.of(fu));
        RequestBody body = Mockito.mock(RequestBody.class);
        Mockito.when(ctx.body()).thenReturn(body);
        Mockito.when(fu.name()).thenReturn("cert");
        Mockito.when(env.hasEnv(Mockito.any())).thenReturn(false);
        ApiException ex = Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(500));
    }

    @Test
    void shouldThrow409IfCertAlreadyExists() {
        Mockito.when(ctx.fileUploads()).thenReturn(List.of(fu));
        RequestBody body = Mockito.mock(RequestBody.class);
        Mockito.when(ctx.body()).thenReturn(body);
        Mockito.when(fu.name()).thenReturn("cert");
        Mockito.when(fu.fileName()).thenReturn("certificate.cer");
        Mockito.when(fu.uploadedFileName()).thenReturn("/temp/temp.cer");
        Mockito.when(fs.pathOf("/temp/temp.cer")).thenReturn(fileUploadPath);
        Mockito.when(env.hasEnv(Mockito.any())).thenReturn(true);
        Mockito.when(env.getEnv(Mockito.any())).thenReturn("/truststore");
        Mockito.when(fs.pathOf("/truststore", "certificate.cer")).thenReturn(truststorePath);
        Mockito.when(truststorePath.normalize()).thenReturn(truststorePath);
        Mockito.when(truststorePath.toString()).thenReturn("/truststore/certificate.cer");
        Mockito.when(fs.exists(Mockito.any())).thenReturn(true);
        ApiException ex = Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(409));
    }

    @Test
    void shouldThrowExceptionIfCertIsMalformed() throws Exception {
        Mockito.when(ctx.fileUploads()).thenReturn(List.of(fu));
        RequestBody body = Mockito.mock(RequestBody.class);
        Mockito.when(ctx.body()).thenReturn(body);
        Mockito.when(fu.name()).thenReturn("cert");
        Mockito.when(fu.fileName()).thenReturn("certificate.cer");
        Mockito.when(fu.uploadedFileName()).thenReturn("/temp/temp.cer");
        Mockito.when(fs.pathOf("/temp/temp.cer")).thenReturn(fileUploadPath);
        Mockito.when(env.hasEnv(Mockito.any())).thenReturn(true);
        Mockito.when(env.getEnv(Mockito.any())).thenReturn("/truststore");
        Mockito.when(fs.pathOf("/truststore", "certificate.cer")).thenReturn(truststorePath);
        Mockito.when(truststorePath.normalize()).thenReturn(truststorePath);
        Mockito.when(fs.exists(Mockito.any())).thenReturn(false);

        InputStream instream = new ByteArrayInputStream("not a certificate".getBytes());
        Mockito.when(fs.newInputStream(fileUploadPath)).thenReturn(instream);
        Mockito.when(certValidator.parseCertificates(Mockito.any()))
                .thenThrow(new CertificateException("parsing error"));

        ApiException ex = Assertions.assertThrows(ApiException.class, () -> handler.handle(ctx));
        MatcherAssert.assertThat(ex.getFailureReason(), Matchers.equalTo("parsing error"));
        Mockito.verify(outputStreamFunction, Mockito.never()).apply(Mockito.any());
        Mockito.verify(outStream, Mockito.never()).write(Mockito.any());
    }

    @Test
    void shouldAddCertToTruststore() throws Exception {
        Mockito.when(ctx.fileUploads()).thenReturn(List.of(fu));
        RequestBody body = Mockito.mock(RequestBody.class);
        Mockito.when(ctx.body()).thenReturn(body);
        Mockito.when(fu.name()).thenReturn("cert");
        Mockito.when(fu.fileName()).thenReturn("certificate.cer");
        Mockito.when(fu.uploadedFileName()).thenReturn("/temp/temp.cer");
        Mockito.when(fs.pathOf("/temp/temp.cer")).thenReturn(fileUploadPath);
        Mockito.when(env.hasEnv(Mockito.any())).thenReturn(true);
        Mockito.when(env.getEnv(Mockito.any())).thenReturn("/truststore");
        Mockito.when(fs.pathOf("/truststore", "certificate.cer")).thenReturn(truststorePath);
        Mockito.when(truststorePath.normalize()).thenReturn(truststorePath);
        Mockito.when(truststorePath.toString()).thenReturn("/truststore/certificate.cer");
        Mockito.when(fs.exists(Mockito.any())).thenReturn(false);

        InputStream instream = new ByteArrayInputStream("certificate".getBytes());
        Mockito.when(fs.newInputStream(fileUploadPath)).thenReturn(instream);

        Mockito.when(outputStreamFunction.apply(Mockito.any())).thenReturn(outStream);
        Mockito.when(certValidator.parseCertificates(Mockito.any())).thenReturn(certificates);
        Mockito.when(certificates.iterator()).thenReturn(iterator);
        Mockito.when(iterator.hasNext()).thenReturn(true).thenReturn(false);
        Mockito.when(iterator.next()).thenReturn(cert);
        Mockito.when(cert.getEncoded()).thenReturn("certificate".getBytes());

        HttpServerResponse resp = Mockito.mock(HttpServerResponse.class);
        Mockito.when(ctx.response()).thenReturn(resp);

        handler.handle(ctx);

        Mockito.verify(outStream).write("certificate".getBytes());

        ApiMeta meta = new ApiMeta(HttpMimeType.PLAINTEXT);
        ApiData data = new ApiResultData(truststorePath);
        ApiResponse expected = new ApiResponse(meta, data);

        Mockito.verify(resp).end(gson.toJson(expected));
    }
}
