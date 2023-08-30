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
package io.cryostat.net.web.http;

import javax.inject.Named;

import io.cryostat.configuration.Variables;
import io.cryostat.core.sys.Environment;
import io.cryostat.net.web.http.api.beta.HttpApiBetaModule;
import io.cryostat.net.web.http.api.v1.HttpApiV1Module;
import io.cryostat.net.web.http.api.v2.HttpApiV2Module;
import io.cryostat.net.web.http.generic.HttpGenericModule;

import dagger.Module;
import dagger.Provides;

@Module(
        includes = {
            HttpGenericModule.class,
            HttpApiBetaModule.class,
            HttpApiV1Module.class,
            HttpApiV2Module.class,
        })
public abstract class HttpModule {

    public static final String HTTP_REQUEST_TIMEOUT_SECONDS = "HTTP_REQUEST_TIMEOUT_SECONDS";

    @Provides
    @Named(HTTP_REQUEST_TIMEOUT_SECONDS)
    static long provideReportGenerationTimeoutSeconds(Environment env) {
        return Long.parseLong(env.getEnv(Variables.HTTP_REQUEST_TIMEOUT, "29"));
    }
}
