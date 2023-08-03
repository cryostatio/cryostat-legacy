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

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import io.cryostat.MainModule;
import io.cryostat.core.log.Logger;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.ServiceRef.AnnotationKey;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.handler.HttpException;
import itest.bases.ExternalTargetsTest;
import itest.util.ITestCleanupFailedException;
import itest.util.Podman;
import itest.util.http.JvmIdWebRequest;
import org.apache.http.client.utils.URIBuilder;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class CredentialsIT extends ExternalTargetsTest {

    private static final Gson gson = MainModule.provideGson(Logger.INSTANCE);
    static final List<String> CONTAINERS = new ArrayList<>();
    static final Map<String, String> NULL_RESULT = new HashMap<>();

    final String jmxServiceUrl =
            String.format("service:jmx:rmi:///jndi/rmi://%s:9093/jmxrmi", Podman.POD_NAME);
    final String jmxServiceUrlEncoded = jmxServiceUrl.replaceAll("/", "%2F");
    final String requestUrl = String.format("/api/v2/targets/%s/credentials", jmxServiceUrlEncoded);

    static {
        NULL_RESULT.put("result", null);
    }

    @AfterAll
    static void cleanup() throws ITestCleanupFailedException {
        for (String id : CONTAINERS) {
            try {
                Podman.kill(id);
            } catch (Exception e) {
                throw new ITestCleanupFailedException(
                        String.format("Failed to kill container instance with ID %s", id), e);
            }
        }
    }

    @Test
    void testDeleteThrowsOnNonExistentCredentials() throws Exception {
        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        webClient
                .delete(requestUrl)
                .send(
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(
                        ExecutionException.class,
                        () -> response.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(404));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Not Found"));
    }

    @Test
    void testAddCredentialsThrowsOnEmptyForm() throws Exception {
        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();

        webClient
                .post(requestUrl)
                .sendForm(
                        form,
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(
                        ExecutionException.class,
                        () -> response.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Bad Request"));
    }

    @Test
    void testAddCredentialsThrowsOnOmittedUsername() throws Exception {
        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("password", "adminpass123");
        webClient
                .post(requestUrl)
                .sendForm(
                        form,
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(
                        ExecutionException.class,
                        () -> response.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Bad Request"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t", "\n"})
    void testAddCredentialsThrowsOnBlankUsername(String username) throws Exception {
        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("username", username);
        form.add("password", "adminpass123");
        webClient
                .post(requestUrl)
                .sendForm(
                        form,
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(
                        ExecutionException.class,
                        () -> response.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Bad Request"));
    }

    @Test
    void testAddCredentialsThrowsOnOmittedPassword() throws Exception {
        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("username", "admin");
        webClient
                .post(requestUrl)
                .sendForm(
                        form,
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(
                        ExecutionException.class,
                        () -> response.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Bad Request"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t", "\n"})
    void testAddCredentialsThrowsOnBlankPassword(String password) throws Exception {
        CompletableFuture<JsonObject> response = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("username", "admin");
        form.add("password", password);
        webClient
                .post(requestUrl)
                .sendForm(
                        form,
                        ar -> {
                            assertRequestStatus(ar, response);
                        });
        ExecutionException ex =
                Assertions.assertThrows(
                        ExecutionException.class,
                        () -> response.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        MatcherAssert.assertThat(
                ((HttpException) ex.getCause()).getStatusCode(), Matchers.equalTo(400));
        MatcherAssert.assertThat(ex.getCause().getMessage(), Matchers.equalTo("Bad Request"));
    }

    @Test
    void testGetTargetCredentialsReturnsTargetList() throws Exception {
        // Get target credentials list should be empty at first
        CompletableFuture<JsonObject> getResponse = new CompletableFuture<>();
        webClient
                .get("/api/v2.1/credentials")
                .send(
                        ar -> {
                            if (ar.succeeded()) {

                                MatcherAssert.assertThat(
                                        ar.result().statusCode(), Matchers.equalTo(200));
                                getResponse.complete(ar.result().bodyAsJsonObject());
                            } else {
                                getResponse.completeExceptionally(ar.cause());
                            }
                        });

        JsonObject expectedEmptyResponse =
                new JsonObject(
                        Map.of(
                                "meta",
                                Map.of("type", HttpMimeType.JSON.mime(), "status", "OK"),
                                "data",
                                Map.of("result", new ArrayList<>())));

        MatcherAssert.assertThat(
                getResponse.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                Matchers.equalTo(expectedEmptyResponse));

        // Post credentials for the new pod
        CompletableFuture<JsonObject> postResponse = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("username", "admin");
        form.add("password", "adminpass123");
        webClient
                .post(String.format("/api/v2/targets/%s/credentials", SELF_REFERENCE_TARGET_ID))
                .sendForm(
                        form,
                        ar -> {
                            if (assertRequestStatus(ar, postResponse)) {
                                postResponse.complete(ar.result().bodyAsJsonObject());
                            }
                        });
        JsonObject expectedResponse =
                new JsonObject(
                        Map.of(
                                "meta",
                                Map.of("type", HttpMimeType.JSON.mime(), "status", "OK"),
                                "data",
                                NULL_RESULT));
        MatcherAssert.assertThat(
                postResponse.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                Matchers.equalTo(expectedResponse));

        Thread.sleep(3_000L);

        // Confirm target credentials list returns target
        CompletableFuture<JsonObject> getResponse2 = new CompletableFuture<>();
        webClient
                .get("/api/v2.1/credentials")
                .send(
                        ar -> {
                            if (ar.succeeded()) {

                                MatcherAssert.assertThat(
                                        ar.result().statusCode(), Matchers.equalTo(200));
                                getResponse2.complete(ar.result().bodyAsJsonObject());
                            } else {
                                getResponse2.completeExceptionally(ar.cause());
                            }
                        });

        List<ServiceRef> expectedList = new ArrayList<ServiceRef>();
        URI expectedURI =
                new URIBuilder("service:jmx:rmi:///jndi/rmi://cryostat-itests:9091/jmxrmi").build();
        String expectedJvmId = JvmIdWebRequest.jvmIdRequest(expectedURI);
        ServiceRef expectedServiceRef =
                new ServiceRef(expectedJvmId, expectedURI, "io.cryostat.Cryostat");
        expectedServiceRef.setCryostatAnnotations(
                Map.of(
                        AnnotationKey.REALM,
                        "JDP",
                        AnnotationKey.HOST,
                        "cryostat-itests",
                        AnnotationKey.PORT,
                        "9091",
                        AnnotationKey.JAVA_MAIN,
                        "io.cryostat.Cryostat"));
        expectedList.add(expectedServiceRef);

        JsonObject response = getResponse2.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        MatcherAssert.assertThat(response.getJsonObject("meta"), Matchers.notNullValue());
        MatcherAssert.assertThat(
                response.getJsonObject("meta").getString("type"),
                Matchers.equalTo("application/json"));
        MatcherAssert.assertThat(
                response.getJsonObject("meta").getString("status"), Matchers.equalTo("OK"));

        MatcherAssert.assertThat(response.getJsonObject("data"), Matchers.notNullValue());
        MatcherAssert.assertThat(
                response.getJsonObject("data").getValue("result"), Matchers.notNullValue());

        List<ServiceRef> actualList =
                gson.fromJson(
                        response.getJsonObject("data").getValue("result").toString(),
                        new TypeToken<List<ServiceRef>>() {}.getType());

        MatcherAssert.assertThat(actualList, Matchers.equalTo(expectedList));

        // Delete credentials to clean up
        CompletableFuture<JsonObject> deleteResponse = new CompletableFuture<>();
        webClient
                .delete(String.format("/api/v2/targets/%s/credentials", SELF_REFERENCE_TARGET_ID))
                .send(
                        ar -> {
                            if (ar.succeeded()) {

                                MatcherAssert.assertThat(
                                        ar.result().statusCode(), Matchers.equalTo(200));
                                deleteResponse.complete(ar.result().bodyAsJsonObject());
                            } else {
                                deleteResponse.completeExceptionally(ar.cause());
                            }
                        });

        JsonObject expectedDeleteResponse =
                new JsonObject(
                        Map.of(
                                "meta",
                                Map.of("type", HttpMimeType.JSON.mime(), "status", "OK"),
                                "data",
                                NULL_RESULT));
        MatcherAssert.assertThat(
                deleteResponse.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                Matchers.equalTo(expectedDeleteResponse));
    }
}
