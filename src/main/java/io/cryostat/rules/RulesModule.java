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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.persistence.EntityManager;
import javax.script.ScriptEngine;

import io.cryostat.configuration.ConfigurationModule;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingMetadataManager;
import io.cryostat.recordings.RecordingOptionsBuilderFactory;
import io.cryostat.recordings.RecordingTargetHelper;

import com.google.gson.Gson;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;

@Module
public abstract class RulesModule {
    public static final String RULES_SUBDIRECTORY = "rules";
    public static final String RULES_WEB_CLIENT = "RULES_WEB_CLIENT";

    @Provides
    @Singleton
    static RuleRegistry provideRuleRegistry(
            @Named(ConfigurationModule.CONFIGURATION_PATH) Path confDir,
            Lazy<MatchExpressionEvaluator> matchExpressionEvaluator,
            FileSystem fs,
            Gson gson,
            Logger logger) {
        try {
            Path rulesDir = confDir.resolve(RULES_SUBDIRECTORY);
            if (!fs.isDirectory(rulesDir)) {
                Files.createDirectory(rulesDir);
            }
            return new RuleRegistry(rulesDir, matchExpressionEvaluator, fs, gson, logger);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Provides
    @Singleton
    static MatchExpressionDao provideMatchExpressionDao(EntityManager em, Logger logger) {
        return new MatchExpressionDao(em, logger);
    }

    @Provides
    @Singleton
    static MatchExpressionManager provideMatchExpressionManager(
            MatchExpressionValidator matchExpressionValidator,
            Lazy<MatchExpressionEvaluator> matchExpressionEvaluator,
            DiscoveryStorage discovery,
            MatchExpressionDao dao,
            Gson gson,
            Logger logger) {
        return new MatchExpressionManager(
                matchExpressionValidator, matchExpressionEvaluator, discovery, dao, gson, logger);
    }

    @Provides
    @Singleton
    static MatchExpressionValidator provideMatchExpressionValidator() {
        return new MatchExpressionValidator();
    }

    @Provides
    @Singleton
    static MatchExpressionEvaluator provideMatchExpressionEvaluator(
            ScriptEngine scriptEngine,
            CredentialsManager credentialsManager,
            RuleRegistry ruleRegistry,
            Logger logger) {
        return new MatchExpressionEvaluator(scriptEngine, credentialsManager, ruleRegistry, logger);
    }

    @Provides
    @Singleton
    static RuleProcessor provideRuleProcessor(
            DiscoveryStorage storage,
            RuleRegistry registry,
            CredentialsManager credentialsManager,
            RecordingOptionsBuilderFactory recordingOptionsBuilderFactory,
            TargetConnectionManager targetConnectionManager,
            RecordingArchiveHelper recordingArchiveHelper,
            RecordingTargetHelper recordingTargetHelper,
            RecordingMetadataManager metadataManager,
            PeriodicArchiverFactory periodicArchiverFactory,
            Logger logger) {
        return new RuleProcessor(
                Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors() * 2),
                storage,
                registry,
                credentialsManager,
                recordingOptionsBuilderFactory,
                targetConnectionManager,
                recordingArchiveHelper,
                recordingTargetHelper,
                metadataManager,
                periodicArchiverFactory,
                logger);
    }

    @Provides
    @Singleton
    static PeriodicArchiverFactory providePeriodicArchivedFactory(Logger logger) {
        return new PeriodicArchiverFactory(logger);
    }
}
