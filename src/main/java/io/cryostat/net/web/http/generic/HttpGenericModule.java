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

import io.cryostat.net.web.http.RequestHandler;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.IntoSet;

@Module
public abstract class HttpGenericModule {

    static final String NON_API_PATH = "^(?!/api/).*";

    @Binds
    @IntoSet
    abstract RequestHandler bindRequestLoggingHandler(RequestLoggingHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindCorsEnablingHandler(CorsEnablingHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindCorsOptionsHandler(CorsOptionsHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindHealthGetHandler(HealthGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindHealthLivenessGetHandler(HealthLivenessGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindStaticAssetsGetHandler(StaticAssetsGetHandler handler);

    @Binds
    @IntoSet
    abstract RequestHandler bindWebClientAssetsGetHandler(WebClientAssetsGetHandler handler);
}
