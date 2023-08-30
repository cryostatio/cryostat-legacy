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
package io.cryostat.platform;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

import io.cryostat.platform.ServiceRef.AnnotationKey;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ServiceRefTest {

    static final String EXAMPLE_JVMID = "asdf1234";
    static final String URI_STRING = "service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi";
    static final URI EXAMPLE_URI = URI.create(URI_STRING);
    static final String EXAMPLE_ALIAS = "some.app.Alias";

    @Test
    void testConstruct() {
        ServiceRef sr = new ServiceRef(EXAMPLE_JVMID, EXAMPLE_URI, EXAMPLE_ALIAS);
        MatcherAssert.assertThat(sr.getJvmId(), Matchers.equalTo(EXAMPLE_JVMID));
        MatcherAssert.assertThat(sr.getServiceUri(), Matchers.equalTo(EXAMPLE_URI));
        MatcherAssert.assertThat(sr.getAlias(), Matchers.equalTo(Optional.of(EXAMPLE_ALIAS)));
    }

    @Test
    void shouldThrowOnNullUri() {
        Assertions.assertThrows(
                NullPointerException.class,
                () -> new ServiceRef(EXAMPLE_JVMID, null, EXAMPLE_ALIAS));
    }

    @Test
    void shouldAllowEmptyAlias() {
        Assertions.assertTrue(
                new ServiceRef(EXAMPLE_JVMID, EXAMPLE_URI, null).getAlias().isEmpty());
    }

    @Test
    void shouldHaveEmptyLabels() {
        ServiceRef sr = new ServiceRef(EXAMPLE_JVMID, EXAMPLE_URI, EXAMPLE_ALIAS);
        MatcherAssert.assertThat(sr.getLabels(), Matchers.equalTo(Map.of()));
    }

    @Test
    void shouldBeAbleToSetEmptyLabels() {
        ServiceRef sr = new ServiceRef(EXAMPLE_JVMID, EXAMPLE_URI, EXAMPLE_ALIAS);
        sr.setLabels(Map.of());
        MatcherAssert.assertThat(sr.getLabels(), Matchers.equalTo(Map.of()));
    }

    @Test
    void shouldBeAbleToSetNonEmptyLabels() {
        ServiceRef sr = new ServiceRef(EXAMPLE_JVMID, EXAMPLE_URI, EXAMPLE_ALIAS);
        Map<String, String> labels = Map.of("a", "1", "foo", "bar");
        sr.setLabels(labels);
        MatcherAssert.assertThat(sr.getLabels(), Matchers.equalTo(labels));
    }

    @Test
    void shouldBeAbleToReplaceLabels() {
        ServiceRef sr = new ServiceRef(EXAMPLE_JVMID, EXAMPLE_URI, EXAMPLE_ALIAS);
        Map<String, String> labels = Map.of("a", "1", "foo", "bar");
        sr.setLabels(labels);
        MatcherAssert.assertThat(sr.getLabels(), Matchers.equalTo(labels));
        sr.setLabels(Map.of());
        MatcherAssert.assertThat(sr.getLabels(), Matchers.equalTo(Map.of()));
    }

    @Test
    void shouldHaveEmptyCryostatAnnotations() {
        ServiceRef sr = new ServiceRef(EXAMPLE_JVMID, EXAMPLE_URI, EXAMPLE_ALIAS);
        MatcherAssert.assertThat(sr.getCryostatAnnotations(), Matchers.equalTo(Map.of()));
    }

    @Test
    void shouldBeAbleToSetEmptyCryostatAnnotations() {
        ServiceRef sr = new ServiceRef(EXAMPLE_JVMID, EXAMPLE_URI, EXAMPLE_ALIAS);
        sr.setLabels(Map.of());
        MatcherAssert.assertThat(sr.getCryostatAnnotations(), Matchers.equalTo(Map.of()));
    }

    @Test
    void shouldBeAbleToSetNonEmptyCryostatAnnotations() {
        ServiceRef sr = new ServiceRef(EXAMPLE_JVMID, EXAMPLE_URI, EXAMPLE_ALIAS);
        Map<AnnotationKey, String> annotations =
                Map.of(AnnotationKey.HOST, "fooHost", AnnotationKey.JAVA_MAIN, "some.App");
        sr.setCryostatAnnotations(annotations);
        MatcherAssert.assertThat(sr.getCryostatAnnotations(), Matchers.equalTo(annotations));
    }

    @Test
    void shouldBeAbleToReplaceCryostatAnnotations() {
        ServiceRef sr = new ServiceRef(EXAMPLE_JVMID, EXAMPLE_URI, EXAMPLE_ALIAS);
        Map<AnnotationKey, String> annotations =
                Map.of(AnnotationKey.HOST, "fooHost", AnnotationKey.JAVA_MAIN, "some.App");
        sr.setCryostatAnnotations(annotations);
        MatcherAssert.assertThat(sr.getCryostatAnnotations(), Matchers.equalTo(annotations));
        sr.setCryostatAnnotations(Map.of());
        MatcherAssert.assertThat(sr.getCryostatAnnotations(), Matchers.equalTo(Map.of()));
    }

    @Test
    void shouldHaveEmptyPlatformAnnotations() {
        ServiceRef sr = new ServiceRef(EXAMPLE_JVMID, EXAMPLE_URI, EXAMPLE_ALIAS);
        MatcherAssert.assertThat(sr.getPlatformAnnotations(), Matchers.equalTo(Map.of()));
    }

    @Test
    void shouldBeAbleToSetEmptyPlatformAnnotations() {
        ServiceRef sr = new ServiceRef(EXAMPLE_JVMID, EXAMPLE_URI, EXAMPLE_ALIAS);
        sr.setPlatformAnnotations(Map.of());
        MatcherAssert.assertThat(sr.getPlatformAnnotations(), Matchers.equalTo(Map.of()));
    }

    @Test
    void shouldBeAbleToSetNonEmptyPlatformAnnotations() {
        ServiceRef sr = new ServiceRef(EXAMPLE_JVMID, EXAMPLE_URI, EXAMPLE_ALIAS);
        Map<String, String> annotations = Map.of("a", "1", "foo", "bar");
        sr.setPlatformAnnotations(annotations);
        MatcherAssert.assertThat(sr.getPlatformAnnotations(), Matchers.equalTo(annotations));
    }

    @Test
    void shouldBeAbleToReplacePlatformAnnotations() {
        ServiceRef sr = new ServiceRef(EXAMPLE_JVMID, EXAMPLE_URI, EXAMPLE_ALIAS);
        Map<String, String> annotations = Map.of("a", "1", "foo", "bar");
        sr.setPlatformAnnotations(annotations);
        MatcherAssert.assertThat(sr.getPlatformAnnotations(), Matchers.equalTo(annotations));
        sr.setPlatformAnnotations(Map.of());
        MatcherAssert.assertThat(sr.getPlatformAnnotations(), Matchers.equalTo(Map.of()));
    }
}
