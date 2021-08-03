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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.cryostat.net.web.http.HttpMimeType;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.impl.HttpStatusException;
import itest.bases.StandardSelfTest;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ReportIT extends StandardSelfTest {

    static final String TEST_RECORDING_NAME = "someRecording";
    static final String REPORT_REQ_URL = "/api/v1/reports/";
    static final String RECORDING_REQ_URL =
            String.format("/api/v1/targets/%s/recordings", SELF_REFERENCE_TARGET_ID);
    static final String TEMP_REPORT = "src/test/resources/reportTest.html";
    static File file;
    static Document doc;

    @Test
    void testGetReportShouldSendFile() throws Exception {

        CompletableFuture<String> savedRecordingName = new CompletableFuture<>();

        try {
            // Create a recording
            CompletableFuture<JsonObject> postResponse = new CompletableFuture<>();
            MultiMap form = MultiMap.caseInsensitiveMultiMap();
            form.add("recordingName", TEST_RECORDING_NAME);
            form.add("duration", "1");
            form.add("events", "template=ALL");

            webClient
                    .post(RECORDING_REQ_URL)
                    .sendForm(
                            form,
                            ar -> {
                                if (assertRequestStatus(ar, postResponse)) {
                                    MatcherAssert.assertThat(
                                            ar.result().statusCode(), Matchers.equalTo(201));
                                    postResponse.complete(null);
                                }
                            });

            postResponse.get();

            // Save the recording to archive
            webClient
                    .patch(String.format("%s/%s", RECORDING_REQ_URL, TEST_RECORDING_NAME))
                    .sendBuffer(
                            Buffer.buffer("SAVE"),
                            ar -> {
                                if (assertRequestStatus(ar, savedRecordingName)) {
                                    MatcherAssert.assertThat(
                                            ar.result().statusCode(), Matchers.equalTo(200));
                                    MatcherAssert.assertThat(
                                            ar.result()
                                                    .getHeader(HttpHeaders.CONTENT_TYPE.toString()),
                                            Matchers.equalTo(HttpMimeType.PLAINTEXT.mime()));
                                    savedRecordingName.complete(ar.result().bodyAsString());
                                }
                            });

            savedRecordingName.get();

            // Get a report for the above recording
            CompletableFuture<Buffer> getResponse = new CompletableFuture<>();
            webClient
                    .get(String.format("%s/%s", REPORT_REQ_URL, savedRecordingName.get()))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, getResponse)) {
                                    MatcherAssert.assertThat(
                                            ar.result().statusCode(), Matchers.equalTo(200));
                                    MatcherAssert.assertThat(
                                            ar.result()
                                                    .getHeader(HttpHeaders.CONTENT_TYPE.toString()),
                                            Matchers.equalTo(HttpMimeType.HTML.mime()));
                                    getResponse.complete(ar.result().bodyAsBuffer());
                                }
                            });

            file = new File(TEMP_REPORT);

            try (FileOutputStream fos = new FileOutputStream(file);
                    BufferedOutputStream bos = new BufferedOutputStream(fos)) {

                byte[] bytes = getResponse.get().getBytes();
                bos.write(bytes);
            }

            doc = Jsoup.parse(file, "UTF-8");

            MatcherAssert.assertThat(file.length(), Matchers.greaterThan(0L));

            Elements head = doc.getElementsByTag("head");
            Elements titles = head.first().getElementsByTag("title");
            Elements body = doc.getElementsByTag("body");
            Elements script = head.first().getElementsByTag("script");

            MatcherAssert.assertThat("Expected one <head>", head.size(), Matchers.equalTo(1));
            MatcherAssert.assertThat(titles.size(), Matchers.equalTo(1));
            MatcherAssert.assertThat("Expected one <body>", body.size(), Matchers.equalTo(1));
            MatcherAssert.assertThat(
                    "Expected at least one <script>",
                    script.size(),
                    Matchers.greaterThanOrEqualTo(1));

        } finally {
            file.delete();

            // Clean up recording
            CompletableFuture<JsonObject> deleteActiveRecResponse = new CompletableFuture<>();
            webClient
                    .delete(String.format("%s/%s", RECORDING_REQ_URL, TEST_RECORDING_NAME))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, deleteActiveRecResponse)) {
                                    MatcherAssert.assertThat(
                                            ar.result().statusCode(), Matchers.equalTo(200));
                                    deleteActiveRecResponse.complete(
                                            ar.result().bodyAsJsonObject());
                                }
                            });

            MatcherAssert.assertThat(deleteActiveRecResponse.get(), Matchers.equalTo(null));

            CompletableFuture<JsonObject> deleteArchivedRecResp = new CompletableFuture<>();
            webClient
                    .delete(String.format("/api/v1/recordings/%s", savedRecordingName.get()))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, deleteArchivedRecResp)) {
                                    MatcherAssert.assertThat(
                                            ar.result().statusCode(), Matchers.equalTo(200));
                                    deleteArchivedRecResp.complete(ar.result().bodyAsJsonObject());
                                }
                            });

            MatcherAssert.assertThat(deleteArchivedRecResp.get(), Matchers.equalTo(null));
        }
    }

    @Test
    void testGetReportThrowsWithNonExistentRecordingName() throws Exception {

        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        webClient
                .get(String.format("%s/%s", REPORT_REQ_URL, TEST_RECORDING_NAME))
                .send(
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(ExecutionException.class, () -> response.get());
        MatcherAssert.assertThat(
                ((HttpStatusException) ex.getCause()).getStatusCode(), Matchers.equalTo(404));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Not Found"));
    }
}
