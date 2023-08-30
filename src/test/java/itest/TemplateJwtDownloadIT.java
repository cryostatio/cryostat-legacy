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
import java.util.concurrent.TimeUnit;

import itest.bases.JwtAssetsSelfTest;
import itest.util.Utils;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TemplateJwtDownloadIT extends JwtAssetsSelfTest {

    @Test
    void testDownloadRecordingUsingJwt() throws Exception {
        URL resource = null;
        Path assetDownload = null;
        try {
            resource =
                    new URL(
                            String.format(
                                    "http://%s:%d/api/v2.1/targets/%s/templates/Profiling/type/TARGET",
                                    Utils.WEB_HOST, Utils.WEB_PORT, SELF_REFERENCE_TARGET_ID));
            String downloadUrl = getTokenDownloadUrl(resource);
            assetDownload =
                    downloadFileAbs(downloadUrl, "Profiling", ".jfc")
                            .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Assertions.assertTrue(Files.isReadable(assetDownload));
            Assertions.assertTrue(Files.isRegularFile(assetDownload));
            MatcherAssert.assertThat(assetDownload.toFile().length(), Matchers.greaterThan(0L));
        } finally {
            if (assetDownload != null) {
                Files.deleteIfExists(assetDownload);
            }
        }
    }
}
