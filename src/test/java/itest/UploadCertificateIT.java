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
package itest;

import java.io.File;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.cryostat.net.web.http.HttpMimeType;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.HttpException;
import io.vertx.ext.web.multipart.MultipartForm;
import itest.bases.StandardSelfTest;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class UploadCertificateIT extends StandardSelfTest {

    static final String CERT_NAME = "cert";
    static final String EMPTY_FILE_NAME = "empty.cer";
    static final String VALID_FILE_NAME = "valid.cert";
    static final String MEDIA_TYPE = "application/pkix-cert";
    static final String TRUSTSTORE_DIR = "/opt/cryostat.d/truststore.d";
    static final String REQ_URL = String.format("/api/v2/certificates");

    @Test
    public void shouldThrowOnNullCertificateUpload() throws Exception {

        CompletableFuture<Integer> response = new CompletableFuture<>();

        webClient
                .post(REQ_URL)
                .sendMultipartForm(
                        null,
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(ExecutionException.class, () -> response.get());
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Bad Request"));
    }

    @Test
    public void shouldNotAddEmptyCertToTrustStore() throws Exception {

        CompletableFuture<Integer> response = new CompletableFuture<>();
        ClassLoader classLoader = getClass().getClassLoader();
        File emptyCert = new File(classLoader.getResource(EMPTY_FILE_NAME).getFile());
        String path = emptyCert.getAbsolutePath();

        MultipartForm form =
                MultipartForm.create()
                        .attribute("name", CERT_NAME)
                        .binaryFileUpload(CERT_NAME, EMPTY_FILE_NAME, path, MEDIA_TYPE);

        webClient
                .post(REQ_URL)
                .sendMultipartForm(
                        form,
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(ExecutionException.class, () -> response.get());
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Bad Request"));
    }

    @Test
    public void shouldThrowWhenPostingWithoutCert() throws Exception {

        CompletableFuture<JsonObject> response = new CompletableFuture<>();

        webClient
                .post(REQ_URL)
                .send(
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(ExecutionException.class, () -> response.get());
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Bad Request"));
    }

    @Test
    public void shouldThrowOnDuplicateCert() throws Exception {

        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        ClassLoader classLoader = getClass().getClassLoader();
        File validCert = new File(classLoader.getResource(VALID_FILE_NAME).getFile());
        String path = validCert.getAbsolutePath();

        MultipartForm form =
                MultipartForm.create()
                        .attribute("name", CERT_NAME)
                        .binaryFileUpload(CERT_NAME, VALID_FILE_NAME, path, MEDIA_TYPE);

        webClient
                .post(REQ_URL)
                .sendMultipartForm(
                        form,
                        ar -> {
                            if (assertRequestStatus(ar, response)) {
                                MatcherAssert.assertThat(
                                        ar.result().statusCode(), Matchers.equalTo(200));
                                response.complete(ar.result().bodyAsJsonObject());
                            }
                        });
        JsonObject expectedResponse =
                new JsonObject(
                        Map.of(
                                "meta",
                                        Map.of(
                                                "type",
                                                HttpMimeType.PLAINTEXT.mime(),
                                                "status",
                                                "OK"),
                                "data",
                                        Map.of(
                                                "result",
                                                String.format(
                                                        "%s/%s",
                                                        TRUSTSTORE_DIR, VALID_FILE_NAME))));
        MatcherAssert.assertThat(response.get(), Matchers.equalTo(expectedResponse));

        CompletableFuture<JsonObject> duplicateResponse = new CompletableFuture<>();

        webClient
                .post(REQ_URL)
                .sendMultipartForm(
                        form,
                        ar -> {
                            assertRequestStatus(ar, duplicateResponse);
                        });
        ExecutionException ex =
                Assertions.assertThrows(ExecutionException.class, () -> duplicateResponse.get());
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(409));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Conflict"));
    }
}
