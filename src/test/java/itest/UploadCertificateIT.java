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
