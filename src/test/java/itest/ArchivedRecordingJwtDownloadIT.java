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

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.cryostat.net.web.http.HttpMimeType;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import itest.bases.JwtAssetsSelfTest;
import itest.util.Utils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ArchivedRecordingJwtDownloadIT extends JwtAssetsSelfTest {

    static final String TEST_RECORDING_NAME = "ArchivedRecordingJwtDownloadIT";

    @Test
    void testDownloadRecordingUsingJwt() throws Exception {
        URL resource = null;
        URL archivedResource = null;
        Path assetDownload = null;
        try {
            resource = createRecording();
            Thread.sleep(10_000L);
            archivedResource = createArchivedRecording(resource);
            String downloadUrl =
                    getTokenDownloadUrl(
                            new URL(
                                    String.format(
                                            "http://%s:%d/api/beta/recordings/%s/%s",
                                            Utils.WEB_HOST,
                                            Utils.WEB_PORT,
                                            SELF_REFERENCE_TARGET_ID,
                                            StringUtils.substringAfter(
                                                    archivedResource.getPath(), "recordings/"))));
            assetDownload =
                    downloadFileAbs(downloadUrl, TEST_RECORDING_NAME, ".jfr")
                            .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Assertions.assertTrue(Files.isReadable(assetDownload));
            Assertions.assertTrue(Files.isRegularFile(assetDownload));
            MatcherAssert.assertThat(assetDownload.toFile().length(), Matchers.greaterThan(0L));
        } finally {
            if (resource != null) {
                cleanupCreatedResources(resource.getPath());
            }
            if (archivedResource != null) {
                // updated because of v1 RecordingDeleteHandler deprecation
                String updatedArchivedPath =
                        archivedResource
                                .getPath()
                                .replaceFirst("/api/v1/", "/api/beta/")
                                .replaceFirst(
                                        "/recordings/",
                                        String.format("/recordings%s/", SELF_REFERENCE_TARGET_ID));
                cleanupCreatedResources(updatedArchivedPath);
            }
            if (assetDownload != null) {
                Files.deleteIfExists(assetDownload);
            }
        }
    }

    URL createRecording() throws Exception {
        CompletableFuture<URL> future = new CompletableFuture<>();
        MultiMap form = MultiMap.caseInsensitiveMultiMap();
        form.add("recordingName", TEST_RECORDING_NAME);
        form.add("duration", "10");
        form.add("events", "template=ALL");
        webClient
                .post(String.format("/api/v1/targets/%s/recordings", SELF_REFERENCE_TARGET_ID))
                .sendForm(
                        form,
                        ar -> {
                            if (assertRequestStatus(ar, future)) {
                                try {
                                    future.complete(
                                            new URL(
                                                    ar.result()
                                                            .bodyAsJsonObject()
                                                            .getString("downloadUrl")));
                                } catch (MalformedURLException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        });
        return future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    URL createArchivedRecording(URL resource) throws Exception {
        CompletableFuture<URL> future = new CompletableFuture<>();
        webClient
                .patch(resource.getPath())
                .putHeader(HttpHeaders.CONTENT_TYPE.toString(), HttpMimeType.PLAINTEXT.mime())
                .sendBuffer(
                        Buffer.buffer("SAVE"),
                        ar -> {
                            if (assertRequestStatus(ar, future)) {
                                try {
                                    future.complete(
                                            new URIBuilder(resource.toURI())
                                                    .setPath(
                                                            String.format(
                                                                    "/api/v1/recordings/%s",
                                                                    ar.result().bodyAsString()))
                                                    .build()
                                                    .toURL());
                                } catch (MalformedURLException | URISyntaxException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        });
        return future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}
