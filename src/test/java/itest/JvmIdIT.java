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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import itest.bases.ExternalTargetsTest;
import itest.util.ITestCleanupFailedException;
import itest.util.Podman;
import itest.util.http.JvmIdWebRequest;
import org.apache.commons.lang3.tuple.Pair;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class JvmIdIT extends ExternalTargetsTest {

    static final int NUM_EXT_CONTAINERS = 3;
    static final List<String> CONTAINERS = new ArrayList<>();

    @BeforeAll
    static void setup() throws Exception {
        Set<Podman.ImageSpec> specs = new HashSet<>();
        for (int i = 0; i < NUM_EXT_CONTAINERS; i++) {
            specs.add(
                    new Podman.ImageSpec(
                            FIB_DEMO_IMAGESPEC, Map.of("JMX_PORT", String.valueOf(9093 + i))));
        }
        for (Podman.ImageSpec spec : specs) {
            CONTAINERS.add(Podman.run(spec));
        }
        CompletableFuture.allOf(
                        CONTAINERS.stream()
                                .map(id -> Podman.waitForContainerState(id, "running"))
                                .collect(Collectors.toList())
                                .toArray(new CompletableFuture[0]))
                .join();
        waitForDiscovery(NUM_EXT_CONTAINERS);
    }

    @AfterAll
    static void cleanup() throws ITestCleanupFailedException {
        for (String id : CONTAINERS) {
            try {
                Podman.kill(id);
            } catch (Exception e) {
                throw new ITestCleanupFailedException(
                        String.format("Failed to kill container instance with ID %s", id), e);
            }
        }
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
