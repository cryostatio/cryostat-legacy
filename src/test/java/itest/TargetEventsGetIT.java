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
