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
                    .get(String.format("/api/v1/targets/%s/templates", SELF_REFERENCE_TARGET_ID))
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
