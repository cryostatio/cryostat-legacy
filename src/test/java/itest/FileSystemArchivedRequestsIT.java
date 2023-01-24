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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.cryostat.MainModule;
import io.cryostat.core.log.Logger;
import io.cryostat.net.web.http.HttpMimeType;

import com.google.gson.Gson;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import itest.bases.JwtAssetsSelfTest;
import org.apache.http.client.utils.URIBuilder;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("FIXME broken for now, need to investigate deeper")
public class FileSystemArchivedRequestsIT extends JwtAssetsSelfTest {
    private static final Gson gson = MainModule.provideGson(Logger.INSTANCE);

    static final String TEST_RECORDING_NAME = "FileSystemArchivedRequestsIT";

    @Test
    void testGetRecordingsAndDirectories() throws Exception {
        URL resource = null;
        URL archivedResource = null;
        Path assetDownload = null;
        String subdirectoryName = null;
        try {
            JsonObject creationResponse = createRecording();
            resource = new URL(creationResponse.getString("downloadUrl"));
            Thread.sleep(10_000L);

            // create archivedRecording
            archivedResource = createArchivedRecording(resource);

            // get recordings and directories fromPath
            JsonArray archivedDirectories = getRecordingsAndDirectories();

            MatcherAssert.assertThat(archivedDirectories.size(), Matchers.equalTo(1));

            JsonObject dir = archivedDirectories.getJsonObject(0);
            JsonArray dirRecordings = dir.getJsonArray("recordings");

            MatcherAssert.assertThat(
                    dir.getString("connectUrl"), Matchers.equalTo(SELF_REFERENCE_TARGET_ID_RAW));
            MatcherAssert.assertThat(dir.getString("jvmId"), Matchers.notNullValue());
            MatcherAssert.assertThat(dirRecordings, Matchers.notNullValue());
            MatcherAssert.assertThat(dirRecordings.size(), Matchers.equalTo(1));

            JsonObject archivedRecording = dirRecordings.getJsonObject(0);
            JsonObject metadata = archivedRecording.getJsonObject("metadata");
            JsonObject labels = metadata.getJsonObject("labels");

            JsonObject expectedLabels = new JsonObject();
            expectedLabels.put("template.name", "ALL").put("template.type", "TARGET");

            MatcherAssert.assertThat(
                    archivedRecording.getString("name"),
                    Matchers.startsWith("io-cryostat-Cryostat_" + TEST_RECORDING_NAME));
            MatcherAssert.assertThat(labels, Matchers.equalTo(expectedLabels));

            // post metadata fromPath
            subdirectoryName = dir.getString("jvmId");
            String recordingName = archivedRecording.getString("name");
            Map<String, String> uploadMetadata = Map.of("label", "test");
            CompletableFuture<JsonObject> metadataFuture = new CompletableFuture<>();
            webClient
                    .post(
                            String.format(
                                    "/api/beta/fs/recordings/%s/%s/metadata/labels",
                                    subdirectoryName, recordingName))
                    .sendBuffer(
                            Buffer.buffer(gson.toJson(uploadMetadata, Map.class)),
                            ar -> {
                                if (assertRequestStatus(ar, metadataFuture)) {
                                    MatcherAssert.assertThat(
                                            ar.result().statusCode(), Matchers.equalTo(200));
                                    metadataFuture.complete(ar.result().bodyAsJsonObject());
                                }
                            });
            metadataFuture.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            // verify directory object is same but with metadata added
            JsonArray updatedArchivedDirectories = getRecordingsAndDirectories();

            MatcherAssert.assertThat(updatedArchivedDirectories.size(), Matchers.equalTo(1));

            JsonObject updatedDir = updatedArchivedDirectories.getJsonObject(0);
            JsonArray updatedDirRecordings = updatedDir.getJsonArray("recordings");

            MatcherAssert.assertThat(
                    updatedDir.getString("connectUrl"),
                    Matchers.equalTo(dir.getString("connectUrl")));
            MatcherAssert.assertThat(
                    updatedDir.getString("jvmId"), Matchers.equalTo(dir.getString("jvmId")));
            MatcherAssert.assertThat(updatedDirRecordings, Matchers.notNullValue());
            MatcherAssert.assertThat(updatedDirRecordings.size(), Matchers.equalTo(1));

            JsonObject updatedArchivedRecording = updatedDirRecordings.getJsonObject(0);
            MatcherAssert.assertThat(
                    updatedArchivedRecording.getString("name"),
                    Matchers.equalTo(archivedRecording.getString("name")));

            JsonObject updatedMetadata = updatedArchivedRecording.getJsonObject("metadata");
            JsonObject updatedLabels = updatedMetadata.getJsonObject("labels");
            JsonObject expectedUpdatedLabels = new JsonObject().put("label", "test");

            MatcherAssert.assertThat(updatedLabels, Matchers.equalTo(expectedUpdatedLabels));

            // throw and catch error from non-existent report fromPath
            URL badReportUrl =
                    new URL(
                            updatedArchivedRecording
                                    .getString("reportUrl")
                                    .replace("cryostat", "deadbeef"));
            Assertions.assertThrows(
                    ExecutionException.class,
                    () ->
                            downloadFileAbs(
                                            getTokenDownloadUrl(badReportUrl),
                                            TEST_RECORDING_NAME,
                                            ".html")
                                    .get());

            // get recording report fromPath
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            headers.add(HttpHeaders.ACCEPT, HttpMimeType.HTML.mime());
            URL reportUrl = new URL(updatedArchivedRecording.getString("reportUrl"));
            String downloadUrl = getTokenDownloadUrl(new URL(reportUrl.toString()));
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
                // delete recording fromPath
                String updatedArchivedPath =
                        archivedResource
                                .getPath()
                                .replaceFirst(
                                        "/api/v1/recordings",
                                        String.format(
                                                "/api/beta/fs/recordings%s", subdirectoryName));
                cleanupCreatedResources(updatedArchivedPath);
            }
            if (assetDownload != null) {
                Files.deleteIfExists(assetDownload);
            }
        }
    }

    JsonArray getRecordingsAndDirectories()
            throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<JsonArray> future = new CompletableFuture<>();
        webClient
                .get("/api/beta/fs/recordings")
                .send(
                        ar -> {
                            if (assertRequestStatus(ar, future)) {
                                future.complete(ar.result().bodyAsJsonArray());
                            }
                        });
        return future.get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
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
