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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import itest.bases.ExternalTargetsTest;
import itest.util.Podman;
import itest.util.http.JvmIdWebRequest;
import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class JvmIdIT extends ExternalTargetsTest {

    static final int NUM_EXT_CONTAINERS = 3;

    @BeforeAll
    static void setup() throws Exception {
        Set<Podman.ImageSpec> specs = new HashSet<>();
        for (int i = 0; i < NUM_EXT_CONTAINERS; i++) {
            Podman.ImageSpec spec =
                    new Podman.ImageSpec(
                            "vertx-fib-demo-" + i,
                            FIB_DEMO_IMAGESPEC,
                            Map.of("JMX_PORT", String.valueOf(9093 + i)));
            specs.add(spec);
            CONTAINERS.add(Podman.runAppWithAgent(10_000 + i, spec));
        }
        waitForDiscovery(NUM_EXT_CONTAINERS);
    }

    @Test
    void testUniqueJvmIds() throws Exception {
        String targetId =
                String.format("service:jmx:rmi:///jndi/rmi://%s:9093/jmxrmi", Podman.POD_NAME);
        Pair<String, String> credentials = Pair.of("admin", "adminpass123");
        // send jvmIds requests for all external containers
        String one = JvmIdWebRequest.jvmIdRequest(targetId);
        String two = JvmIdWebRequest.jvmIdRequest(targetId.replace("9093", "9094"), credentials);
        String three = JvmIdWebRequest.jvmIdRequest(targetId.replace("9093", "9095"), credentials);
        Set<String> targets = Set.of(one, two, three);

        // check that all jvmIds are unique
        MatcherAssert.assertThat(targets, Matchers.hasSize(3));

        for (String id : targets) {
            MatcherAssert.assertThat(id, Matchers.not(Matchers.blankOrNullString()));
        }
    }
}
