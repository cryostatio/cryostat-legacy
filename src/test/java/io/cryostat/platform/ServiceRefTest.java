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
package io.cryostat.platform;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.cryostat.platform.ServiceRef.AnnotationKey;

class ServiceRefTest {

    static final String URI_STRING = "service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi";
    static final URI EXAMPLE_URI = URI.create(URI_STRING);
    static final String EXAMPLE_ALIAS = "some.app.Alias";

    @Test
    void testConstruct() {
        ServiceRef sr = new ServiceRef(EXAMPLE_URI, EXAMPLE_ALIAS);
        MatcherAssert.assertThat(sr.getServiceUri(), Matchers.equalTo(EXAMPLE_URI));
        MatcherAssert.assertThat(sr.getAlias(), Matchers.equalTo(Optional.of(EXAMPLE_ALIAS)));
    }

    @Test
    void shouldThrowOnNullUri() {
        Assertions.assertThrows(NullPointerException.class, () -> new ServiceRef(null, "alias"));
    }

    @Test
    void shouldAllowEmptyAlias() {
        Assertions.assertTrue(new ServiceRef(EXAMPLE_URI, null).getAlias().isEmpty());
    }

    @Test
    void shouldHaveEmptyLabels() {
        ServiceRef sr = new ServiceRef(EXAMPLE_URI, EXAMPLE_ALIAS);
        MatcherAssert.assertThat(sr.getLabels(), Matchers.equalTo(Map.of()));
    }

    @Test
    void shouldBeAbleToSetEmptyLabels() {
        ServiceRef sr = new ServiceRef(EXAMPLE_URI, EXAMPLE_ALIAS);
        sr.setLabels(Map.of());
        MatcherAssert.assertThat(sr.getLabels(), Matchers.equalTo(Map.of()));
    }

    @Test
    void shouldBeAbleToSetNonEmptyLabels() {
        ServiceRef sr = new ServiceRef(EXAMPLE_URI, EXAMPLE_ALIAS);
        Map<String, String> labels = Map.of("a", "1", "foo", "bar");
        sr.setLabels(labels);
        MatcherAssert.assertThat(sr.getLabels(), Matchers.equalTo(labels));
    }

    @Test
    void shouldBeAbleToReplaceLabels() {
        ServiceRef sr = new ServiceRef(EXAMPLE_URI, EXAMPLE_ALIAS);
        Map<String, String> labels = Map.of("a", "1", "foo", "bar");
        sr.setLabels(labels);
        MatcherAssert.assertThat(sr.getLabels(), Matchers.equalTo(labels));
        sr.setLabels(Map.of());
        MatcherAssert.assertThat(sr.getLabels(), Matchers.equalTo(Map.of()));
    }

    @Test
    void shouldHaveEmptyCryostatAnnotations() {
        ServiceRef sr = new ServiceRef(EXAMPLE_URI, EXAMPLE_ALIAS);
        MatcherAssert.assertThat(sr.getCryostatAnnotations(), Matchers.equalTo(Map.of()));
    }

    @Test
    void shouldBeAbleToSetEmptyCryostatAnnotations() {
        ServiceRef sr = new ServiceRef(EXAMPLE_URI, EXAMPLE_ALIAS);
        sr.setLabels(Map.of());
        MatcherAssert.assertThat(sr.getCryostatAnnotations(), Matchers.equalTo(Map.of()));
    }

    @Test
    void shouldBeAbleToSetNonEmptyCryostatAnnotations() {
        ServiceRef sr = new ServiceRef(EXAMPLE_URI, EXAMPLE_ALIAS);
        Map<AnnotationKey, String> annotations = Map.of(AnnotationKey.HOST, "fooHost",
                AnnotationKey.JAVA_MAIN, "some.App");
        sr.setCryostatAnnotations(annotations);
        MatcherAssert.assertThat(sr.getCryostatAnnotations(), Matchers.equalTo(annotations));
    }

    @Test
    void shouldBeAbleToReplaceCryostatAnnotations() {
        ServiceRef sr = new ServiceRef(EXAMPLE_URI, EXAMPLE_ALIAS);
        Map<AnnotationKey, String> annotations = Map.of(AnnotationKey.HOST, "fooHost",
                AnnotationKey.JAVA_MAIN, "some.App");
        sr.setCryostatAnnotations(annotations);
        MatcherAssert.assertThat(sr.getCryostatAnnotations(), Matchers.equalTo(annotations));
        sr.setCryostatAnnotations(Map.of());
        MatcherAssert.assertThat(sr.getCryostatAnnotations(), Matchers.equalTo(Map.of()));
    }

    @Test
    void shouldHaveEmptyPlatformAnnotations() {
        ServiceRef sr = new ServiceRef(EXAMPLE_URI, EXAMPLE_ALIAS);
        MatcherAssert.assertThat(sr.getPlatformAnnotations(), Matchers.equalTo(Map.of()));
    }

    @Test
    void shouldBeAbleToSetEmptyPlatformAnnotations() {
        ServiceRef sr = new ServiceRef(EXAMPLE_URI, EXAMPLE_ALIAS);
        sr.setPlatformAnnotations(Map.of());
        MatcherAssert.assertThat(sr.getPlatformAnnotations(), Matchers.equalTo(Map.of()));
    }

    @Test
    void shouldBeAbleToSetNonEmptyPlatformAnnotations() {
        ServiceRef sr = new ServiceRef(EXAMPLE_URI, EXAMPLE_ALIAS);
        Map<String, String> annotations = Map.of("a", "1", "foo", "bar");
        sr.setPlatformAnnotations(annotations);
        MatcherAssert.assertThat(sr.getPlatformAnnotations(), Matchers.equalTo(annotations));
    }

    @Test
    void shouldBeAbleToReplacePlatformAnnotations() {
        ServiceRef sr = new ServiceRef(EXAMPLE_URI, EXAMPLE_ALIAS);
        Map<String, String> annotations = Map.of("a", "1", "foo", "bar");
        sr.setPlatformAnnotations(annotations);
        MatcherAssert.assertThat(sr.getPlatformAnnotations(), Matchers.equalTo(annotations));
        sr.setPlatformAnnotations(Map.of());
        MatcherAssert.assertThat(sr.getPlatformAnnotations(), Matchers.equalTo(Map.of()));
    }

}
