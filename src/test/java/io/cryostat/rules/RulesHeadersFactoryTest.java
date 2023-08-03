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
package io.cryostat.rules;

import java.util.Map;
import java.util.function.Function;

import io.cryostat.core.net.Credentials;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;

import io.vertx.core.MultiMap;
import org.apache.commons.codec.binary.Base64;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

class RulesHeadersFactoryTest {

    Function<Credentials, MultiMap> factory = RulesModule.provideRulesHeadersFactory();

    @Test
    void testNoHeaderAddedIfCredentialsAreNull() {
        MultiMap result = factory.apply(null);
        MatcherAssert.assertThat(result, Matchers.emptyIterable());
    }

    @Test
    void ensureCorrectHeaderWhenCredentialsProvided() {
        Credentials credentials = new Credentials("foouser", "barpassword");
        MultiMap result = factory.apply(credentials);
        MatcherAssert.assertThat(result.size(), Matchers.is(1));
        Map.Entry<String, String> header = result.entries().get(0);
        MatcherAssert.assertThat(
                header.getKey(),
                Matchers.equalTo(AbstractAuthenticatedRequestHandler.JMX_AUTHORIZATION_HEADER));
        MatcherAssert.assertThat(
                header.getValue(),
                Matchers.equalTo(
                        "Basic "
                                + Base64.encodeBase64String(
                                        (credentials.getUsername()
                                                        + ":"
                                                        + credentials.getPassword())
                                                .getBytes())));
    }
}
