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
package io.cryostat.templates;

import javax.inject.Singleton;

import io.cryostat.core.agent.LocalProbeTemplateService;
import io.cryostat.core.sys.Environment;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.core.templates.LocalStorageTemplateService;

import dagger.Module;
import dagger.Provides;

@Module
public abstract class TemplatesModule {

    @Provides
    @Singleton
    static LocalStorageTemplateService provideLocalStorageTemplateService(
            FileSystem fs, Environment env) {
        return new LocalStorageTemplateService(fs, env);
    }

    @Provides
    @Singleton
    static LocalProbeTemplateService provideLocalProbeTemplateService(
            FileSystem fs, Environment env) {
        try {
            return new LocalProbeTemplateService(fs, env);
        } catch (Exception e) {
            // Dagger doesn't like constructors that can throw exceptions, the probeTemplateService
            // throws an exception if the sanity checks fail so we need to deal with it here
            throw new RuntimeException(e);
        }
    }
}
