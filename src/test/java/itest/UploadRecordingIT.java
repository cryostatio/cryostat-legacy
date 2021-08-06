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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.cryostat.net.web.http.HttpMimeType;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import itest.bases.StandardSelfTest;
import itest.util.ITestCleanupFailedException;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class UploadRecordingIT extends StandardSelfTest {

    static final String RECORDING_NAME = "upload_recording_it_rec";

    @BeforeAll
    public static void createRecording() throws Exception {
        CompletableFuture<JsonObject> dumpPostResponse = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("recordingName", RECORDING_NAME);
        form.add("duration", "5");
        form.add("events", "template=ALL");
        webClient
                .post(String.format("/api/v1/targets/%s/recordings", SELF_REFERENCE_TARGET_ID))
                .sendForm(
                        form,
                        ar -> {
                            if (assertRequestStatus(ar, dumpPostResponse)) {
                                MatcherAssert.assertThat(
                                        ar.result().statusCode(), Matchers.equalTo(201));
                                dumpPostResponse.complete(ar.result().bodyAsJsonObject());
                            }
                        });
        dumpPostResponse.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @AfterAll
    public static void deleteRecording() throws Exception {
        CompletableFuture<Void> deleteRespFuture = new CompletableFuture<>();
        webClient
                .delete(
                        String.format(
                                "/api/v1/targets/%s/recordings/%s",
                                SELF_REFERENCE_TARGET_ID, RECORDING_NAME))
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, deleteRespFuture)) {
                                MatcherAssert.assertThat(
                                        ar.result().statusCode(), Matchers.equalTo(200));
                                deleteRespFuture.complete(null);
                            }
                        });
        try {
            deleteRespFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            throw new ITestCleanupFailedException(
                    String.format("Failed to delete target recording %s", RECORDING_NAME), e);
        }
    }

    @Test
    public void shouldUploadRecordingToGrafana() throws Exception {
        CompletableFuture<String> uploadRespFuture = new CompletableFuture<>();
        webClient
                .post(
                        String.format(
                                "/api/v1/targets/%s/recordings/%s/upload",
                                SELF_REFERENCE_TARGET_ID, RECORDING_NAME))
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, uploadRespFuture)) {
                                MatcherAssert.assertThat(
                                        ar.result().statusCode(), Matchers.equalTo(200));
                            }
                            uploadRespFuture.complete(ar.result().bodyAsString());
                        });

        final String expectedUploadResponse =
                String.format("Uploaded: %s\nSet: %s", RECORDING_NAME, RECORDING_NAME);

        MatcherAssert.assertThat(
                uploadRespFuture.get().trim(), Matchers.equalTo(expectedUploadResponse));

        // Confirm recording appears in Grafana
        CompletableFuture<String> getRespFuture = new CompletableFuture<>();
        webClient
                .get(8080, "localhost", "/list")
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, getRespFuture)) {
                                MatcherAssert.assertThat(
                                        ar.result().statusCode(), Matchers.equalTo(200));
                                MatcherAssert.assertThat(
                                        ar.result().getHeader(HttpHeaders.CONTENT_TYPE.toString()),
                                        Matchers.equalTo(HttpMimeType.JSON.mime()));
                                getRespFuture.complete(ar.result().bodyAsString());
                            }
                        });

        MatcherAssert.assertThat(
                getRespFuture.get().trim(), Matchers.equalTo(String.format("%s", RECORDING_NAME)));

        // Query Grafana for recording metrics
        CompletableFuture<JsonArray> queryRespFuture = new CompletableFuture<>();

        Calendar lastFiveSecs = Calendar.getInstance();
        lastFiveSecs.add(Calendar.SECOND, -5);
        final DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        final String FROM = dateFormat.format(lastFiveSecs.getTime());
        final String TO = dateFormat.format(Calendar.getInstance().getTime());
        final String TARGET_METRIC = "jdk.CPULoad.machineTotal";
        final int PANEL_ID = 4; // from inspecting dashboard html

        JsonObject query =
                new JsonObject(
                        Map.ofEntries(
                                Map.entry("panelId", PANEL_ID),
                                Map.entry(
                                        "range",
                                        Map.of(
                                                "from",
                                                FROM,
                                                "to",
                                                TO,
                                                "raw",
                                                Map.of("from", FROM, "to", TO))),
                                Map.entry("interval", "10ms"),
                                Map.entry("intervalMs", "10"),
                                Map.entry(
                                        "targets",
                                        List.of(
                                                Map.of(
                                                        "target",
                                                        TARGET_METRIC,
                                                        "refId",
                                                        "A",
                                                        "type",
                                                        "timeserie"))),
                                Map.entry("format", "json"),
                                Map.entry("maxDataPoints", 1000)));

        webClient
                .post(8080, "localhost", "/query")
                .sendJsonObject(
                        query,
                        ar -> {
                            if (assertRequestStatus(ar, queryRespFuture)) {
                                MatcherAssert.assertThat(
                                        ar.result().statusCode(), Matchers.equalTo(200));
                                MatcherAssert.assertThat(
                                        ar.result().getHeader(HttpHeaders.CONTENT_TYPE.toString()),
                                        Matchers.equalTo(HttpMimeType.JSON.mime()));
                                queryRespFuture.complete(ar.result().bodyAsJsonArray());
                            }
                        });
        // FIXME the datapoints array shouldn't be empty
        JsonArray expectedQueryResponse = new JsonArray();
        expectedQueryResponse.add(Map.of("target", TARGET_METRIC, "datapoints", List.of()));

        MatcherAssert.assertThat(queryRespFuture.get(), Matchers.equalTo(expectedQueryResponse));
    }
}
