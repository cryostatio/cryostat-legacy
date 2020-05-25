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

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class RecordingWorkflowIT extends ITestBase {

    static final String TARGET_ID = "localhost";
    static final String TEST_RECORDING_NAME = "workflow_itest";

    @Test
    public void testWorkflow() throws Exception {
        // Check preconditions
        JsonObject listResp =
                sendMessage("list", TARGET_ID).get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertResponseStatus(listResp);
        MatcherAssert.assertThat(
                listResp.getJsonArray("payload"), Matchers.equalTo(new JsonArray(List.of())));

        try {
            // create an in-memory recording
            JsonObject dumpResp =
                    sendMessage("dump", TARGET_ID, TEST_RECORDING_NAME, "5", "template=ALL")
                            .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertResponseStatus(dumpResp);

            // verify in-memory recording created
            listResp =
                    sendMessage("list", TARGET_ID).get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertResponseStatus(listResp);
            JsonArray recordingInfos = listResp.getJsonArray("payload");
            MatcherAssert.assertThat(
                    "list should have size 1 after recording creation",
                    recordingInfos.size(),
                    Matchers.equalTo(1));
            JsonObject recordingInfo = recordingInfos.getJsonObject(0);
            MatcherAssert.assertThat(
                    recordingInfo.getString("name"), Matchers.equalTo(TEST_RECORDING_NAME));
            MatcherAssert.assertThat(recordingInfo.getString("state"), Matchers.equalTo("RUNNING"));

            Thread.sleep(2_000L); // wait some time to save a portion of the recording

            // save a copy of the partial recording dump
            JsonObject saveResp =
                    sendMessage("save", TARGET_ID, TEST_RECORDING_NAME)
                            .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertResponseStatus(saveResp);

            // check that the in-memory recording list hasn't changed
            listResp =
                    sendMessage("list", TARGET_ID).get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertResponseStatus(listResp);
            recordingInfos = listResp.getJsonArray("payload");
            MatcherAssert.assertThat(
                    "list should have size 1 after recording creation",
                    recordingInfos.size(),
                    Matchers.equalTo(1));
            recordingInfo = recordingInfos.getJsonObject(0);
            MatcherAssert.assertThat(
                    recordingInfo.getString("name"), Matchers.equalTo(TEST_RECORDING_NAME));
            MatcherAssert.assertThat(recordingInfo.getString("state"), Matchers.equalTo("RUNNING"));

            // verify saved recording created
            listResp = sendMessage("list-saved").get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertResponseStatus(listResp);
            recordingInfos = listResp.getJsonArray("payload");
            MatcherAssert.assertThat(
                    "list-saved should have size 1 after recording save",
                    recordingInfos.size(),
                    Matchers.equalTo(1));
            recordingInfo = recordingInfos.getJsonObject(0);
            MatcherAssert.assertThat(
                    recordingInfo.getString("name"),
                    Matchers.matchesRegex(
                            TARGET_ID + "_" + TEST_RECORDING_NAME + "_[\\d]{8}T[\\d]{6}Z.jfr"));
            String savedDownloadUrl = recordingInfo.getString("downloadUrl");

            Thread.sleep(3_000L); // wait for the dump to complete

            // verify the in-memory recording list has not changed, except recording is now stopped
            listResp =
                    sendMessage("list", TARGET_ID).get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertResponseStatus(listResp);
            recordingInfos = listResp.getJsonArray("payload");
            MatcherAssert.assertThat(
                    "list should have size 1 after wait period",
                    recordingInfos.size(),
                    Matchers.equalTo(1));
            recordingInfo = recordingInfos.getJsonObject(0);
            MatcherAssert.assertThat(
                    recordingInfo.getString("name"), Matchers.equalTo(TEST_RECORDING_NAME));
            MatcherAssert.assertThat(recordingInfo.getString("state"), Matchers.equalTo("STOPPED"));
            MatcherAssert.assertThat(recordingInfo.getInteger("duration"), Matchers.equalTo(5_000));

            // verify in-memory and saved recordings can be downloaded successfully and yield
            // non-empty recording binaries (TODO: better verification of file content), and that
            // the fully completed in-memory recording is larger than the saved partial copy
            String inMemoryDownloadUrl = recordingInfo.getString("downloadUrl");
            Path inMemoryDownloadPath =
                    downloadFileAbs(inMemoryDownloadUrl, TEST_RECORDING_NAME, ".jfr")
                            .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Path savedDownloadPath =
                    downloadFileAbs(savedDownloadUrl, TEST_RECORDING_NAME + "_saved", ".jfr")
                            .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            MatcherAssert.assertThat(
                    inMemoryDownloadPath.toFile().length(), Matchers.greaterThan(0L));
            MatcherAssert.assertThat(savedDownloadPath.toFile().length(), Matchers.greaterThan(0L));
            MatcherAssert.assertThat(
                    inMemoryDownloadPath.toFile().length(),
                    Matchers.greaterThan(savedDownloadPath.toFile().length()));

            // verify that reports can be downloaded successfully and yield non-empty HTML documents
            // (TODO: verify response body is a valid HTML document)
            String reportUrl = recordingInfo.getString("reportUrl");
            Path reportPath =
                    downloadFileAbs(reportUrl, TEST_RECORDING_NAME + "_report", ".html")
                            .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            MatcherAssert.assertThat(reportPath.toFile().length(), Matchers.greaterThan(0L));
        } finally {
            // Clean up what we created
            sendMessage("delete", TARGET_ID, TEST_RECORDING_NAME)
                    .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            JsonObject savedResp =
                    sendMessage("list-saved").get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            JsonArray savedRecordings = savedResp.getJsonArray("payload");
            savedRecordings.forEach(
                    rec -> {
                        String savedRecordingName = ((JsonObject) rec).getString("name");
                        if (!savedRecordingName.matches(
                                TARGET_ID
                                        + "_"
                                        + TEST_RECORDING_NAME
                                        + "_[\\d]{8}T[\\d]{6}Z.jfr")) {
                            return;
                        }
                        try {
                            sendMessage("delete-saved", savedRecordingName)
                                    .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                        } catch (InterruptedException | ExecutionException | TimeoutException e) {
                            e.printStackTrace();
                        }
                    });
        }
    }
}
