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
package com.redhat.rhjmc.containerjfr.net.web.http.api.v2;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

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
import com.redhat.rhjmc.containerjfr.core.sys.Environment;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.net.AuthManager;
import com.redhat.rhjmc.containerjfr.net.security.CertificateValidator;

import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.impl.HttpStatusException;

@ExtendWith(MockitoExtension.class)
class CertificatePostHandlerTest {

    CertificatePostHandler handler;
    @Mock AuthManager auth;
    @Mock Environment env;
    @Mock FileSystem fs;
    @Mock Logger logger;

    @Mock RoutingContext ctx;
    @Mock FileOutputStream outStream;
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
                        auth, env, fs, logger, (file) -> outStream, certValidator);
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
    void shouldThrow400IfNoCertInRequest() {
        Mockito.when(ctx.fileUploads()).thenReturn(Collections.<FileUpload>emptySet());
        HttpStatusException ex =
                Assertions.assertThrows(
                        HttpStatusException.class, () -> handler.handleAuthenticated(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
    }

    @Test
    void shouldThrow500IfNoTruststoreDirSet() {
        Mockito.when(ctx.fileUploads()).thenReturn(Set.<FileUpload>of(fu));
        Mockito.when(fu.name()).thenReturn("cert");
        Mockito.when(env.hasEnv(Mockito.any())).thenReturn(false);
        HttpStatusException ex =
                Assertions.assertThrows(
                        HttpStatusException.class, () -> handler.handleAuthenticated(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(500));
    }

    @Test
    void shouldThrow409IfCertAlreadyExists() {
        Mockito.when(ctx.fileUploads()).thenReturn(Set.<FileUpload>of(fu));
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
        HttpStatusException ex =
                Assertions.assertThrows(
                        HttpStatusException.class, () -> handler.handleAuthenticated(ctx));
        MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(409));
    }

    @Test
    void shouldThrowExceptionIfCertIsMalformed() throws Exception {
        Mockito.when(ctx.fileUploads()).thenReturn(Set.<FileUpload>of(fu));
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
        Mockito.when(certValidator.parseCertificate(Mockito.any()))
                .thenThrow(new CertificateException("parsing error"));

        CertificateException ex =
                Assertions.assertThrows(
                        CertificateException.class, () -> handler.handleAuthenticated(ctx));
        MatcherAssert.assertThat(ex.getMessage(), Matchers.equalTo("parsing error"));
    }

    @Test
    void shouldAddCertToTruststore() throws Exception {
        Mockito.when(ctx.fileUploads()).thenReturn(Set.<FileUpload>of(fu));
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
        Mockito.when(certValidator.parseCertificate(Mockito.any())).thenReturn(certificates);
        Mockito.when(certificates.iterator()).thenReturn(iterator);
        Mockito.when(iterator.hasNext()).thenReturn(true).thenReturn(false);
        Mockito.when(iterator.next()).thenReturn(cert);
        Mockito.when(cert.getEncoded()).thenReturn("certificate".getBytes());

        HttpServerResponse resp = Mockito.mock(HttpServerResponse.class);
        Mockito.when(ctx.response()).thenReturn(resp);

        handler.handleAuthenticated(ctx);

        Mockito.verify(outStream).write("certificate".getBytes());
        Mockito.verify(resp).end("Saved: /truststore/certificate.cer");
    }
}
