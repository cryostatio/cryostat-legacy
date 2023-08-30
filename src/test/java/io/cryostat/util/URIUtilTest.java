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
package io.cryostat.util;

import java.net.URI;

import javax.management.remote.JMXServiceURL;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

class URIUtilTest {

    @Test
    void testGetRmiTarget() throws Exception {
        String serviceUrl = "service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi";
        URI converted = URIUtil.getRmiTarget(new JMXServiceURL(serviceUrl));
        MatcherAssert.assertThat(converted.getHost(), Matchers.equalTo("cryostat"));
        MatcherAssert.assertThat(converted.getPort(), Matchers.equalTo(9091));
        MatcherAssert.assertThat(converted.getPath(), Matchers.equalTo("/jmxrmi"));
    }
}
