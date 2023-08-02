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

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;
import itest.bases.JwtAssetsSelfTest;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RecordingJwtDownloadIT extends JwtAssetsSelfTest {

    static final String TEST_RECORDING_NAME = "RecordingJwtDownloadIT";

    @Test
    void testDownloadRecordingUsingJwt() throws Exception {
        JsonObject resource = null;
        Path assetDownload = null;
        try {
            resource = createRecording();
            String downloadUrl =
                    getTokenDownloadUrl(
                            new URL(
                                    resource.getString("downloadUrl")
                                            .replace("/api/v1/", "/api/v2.1/")));
            Thread.sleep(10_000L);
            assetDownload =
                    downloadFileAbs(downloadUrl, TEST_RECORDING_NAME, ".jfr")
                            .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Assertions.assertTrue(Files.isReadable(assetDownload));
            Assertions.assertTrue(Files.isRegularFile(assetDownload));
            MatcherAssert.assertThat(assetDownload.toFile().length(), Matchers.greaterThan(0L));
        } finally {
            if (resource != null) {
                cleanupCreatedResources(resource.getString("downloadUrl"));
            }
            if (assetDownload != null) {
                Files.deleteIfExists(assetDownload);
            }
        }
    }

    JsonObject createRecording() throws Exception {
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("recordingName", TEST_RECORDING_NAME);
        form.add("duration", "10");
        form.add("events", "template=ALL");
        webClient
                .post(String.format("/api/v1/targets/%s/recordings", "localhost:0"))
                .sendForm(
                        form,
                        ar -> {
                            if (assertRequestStatus(ar, future)) {
                                future.complete(ar.result().bodyAsJsonObject());
                            }
                        });
        return future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}
