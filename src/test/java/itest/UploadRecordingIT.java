/*-
 * #%L
 * Container JFR
 * %%
 * Copyright (C) 2020 Red Hat, Inc.
 * %%
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
 * #L%
 */
package itest;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.redhat.rhjmc.containerjfr.util.HttpStatusCodeIdentifier;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;

public class UploadRecordingIT extends ITestBase {

    static final String TARGET_ID = "localhost";
    static final String RECORDING_NAME = "upload_recording_it_rec";

    @BeforeAll
    public static void createRecording() throws Exception {
        CompletableFuture<JsonObject> dumpRespFuture = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("recordingName", RECORDING_NAME);
        form.add("duration", "5");
        form.add("events", "template=ALL");
        webClient
                .post(String.format("/api/v1/targets/%s/recordings", TARGET_ID))
                .sendForm(
                        form,
                        ar -> {
                            if (ar.failed()) {
                                dumpRespFuture.completeExceptionally(ar.cause());
                                return;
                            }
                            HttpResponse<Buffer> result = ar.result();
                            if (!HttpStatusCodeIdentifier.isSuccessCode(result.statusCode())) {
                                dumpRespFuture.completeExceptionally(
                                        new Exception(result.statusMessage()));
                                return;
                            }
                            dumpRespFuture.complete(ar.result().bodyAsJsonObject());
                        });
        dumpRespFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @AfterAll
    public static void deleteRecording() throws Exception {
        CompletableFuture<Void> deleteRespFuture = new CompletableFuture<>();
        webClient
                .delete(
                        String.format(
                                "/api/v1/targets/%s/recordings/%s", TARGET_ID, RECORDING_NAME))
                .send(
                        ar -> {
                            if (ar.failed()) {
                                deleteRespFuture.completeExceptionally(ar.cause());
                                return;
                            }
                            HttpResponse<Buffer> result = ar.result();
                            if (!HttpStatusCodeIdentifier.isSuccessCode(result.statusCode())) {
                                deleteRespFuture.completeExceptionally(
                                        new Exception(result.statusMessage()));
                                return;
                            }
                            deleteRespFuture.complete(null);
                        });
        deleteRespFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    /* Since no other integration test currently tests upload-recording,
     * GRAFANA_DATASOURCE_URL is just set to an invalid URL, so that we can do this test.
     */
    @Test
    public void shouldHandleBadDatasourceUrl() throws Exception {
        CompletableFuture<Integer> uploadRespFuture = new CompletableFuture<>();
        webClient
                .post(
                        String.format(
                                "/api/v1/targets/%s/recordings/%s/upload",
                                TARGET_ID, RECORDING_NAME))
                .send(
                        ar -> {
                            if (ar.failed()) {
                                uploadRespFuture.completeExceptionally(ar.cause());
                                return;
                            }
                            HttpResponse<Buffer> result = ar.result();
                            uploadRespFuture.complete(result.statusCode());
                        });
        int statusCode = uploadRespFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        MatcherAssert.assertThat(statusCode, Matchers.equalTo(500));
    }
}
