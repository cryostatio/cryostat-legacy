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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import io.cryostat.net.web.http.HttpMimeType;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.HttpException;
import itest.bases.StandardSelfTest;
import itest.util.ITestCleanupFailedException;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TargetReportIT extends StandardSelfTest {

    static final String TEST_RECORDING_NAME = "someRecording";
    static final String REPORT_REQ_URL =
            String.format(
                    "/api/v1/targets/%s/reports/%s", SELF_REFERENCE_TARGET_ID, TEST_RECORDING_NAME);
    static final String RECORDING_REQ_URL =
            String.format("/api/v1/targets/%s/recordings", SELF_REFERENCE_TARGET_ID);
    static final String TEMP_REPORT = "src/test/resources/reportTest.html";
    static File file;
    static Document doc;

    @Test
    void testGetReportShouldSendFile() throws Exception {

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

            // Make a webserver request to generate some recording data
            CompletableFuture<JsonArray> targetGetResponse = new CompletableFuture<>();
            webClient
                    .get("/api/v1/targets")
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, targetGetResponse)) {
                                    MatcherAssert.assertThat(
                                            ar.result().statusCode(), Matchers.equalTo(200));
                                    MatcherAssert.assertThat(
                                            ar.result()
                                                    .getHeader(HttpHeaders.CONTENT_TYPE.toString()),
                                            Matchers.equalTo(HttpMimeType.JSON.mime()));
                                    targetGetResponse.complete(ar.result().bodyAsJsonArray());
                                }
                            });

            targetGetResponse.get();

            // Get a report for the above recording
            CompletableFuture<Buffer> getResponse = new CompletableFuture<>();
            webClient
                    .get(REPORT_REQ_URL)
                    .putHeader(HttpHeaders.ACCEPT.toString(), HttpMimeType.HTML.mime())
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

            // Get an unformatted filtered report for the above recording
            CompletableFuture<JsonObject> getUnformattedResponse = new CompletableFuture<>();
            webClient
                    .get(REPORT_REQ_URL)
                    .addQueryParam("filter", "heap")
                    .putHeader(HttpHeaders.ACCEPT.toString(), HttpMimeType.JSON.mime())
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, getUnformattedResponse)) {
                                    MatcherAssert.assertThat(
                                            ar.result().statusCode(), Matchers.equalTo(200));
                                    MatcherAssert.assertThat(
                                            ar.result()
                                                    .getHeader(HttpHeaders.CONTENT_TYPE.toString()),
                                            Matchers.equalTo(HttpMimeType.JSON.mime()));
                                    getUnformattedResponse.complete(ar.result().bodyAsJsonObject());
                                }
                            });

            JsonObject jsonResponse = getUnformattedResponse.get();
            MatcherAssert.assertThat(jsonResponse, Matchers.notNullValue());
            MatcherAssert.assertThat(jsonResponse.getMap(), Matchers.is(Matchers.aMapWithSize(8)));
            Assertions.assertTrue(jsonResponse.containsKey("HeapContent"));
            Assertions.assertTrue(jsonResponse.containsKey("StringDeduplication"));
            Assertions.assertTrue(jsonResponse.containsKey("PrimitiveToObjectConversion"));
            Assertions.assertTrue(jsonResponse.containsKey("GcFreedRatio"));
            Assertions.assertTrue(jsonResponse.containsKey("HighGc"));
            Assertions.assertTrue(jsonResponse.containsKey("Allocations.class"));
            Assertions.assertTrue(jsonResponse.containsKey("LowOnPhysicalMemory"));
            Assertions.assertTrue(jsonResponse.containsKey("HeapDump"));
            for (var obj : jsonResponse.getMap().entrySet()) {
                var value = JsonObject.mapFrom(obj.getValue());
                Assertions.assertTrue(value.containsKey("score"));
                MatcherAssert.assertThat(
                        value.getDouble("score"),
                        Matchers.anyOf(
                                Matchers.equalTo(-1d),
                                Matchers.equalTo(-2d),
                                Matchers.equalTo(-3d),
                                Matchers.both(Matchers.lessThanOrEqualTo(100d))
                                        .and(Matchers.greaterThanOrEqualTo(0d))));
                Assertions.assertTrue(value.containsKey("name"));
                MatcherAssert.assertThat(
                        value.getString("name"), Matchers.not(Matchers.emptyOrNullString()));
                Assertions.assertTrue(value.containsKey("topic"));
                MatcherAssert.assertThat(
                        value.getString("topic"), Matchers.not(Matchers.emptyOrNullString()));
                Assertions.assertTrue(value.containsKey("description"));
            }

        } finally {
            file.delete();

            // Clean up recording
            CompletableFuture<JsonObject> deleteResponse = new CompletableFuture<>();
            webClient
                    .delete(String.format("%s/%s", RECORDING_REQ_URL, TEST_RECORDING_NAME))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, deleteResponse)) {
                                    MatcherAssert.assertThat(
                                            ar.result().statusCode(), Matchers.equalTo(200));
                                    deleteResponse.complete(ar.result().bodyAsJsonObject());
                                }
                            });

            try {
                deleteResponse.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new ITestCleanupFailedException(
                        String.format("Failed to delete target recording %s", TEST_RECORDING_NAME),
                        e);
            }
        }
    }

    @Test
    void testGetReportThrowsWithNonExistentRecordingName() throws Exception {
        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        webClient
                .get(REPORT_REQ_URL)
                .putHeader(HttpHeaders.ACCEPT.toString(), HttpMimeType.HTML.mime())
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
}
