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

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import io.cryostat.net.web.http.HttpMimeType;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import itest.bases.StandardSelfTest;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(OrderAnnotation.class)
public class TargetRecordingOptionsIT extends StandardSelfTest {

    static final String OPTIONS_REQ_URL =
            String.format("/api/v1/targets/%s/recordingOptions", SELF_REFERENCE_TARGET_ID);
    static final String OPTIONS_LIST_REQ_URL =
            String.format("/api/v2/targets/%s/recordingOptionsList", SELF_REFERENCE_TARGET_ID);
    static final String RECORDING_NAME = "test_recording";

    @Test
    @Order(1)
    public void testGetTargetRecordingOptionsListReturnsListOfRecordingOptions() throws Exception {
        CompletableFuture<JsonObject> getResponse = new CompletableFuture<>();
        webClient
                .get(OPTIONS_LIST_REQ_URL)
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, getResponse)) {
                                MatcherAssert.assertThat(
                                        ar.result().statusCode(), Matchers.equalTo(200));
                                MatcherAssert.assertThat(
                                        ar.result().getHeader(HttpHeaders.CONTENT_TYPE.toString()),
                                        Matchers.equalTo(HttpMimeType.JSON.mime()));
                                getResponse.complete(ar.result().bodyAsJsonObject());
                            }
                        });

        JsonObject expectedGetResponse =
                new JsonObject(
                        Map.of(
                                "meta",
                                Map.of("type", HttpMimeType.JSON.mime(), "status", "OK"),
                                "data",
                                Map.of(
                                        "result",
                                        List.of(
                                                Map.of(
                                                        "name",
                                                        "Name",
                                                        "description",
                                                        "Recording name",
                                                        "defaultValue",
                                                        "Recording"),
                                                Map.of(
                                                        "name",
                                                        "Duration",
                                                        "description",
                                                        "Duration of recording",
                                                        "defaultValue",
                                                        "30s[s]"),
                                                Map.of(
                                                        "name",
                                                        "Max Size",
                                                        "description",
                                                        "Maximum size of recording",
                                                        "defaultValue",
                                                        "0B[B]"),
                                                Map.of(
                                                        "name",
                                                        "Max Age",
                                                        "description",
                                                        "Maximum age of the events in the recording",
                                                        "defaultValue",
                                                        "0s[s]"),
                                                Map.of(
                                                        "name",
                                                        "To disk",
                                                        "description",
                                                        "Record to disk",
                                                        "defaultValue",
                                                        "false"),
                                                Map.of(
                                                        "name",
                                                        "Dump on Exit",
                                                        "description",
                                                        "Dump recording data to disk on JVM exit",
                                                        "defaultValue",
                                                        "false")))));

        MatcherAssert.assertThat(getResponse.get(), Matchers.equalTo(expectedGetResponse));
    }

    @Test
    @Order(2)
    public void testGetTargetRecordingOptionsReturnsDefaultOptions() throws Exception {
        CompletableFuture<JsonObject> getResponse = new CompletableFuture<>();
        webClient
                .get(OPTIONS_REQ_URL)
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, getResponse)) {
                                MatcherAssert.assertThat(
                                        ar.result().statusCode(), Matchers.equalTo(200));
                                MatcherAssert.assertThat(
                                        ar.result().getHeader(HttpHeaders.CONTENT_TYPE.toString()),
                                        Matchers.equalTo(HttpMimeType.JSON.mime()));
                                getResponse.complete(ar.result().bodyAsJsonObject());
                            }
                        });

        // FIXME the default disk option returned is different than the actual value used in
        // recordings
        // https://github.com/cryostatio/cryostat/issues/263
        JsonObject expectedGetResponse =
                new JsonObject(Map.of("maxAge", 0, "toDisk", false, "maxSize", 0));

        MatcherAssert.assertThat(getResponse.get(), Matchers.equalTo(expectedGetResponse));
    }

    @Test
    @Order(3)
    public void testPostRecordingSetsDiskOptionToTrue() throws Exception {
        try {
            CompletableFuture<JsonObject> postResponse = new CompletableFuture<>();
            CompletableFuture<Integer> maxAge = new CompletableFuture<>();
            CompletableFuture<Boolean> toDisk = new CompletableFuture<>();
            CompletableFuture<Integer> maxSize = new CompletableFuture<>();

            MultiMap form = MultiMap.caseInsensitiveMultiMap();
            form.add("recordingName", RECORDING_NAME);
            form.add("duration", "5");
            form.add("events", "template=ALL");
            webClient
                    .post(String.format("/api/v1/targets/%s/recordings", SELF_REFERENCE_TARGET_ID))
                    .sendForm(
                            form,
                            ar -> {
                                if (assertRequestStatus(ar, postResponse)) {
                                    postResponse.complete(ar.result().bodyAsJsonObject());
                                }
                            });

            maxAge.complete(postResponse.get().getInteger("maxAge"));
            toDisk.complete(postResponse.get().getBoolean("toDisk"));
            maxSize.complete(postResponse.get().getInteger("maxSize"));

            MatcherAssert.assertThat(maxAge.get(), Matchers.equalTo(0));
            MatcherAssert.assertThat(toDisk.get(), Matchers.equalTo(true));
            MatcherAssert.assertThat(maxSize.get(), Matchers.equalTo(0));

        } finally {
            // Delete the recording
            CompletableFuture<Void> deleteRespFuture = new CompletableFuture<>();
            webClient
                    .delete(
                            String.format(
                                    "/api/v1/targets/%s/recordings/%s",
                                    SELF_REFERENCE_TARGET_ID, RECORDING_NAME))
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, deleteRespFuture)) {
                                    deleteRespFuture.complete(null);
                                }
                            });
            deleteRespFuture.get();
        }
    }

    @Test
    @Order(4)
    public void testPatchTargetRecordingOptionsUpdatesOptions() throws Exception {

        CompletableFuture<JsonObject> getResponse = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("maxAge", "60");
        form.add("toDisk", "true");
        form.add("maxSize", "1000");

        webClient
                .patch(OPTIONS_REQ_URL)
                .sendForm(
                        form,
                        ar -> {
                            if (assertRequestStatus(ar, getResponse)) {
                                MatcherAssert.assertThat(
                                        ar.result().statusCode(), Matchers.equalTo(200));
                                MatcherAssert.assertThat(
                                        ar.result().getHeader(HttpHeaders.CONTENT_TYPE.toString()),
                                        Matchers.equalTo(HttpMimeType.JSON.mime()));
                                getResponse.complete(ar.result().bodyAsJsonObject());
                            }
                        });

        JsonObject expectedGetResponse =
                new JsonObject(Map.of("maxAge", 60, "toDisk", true, "maxSize", 1000));

        MatcherAssert.assertThat(getResponse.get(), Matchers.equalTo(expectedGetResponse));
    }

    @Test
    @Order(5)
    public void testGetTargetRecordingOptionsReturnsUpdatedOptions() throws Exception {
        CompletableFuture<JsonObject> getResponse = new CompletableFuture<>();
        webClient
                .get(OPTIONS_REQ_URL)
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, getResponse)) {
                                MatcherAssert.assertThat(
                                        ar.result().statusCode(), Matchers.equalTo(200));
                                MatcherAssert.assertThat(
                                        ar.result().getHeader(HttpHeaders.CONTENT_TYPE.toString()),
                                        Matchers.equalTo(HttpMimeType.JSON.mime()));
                                getResponse.complete(ar.result().bodyAsJsonObject());
                            }
                        });

        JsonObject expectedGetResponse =
                new JsonObject(Map.of("maxAge", 60, "toDisk", true, "maxSize", 1000));

        MatcherAssert.assertThat(getResponse.get(), Matchers.equalTo(expectedGetResponse));
    }
}
