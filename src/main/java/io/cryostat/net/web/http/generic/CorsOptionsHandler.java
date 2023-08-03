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

import java.util.Set;

import javax.inject.Inject;

import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Environment;
import io.cryostat.net.NetworkConfiguration;
import io.cryostat.net.SslConfiguration;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.api.ApiVersion;

import io.vertx.core.http.HttpMethod;

class CorsOptionsHandler extends CorsEnablingHandler {

    @Inject
    CorsOptionsHandler(
            Environment env,
            NetworkConfiguration netConf,
            SslConfiguration sslConf,
            Logger logger) {
        super(env, netConf, sslConf, logger);
    }

    @Override
    public ApiVersion apiVersion() {
        return ApiVersion.GENERIC;
    }

    @Override
    public int getPriority() {
        return DEFAULT_PRIORITY - 10;
    }

    @Override
    public HttpMethod httpMethod() {
        return HttpMethod.OPTIONS;
    }

    @Override
    public Set<ResourceAction> resourceActions() {
        return ResourceAction.NONE;
    }

    @Override
    public String path() {
        return basePath() + "*";
    }
}
