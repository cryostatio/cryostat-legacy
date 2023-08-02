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

import java.io.File;
import java.util.concurrent.TimeUnit;

import itest.bases.StandardSelfTest;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

@DisabledIfSystemProperty(named = "isHeadlessBuild", matches = "true")
public class ClientAssetsIT extends StandardSelfTest {

    static File file;
    static Document doc;

    @BeforeAll
    static void setup() throws Exception {
        file =
                downloadFile("/index.html", "index", "html")
                        .get(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .toFile();
        doc = Jsoup.parse(file, "UTF-8");
    }

    @Test
    public void indexHtmlShouldReturnClient() {
        MatcherAssert.assertThat(file.length(), Matchers.greaterThan(0L));
    }

    @Test
    public void indexHtmlShouldHaveTitle() {
        Elements head = doc.getElementsByTag("head");
        MatcherAssert.assertThat(head.size(), Matchers.equalTo(1));
        Elements titles = head.first().getElementsByTag("title");
        MatcherAssert.assertThat(titles.size(), Matchers.equalTo(1));
        MatcherAssert.assertThat(titles.first().text(), Matchers.equalTo("Cryostat"));
    }

    @Test
    public void indexHtmlShouldHaveScriptTag() {
        Elements head = doc.getElementsByTag("head");
        MatcherAssert.assertThat("Expected one <head>", head.size(), Matchers.equalTo(1));
        Elements body = doc.getElementsByTag("body");
        MatcherAssert.assertThat("Expected one <body>", body.size(), Matchers.equalTo(1));

        Elements scripts = head.first().getElementsByTag("script");
        MatcherAssert.assertThat(
                "Expected at least one <script>", scripts.size(), Matchers.greaterThanOrEqualTo(1));

        boolean foundAppBundle = false;
        for (Element el : scripts) {
            foundAppBundle |= el.attr("src").matches("^/app(?:.\\w*)?\\.bundle\\.js$");
        }
        Assertions.assertTrue(foundAppBundle, "No app.bundle.js script tag found");
    }
}
