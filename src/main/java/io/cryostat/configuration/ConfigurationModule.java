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
package io.cryostat.configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.persistence.EntityManager;

import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Environment;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.rules.MatchExpressionEvaluator;
import io.cryostat.rules.MatchExpressionValidator;

import com.google.gson.Gson;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;

@Module
public abstract class ConfigurationModule {
    public static final String CONFIGURATION_PATH = "CONFIGURATION_PATH";
    public static final String CREDENTIALS_SUBDIRECTORY = "credentials";

    @Provides
    @Singleton
    @Named(CONFIGURATION_PATH)
    static Path provideConfigurationPath(Logger logger, Environment env) {
        String path = env.getEnv(Variables.CONFIG_PATH, "/opt/cryostat.d/conf.d");
        logger.info(String.format("Local config path set as %s", path));
        return Paths.get(path);
    }

    @Provides
    @Singleton
    static CredentialsManager provideCredentialsManager(
            @Named(CONFIGURATION_PATH) Path confDir,
            MatchExpressionValidator matchExpressionValidator,
            Lazy<MatchExpressionEvaluator> matchExpressionEvaluator,
            DiscoveryStorage discovery,
            StoredCredentialsDao dao,
            FileSystem fs,
            Gson gson,
            Logger logger) {
        Path credentialsDir = confDir.resolve(CREDENTIALS_SUBDIRECTORY);
        return new CredentialsManager(
                credentialsDir,
                matchExpressionValidator,
                matchExpressionEvaluator,
                discovery,
                dao,
                fs,
                gson,
                logger);
    }

    @Provides
    @Singleton
    static StoredCredentialsDao provideStoredCredentialsDao(EntityManager em, Logger logger) {
        return new StoredCredentialsDao(em, logger);
    }
}
