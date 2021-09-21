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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import io.cryostat.net.web.http.HttpMimeType;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import itest.bases.StandardSelfTest;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

public class TargetEventsGetIT extends StandardSelfTest {

    static final String EVENT_REQ_URL =
            String.format("/api/v1/targets/%s/events", SELF_REFERENCE_TARGET_ID);
    static final String SEARCH_REQ_URL =
            String.format("/api/v2/targets/%s/events", SELF_REFERENCE_TARGET_ID);

    @Test
    public void testGetTargetEventsReturnsListOfEvents() throws Exception {
        CompletableFuture<JsonArray> getResponse = new CompletableFuture<>();
        webClient
                .get(EVENT_REQ_URL)
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, getResponse)) {
                                MatcherAssert.assertThat(
                                        ar.result().statusCode(), Matchers.equalTo(200));
                                MatcherAssert.assertThat(
                                        ar.result().getHeader(HttpHeaders.CONTENT_TYPE.toString()),
                                        Matchers.equalTo(HttpMimeType.JSON.mime()));
                                getResponse.complete(ar.result().bodyAsJsonArray());
                            }
                        });

        MatcherAssert.assertThat(getResponse.get().size(), Matchers.greaterThan(0));
    }

    @Test
    public void testGetTargetEventsV2WithNoQueryReturnsListOfEvents() throws Exception {
        CompletableFuture<JsonObject> getResponse = new CompletableFuture<>();
        webClient
                .get(SEARCH_REQ_URL)
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

        MatcherAssert.assertThat(getResponse.get().size(), Matchers.greaterThan(0));
        MatcherAssert.assertThat(
                getResponse.get().getJsonObject("data").getJsonArray("result").size(),
                Matchers.greaterThan(0));
    }

    @Test
    public void testGetTargetEventsV2WithQueryReturnsRequestedEvents() throws Exception {
        CompletableFuture<JsonObject> getResponse = new CompletableFuture<>();
        webClient
                .get(String.format("%s?q=WebServerRequest", SEARCH_REQ_URL))
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

        LinkedHashMap<String, Object> expectedResults = new LinkedHashMap<String, Object>();
        expectedResults.put("name", "Web Server Request");
        expectedResults.put("typeId", "io.cryostat.net.web.WebServer.WebServerRequest");
        expectedResults.put("description", null);
        expectedResults.put("category", List.of("Cryostat"));
        expectedResults.put(
                "options",
                Map.of(
                        "enabled",
                        Map.of(
                                "name",
                                "Enabled",
                                "description",
                                "Record event",
                                "defaultValue",
                                "true"),
                        "threshold",
                        Map.of(
                                "name",
                                "Threshold",
                                "description",
                                "Record event with duration above or equal to threshold",
                                "defaultValue",
                                "0ns[ns]"),
                        "stackTrace",
                        Map.of(
                                "name",
                                "Stack Trace",
                                "description",
                                "Record stack traces",
                                "defaultValue",
                                "true")));

        JsonObject expectedResponse =
                new JsonObject(
                        Map.of(
                                "meta", Map.of("type", HttpMimeType.JSON.mime(), "status", "OK"),
                                "data", Map.of("result", List.of(expectedResults))));

        MatcherAssert.assertThat(getResponse.get().size(), Matchers.greaterThan(0));
        MatcherAssert.assertThat(getResponse.get(), Matchers.equalTo(expectedResponse));
    }

    @Test
    public void testGetTargetEventsV2WithQueryReturnsEmptyListWhenNoEventsMatch() throws Exception {
        CompletableFuture<JsonObject> getResponse = new CompletableFuture<>();
        webClient
                .get(String.format("%s?q=thisEventDoesNotExist", SEARCH_REQ_URL))
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

        JsonObject expectedResponse =
                new JsonObject(
                        Map.of(
                                "meta", Map.of("type", HttpMimeType.JSON.mime(), "status", "OK"),
                                "data", Map.of("result", List.of())));

        MatcherAssert.assertThat(getResponse.get(), Matchers.equalTo(expectedResponse));
    }
}
