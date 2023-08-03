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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.HttpException;
import io.vertx.ext.web.multipart.MultipartForm;
import itest.bases.StandardSelfTest;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TemplatePostDeleteIT extends StandardSelfTest {
    static final String INVALID_TEMPLATE_FILE_NAME = "invalidTemplate.xml";
    static final String SANITIZE_TEMPLATE_FILE_NAME = "TemplateToSanitize.jfc";
    static final String TEMPLATE_NAME = "invalidTemplate";
    static final String MEDIA_TYPE = "application/xml";
    static final String REQ_URL = "/api/v1/templates";

    @Test
    public void shouldThrowIfTemplateUploadNameInvalid() throws Exception {

        CompletableFuture<Integer> response = new CompletableFuture<>();
        ClassLoader classLoader = getClass().getClassLoader();
        File invalidTemplate =
                new File(classLoader.getResource(INVALID_TEMPLATE_FILE_NAME).getFile());
        String path = invalidTemplate.getAbsolutePath();

        MultipartForm form =
                MultipartForm.create()
                        .attribute("invalidTemplateAttribute", INVALID_TEMPLATE_FILE_NAME)
                        .binaryFileUpload(
                                TEMPLATE_NAME, INVALID_TEMPLATE_FILE_NAME, path, MEDIA_TYPE);

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
    public void shouldThrowWhenPostingInvalidTemplate() throws Exception {

        CompletableFuture<Integer> response = new CompletableFuture<>();
        ClassLoader classLoader = getClass().getClassLoader();
        File invalidTemplate =
                new File(classLoader.getResource(INVALID_TEMPLATE_FILE_NAME).getFile());
        String path = invalidTemplate.getAbsolutePath();

        MultipartForm form =
                MultipartForm.create()
                        .attribute("template", INVALID_TEMPLATE_FILE_NAME)
                        .binaryFileUpload(
                                TEMPLATE_NAME, INVALID_TEMPLATE_FILE_NAME, path, MEDIA_TYPE);

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
    public void testDeleteRecordingThrowsOnNonExistentTemplate() throws Exception {

        CompletableFuture<Void> response = new CompletableFuture<>();

        webClient
                .delete(String.format("%s/%s", REQ_URL, INVALID_TEMPLATE_FILE_NAME))
                .send(
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(ExecutionException.class, () -> response.get());
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(404));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Not Found"));
    }

    @Test
    public void testPostedTemplateIsSanitizedAndCanBeDeleted() throws Exception {
        try {
            CompletableFuture<Integer> postResponse = new CompletableFuture<>();
            ClassLoader classLoader = getClass().getClassLoader();
            File templateToSanitize =
                    new File(classLoader.getResource(SANITIZE_TEMPLATE_FILE_NAME).getFile());
            String path = templateToSanitize.getAbsolutePath();
            MultipartForm form =
                    MultipartForm.create()
                            .attribute("template", SANITIZE_TEMPLATE_FILE_NAME)
                            .binaryFileUpload(
                                    "template", SANITIZE_TEMPLATE_FILE_NAME, path, MEDIA_TYPE);
            webClient
                    .post(REQ_URL)
                    .sendMultipartForm(
                            form,
                            ar -> {
                                assertRequestStatus(ar, postResponse);
                                postResponse.complete(ar.result().statusCode());
                            });
            MatcherAssert.assertThat(postResponse.get(), Matchers.equalTo(200));

            CompletableFuture<JsonArray> getResponse = new CompletableFuture<>();
            webClient
                    .get("/api/v1/targets/localhost:0/templates")
                    .send(
                            ar -> {
                                assertRequestStatus(ar, getResponse);
                                JsonArray response = ar.result().bodyAsJsonArray();
                                getResponse.complete(response);
                            });
            boolean foundSanitizedTemplate = false;
            for (Object o : getResponse.get()) {
                JsonObject json = (JsonObject) o;
                foundSanitizedTemplate =
                        foundSanitizedTemplate
                                || json.getString("name").equals("Template_To_Sanitize");
            }
            Assertions.assertTrue(foundSanitizedTemplate);
        } finally {
            CompletableFuture<Integer> deleteResponse = new CompletableFuture<>();
            webClient
                    .delete(REQ_URL + "/Template_To_Sanitize")
                    .send(
                            ar -> {
                                assertRequestStatus(ar, deleteResponse);
                                deleteResponse.complete(ar.result().statusCode());
                            });
            MatcherAssert.assertThat(deleteResponse.get(), Matchers.equalTo(200));
        }
    }
}
