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
import io.vertx.core.json.JsonObject;
import itest.bases.JwtAssetsSelfTest;
import org.apache.http.client.utils.URIBuilder;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ArchivedReportJwtDownloadIT extends JwtAssetsSelfTest {

    static final String TEST_RECORDING_NAME = "ArchivedReportJwtDownloadIT";

    @Test
    void testDownloadRecordingUsingJwt() throws Exception {
        URL resource = null;
        URL archivedResource = null;
        Path assetDownload = null;
        try {
            JsonObject creationResponse = createRecording();
            resource = new URL(creationResponse.getString("downloadUrl"));
            Thread.sleep(10_000L);
            archivedResource = createArchivedRecording(resource);
            URL reportUrl = new URL(creationResponse.getString("reportUrl"));
            String downloadUrl =
                    getTokenDownloadUrl(
                            new URL(reportUrl.toString().replace("/api/v1/", "/api/v2.1/")));
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            headers.add(HttpHeaders.ACCEPT.toString(), HttpMimeType.HTML.mime());
            assetDownload =
                    downloadFileAbs(downloadUrl, TEST_RECORDING_NAME, ".html", headers)
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

    JsonObject createRecording() throws Exception {
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
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
                                future.complete(ar.result().bodyAsJsonObject());
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
