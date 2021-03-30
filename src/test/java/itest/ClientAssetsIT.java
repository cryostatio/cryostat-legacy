/*-
 * #%L
 * Cryostat
 * %%
 * Copyright (C) 2020 - 2021 Cryostat
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

import java.io.File;
import java.util.concurrent.TimeUnit;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ClientAssetsIT extends TestBase {

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
        MatcherAssert.assertThat(titles.first().text(), Matchers.equalTo("ContainerJFR"));
    }

    @Test
    public void indexHtmlShouldHaveScriptTag() {
        Elements body = doc.getElementsByTag("body");
        MatcherAssert.assertThat("Expected one <body>", body.size(), Matchers.equalTo(1));
        Elements script = body.first().getElementsByTag("script");
        MatcherAssert.assertThat(
                "Expected at least one <script>", script.size(), Matchers.greaterThanOrEqualTo(1));

        boolean anyMatch = false;
        for (Element el : script) {
            anyMatch |= el.attr("src").matches("app(?:.\\w*)?\\.bundle\\.js");
        }
        Assertions.assertTrue(anyMatch, "No app.bundle.js script tag found");
    }
}
