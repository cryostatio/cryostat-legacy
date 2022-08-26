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

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import io.cryostat.MainModule;
import io.cryostat.core.log.Logger;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;
import itest.bases.ExternalTargetsTest;
import itest.util.ITestCleanupFailedException;
import itest.util.Podman;
import itest.util.http.StoredCredential;
import org.apache.http.client.utils.URLEncodedUtils;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class JvmIdIT extends ExternalTargetsTest {
    private static final Gson gson = MainModule.provideGson(Logger.INSTANCE);

    static final int NUM_EXT_CONTAINERS = 3;
    static final List<String> CONTAINERS = new ArrayList<>();
    static final MultiMap credentialsForm = MultiMap.caseInsensitiveMultiMap();
    ;

    @BeforeAll
    static void setup() throws Exception {
        Set<Podman.ImageSpec> specs = new HashSet<>();
        for (int i = 0; i < NUM_EXT_CONTAINERS; i++) {
            specs.add(
                    new Podman.ImageSpec(
                            "quay.io/andrewazores/vertx-fib-demo:0.6.0",
                            Map.of("JMX_PORT", String.valueOf(9093 + i))));
        }
        for (Podman.ImageSpec spec : specs) {
            CONTAINERS.add(Podman.run(spec));
        }
        CompletableFuture.allOf(
                        CONTAINERS.stream()
                                .map(id -> Podman.waitForContainerState(id, "running"))
                                .collect(Collectors.toList())
                                .toArray(new CompletableFuture[0]))
                .join();
        waitForDiscovery(NUM_EXT_CONTAINERS);

        // store credentials for the targets
        CompletableFuture<Void> credentialsFuture = new CompletableFuture<>();

        credentialsForm.add("matchExpression", "true");
        credentialsForm.add("username", "admin");
        credentialsForm.add("password", "adminpass123");

        webClient
                .post("/api/v2.2/credentials")
                .sendForm(
                        credentialsForm,
                        ar -> {
                            if (assertRequestStatus(ar, credentialsFuture)) {
                                MatcherAssert.assertThat(
                                        ar.result().statusCode(), Matchers.equalTo(201));
                                credentialsFuture.complete(null);
                            }
                        });
        credentialsFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
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
        try {
            CompletableFuture<JsonObject> getCredentialsFuture = new CompletableFuture<>();
            webClient
                    .get("/api/v2.2/credentials")
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, getCredentialsFuture)) {
                                    getCredentialsFuture.complete(ar.result().bodyAsJsonObject());
                                }
                            });

            JsonObject response =
                    getCredentialsFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            List<StoredCredential> actualList =
                    gson.fromJson(
                            response.getJsonObject("data").getValue("result").toString(),
                            new TypeToken<List<StoredCredential>>() {}.getType());

            MatcherAssert.assertThat(actualList, Matchers.hasSize(1));
            StoredCredential storedCredential = actualList.get(0);

            // delete credentials to clean up
            CompletableFuture<JsonObject> deleteCredentialsResponse = new CompletableFuture<>();
            webClient
                    .delete("/api/v2.2/credentials" + "/" + storedCredential.id)
                    .send(
                            ar -> {
                                if (assertRequestStatus(ar, deleteCredentialsResponse)) {
                                    deleteCredentialsResponse.complete(null);
                                }
                            });

            deleteCredentialsResponse.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException | AssertionError e) {
            throw new ITestCleanupFailedException(
                    String.format(
                            "Failed to delete credentials because of exception: %s", e.getCause()));
        }
    }

    @Test
    void testUniqueJvmIds() throws Exception {
        String targetIdOne =
                URLEncodedUtils.formatSegments(
                        String.format(
                                "service:jmx:rmi:///jndi/rmi://%s:9093/jmxrmi", Podman.POD_NAME));
        String targetIdTwo =
                URLEncodedUtils.formatSegments(
                        String.format(
                                "service:jmx:rmi:///jndi/rmi://%s:9094/jmxrmi", Podman.POD_NAME));

        String targetIdThree =
                URLEncodedUtils.formatSegments(
                        String.format(
                                "service:jmx:rmi:///jndi/rmi://%s:9095/jmxrmi", Podman.POD_NAME));

        // send jvmIds requests for all external containers
        String one =
                getJvmIdFuture(targetIdOne, credentialsForm)
                        .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        String two =
                getJvmIdFuture(targetIdTwo, credentialsForm)
                        .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        String three =
                getJvmIdFuture(targetIdThree, credentialsForm)
                        .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        Set<String> targets = Set.of(one, two, three);

        // check that all jvmIds are unique
        MatcherAssert.assertThat(targets, Matchers.hasSize(3));

        for (String id : targets) {
            MatcherAssert.assertThat(id, Matchers.not(Matchers.blankOrNullString()));
        }

        // check that aliased targets point to the same jvmId

        // targetOne
        String targetOneAliasJvmId1 =
                getJvmIdFuture(
                                URLEncodedUtils.formatSegments(
                                        String.format("%s:9093", Podman.POD_NAME)),
                                credentialsForm)
                        .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        String targetOneAliasJvmId2 =
                getJvmIdFuture(
                                URLEncodedUtils.formatSegments(
                                        "service:jmx:rmi:///jndi/rmi://localhost:9093/jmxrmi"),
                                credentialsForm)
                        .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        String targetOneAliasJvmId3 =
                getJvmIdFuture(URLEncodedUtils.formatSegments("localhost:9093"), credentialsForm)
                        .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        MatcherAssert.assertThat(targetOneAliasJvmId1, Matchers.equalTo(one));
        MatcherAssert.assertThat(targetOneAliasJvmId2, Matchers.equalTo(one));
        MatcherAssert.assertThat(targetOneAliasJvmId3, Matchers.equalTo(one));

        // targetTwo
        String targetTwoAliasJvmId1 =
                getJvmIdFuture(
                                URLEncodedUtils.formatSegments(
                                        String.format("%s:9094", Podman.POD_NAME)),
                                credentialsForm)
                        .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        String targetTwoAliasJvmId2 =
                getJvmIdFuture(
                                URLEncodedUtils.formatSegments(
                                        "service:jmx:rmi:///jndi/rmi://localhost:9094/jmxrmi"),
                                credentialsForm)
                        .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        String targetTwoAliasJvmId3 =
                getJvmIdFuture(URLEncodedUtils.formatSegments("localhost:9094"), credentialsForm)
                        .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        MatcherAssert.assertThat(targetTwoAliasJvmId1, Matchers.equalTo(two));
        MatcherAssert.assertThat(targetTwoAliasJvmId2, Matchers.equalTo(two));
        MatcherAssert.assertThat(targetTwoAliasJvmId3, Matchers.equalTo(two));

        // targetThree
        String targetThreeAliasJvmId1 =
                getJvmIdFuture(
                                URLEncodedUtils.formatSegments(
                                        String.format("%s:9095", Podman.POD_NAME)),
                                credentialsForm)
                        .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        String targetThreeAliasJvmId2 =
                getJvmIdFuture(
                                URLEncodedUtils.formatSegments(
                                        "service:jmx:rmi:///jndi/rmi://localhost:9095/jmxrmi"),
                                credentialsForm)
                        .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        String targetThreeAliasJvmId3 =
                getJvmIdFuture(URLEncodedUtils.formatSegments("localhost:9095"), credentialsForm)
                        .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        MatcherAssert.assertThat(targetThreeAliasJvmId1, Matchers.equalTo(three));
        MatcherAssert.assertThat(targetThreeAliasJvmId2, Matchers.equalTo(three));
        MatcherAssert.assertThat(targetThreeAliasJvmId3, Matchers.equalTo(three));
    }

    private CompletableFuture<String> getJvmIdFuture(String encodedTargetId, MultiMap form)
            throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<String> jvmIdFuture = new CompletableFuture<>();

        webClient
                .get(String.format("/api/beta/targets/%s", encodedTargetId))
                .putHeader(
                        "X-JMX-Authorization",
                        "Basic "
                                + Base64.getUrlEncoder()
                                        .encodeToString("admin:adminpass123".getBytes()))
                .sendForm(
                        form,
                        ar -> {
                            if (assertRequestStatus(ar, jvmIdFuture)) {
                                jvmIdFuture.complete(ar.result().bodyAsString());
                            }
                        });
        return jvmIdFuture;
    }
}
