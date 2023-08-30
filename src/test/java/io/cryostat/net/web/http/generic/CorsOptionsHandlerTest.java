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
package io.cryostat.net.web.http.generic;

import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Environment;
import io.cryostat.net.NetworkConfiguration;
import io.cryostat.net.SslConfiguration;
import io.cryostat.net.web.http.RequestHandler;

import io.vertx.core.http.HttpMethod;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CorsOptionsHandlerTest {

    CorsOptionsHandler handler;
    @Mock Environment env;
    @Mock NetworkConfiguration netConf;
    @Mock SslConfiguration sslConf;
    @Mock Logger logger;

    @BeforeEach
    void setup() {
        Mockito.when(env.getEnv("CRYOSTAT_CORS_ORIGIN", CorsEnablingHandler.DEV_ORIGIN))
                .thenReturn("http://localhost:9000");
        this.handler = new CorsOptionsHandler(env, netConf, sslConf, logger);
    }

    @Test
    void shouldApplyOPTIONSVerb() {
        MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.OPTIONS));
    }

    @Test
    void shouldApplyToAllRequests() {
        MatcherAssert.assertThat(handler.path(), Matchers.equalTo("/*"));
    }

    @Test
    void shouldBeHighPriority() {
        MatcherAssert.assertThat(
                handler.getPriority(), Matchers.lessThan(RequestHandler.DEFAULT_PRIORITY));
    }
}
