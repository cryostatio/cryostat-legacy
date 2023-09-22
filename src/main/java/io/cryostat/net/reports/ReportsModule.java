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
package io.cryostat.net.reports;

import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import io.cryostat.configuration.Variables;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Environment;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.messaging.notifications.NotificationListener;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.web.http.HttpModule;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.util.JavaProcess;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;

@Module
public abstract class ReportsModule {

    public static final String REPORT_GENERATION_TIMEOUT_SECONDS =
            "REPORT_GENERATION_TIMEOUT_SECONDS";
    public static final String ACTIVE_REPORT_CACHE_EXPIRY_SECONDS =
            "ACTIVE_REPORT_CACHE_EXPIRY_SECONDS";
    public static final String ACTIVE_REPORT_CACHE_REFRESH_SECONDS =
            "ACTIVE_REPORT_CACHE_REFRESH_SECONDS";

    @Provides
    @Named(REPORT_GENERATION_TIMEOUT_SECONDS)
    static long provideReportGenerationTimeoutSeconds(
            @Named(HttpModule.HTTP_REQUEST_TIMEOUT_SECONDS) long httpTimeout) {
        return httpTimeout;
    }

    @Provides
    @Named(ACTIVE_REPORT_CACHE_EXPIRY_SECONDS)
    static long provideActiveReportCacheExpirySeconds(Environment env) {
        return Long.parseLong(env.getEnv(Variables.ACTIVE_REPORTS_CACHE_EXPIRY_ENV, "30"));
    }

    @Provides
    @Named(ACTIVE_REPORT_CACHE_REFRESH_SECONDS)
    static long provideActiveReportCacheRefreshSeconds(Environment env) {
        return Long.parseLong(env.getEnv(Variables.ACTIVE_REPORTS_CACHE_REFRESH_ENV, "10"));
    }

    @Provides
    @Singleton
    static ActiveRecordingReportCache provideActiveRecordingReportCache(
            Provider<ReportGeneratorService> reportGeneratorServiceProvider,
            FileSystem fs,
            TargetConnectionManager targetConnectionManager,
            @Named(REPORT_GENERATION_TIMEOUT_SECONDS) long generationTimeoutSeconds,
            @Named(ACTIVE_REPORT_CACHE_EXPIRY_SECONDS) long cacheExpirySeconds,
            @Named(ACTIVE_REPORT_CACHE_REFRESH_SECONDS) long cacheRefreshSeconds,
            Logger logger) {
        return new ActiveRecordingReportCache(
                reportGeneratorServiceProvider,
                fs,
                targetConnectionManager,
                generationTimeoutSeconds,
                cacheExpirySeconds,
                cacheRefreshSeconds,
                logger);
    }

    @Binds
    @IntoSet
    abstract NotificationListener bindActiveRecordingReportCache(ActiveRecordingReportCache cache);

    @Provides
    @Singleton
    static ArchivedRecordingReportCache provideArchivedRecordingReportCache(
            FileSystem fs,
            Provider<ReportGeneratorService> reportGeneratorServiceProvider,
            RecordingArchiveHelper recordingArchiveHelper,
            @Named(REPORT_GENERATION_TIMEOUT_SECONDS) long generationTimeoutSeconds,
            Logger logger) {
        return new ArchivedRecordingReportCache(
                fs,
                reportGeneratorServiceProvider,
                recordingArchiveHelper,
                generationTimeoutSeconds,
                logger);
    }

    @Provides
    static JavaProcess.Builder provideJavaProcessBuilder() {
        return new JavaProcess.Builder();
    }

    @Provides
    static ReportGeneratorService provideReportGeneratorService(
            Environment env,
            RemoteReportGenerator remoteGenerator,
            SubprocessReportGenerator subprocessGenerator) {
        if (env.hasEnv(Variables.REPORT_GENERATOR_ENV)) {
            return remoteGenerator;
        }
        return subprocessGenerator;
    }

    @Provides
    static RemoteReportGenerator provideRemoteReportGenerator(
            TargetConnectionManager targetConnectionManager,
            FileSystem fs,
            Vertx vertx,
            WebClient http,
            Environment env,
            @Named(REPORT_GENERATION_TIMEOUT_SECONDS) long generationTimeoutSeconds,
            Logger logger) {
        return new RemoteReportGenerator(
                targetConnectionManager, fs, vertx, http, env, generationTimeoutSeconds, logger);
    }

    @Provides
    static SubprocessReportGenerator provideSubprocessReportGenerator(
            Environment env,
            FileSystem fs,
            TargetConnectionManager targetConnectionManager,
            Provider<JavaProcess.Builder> javaProcessBuilder,
            @Named(REPORT_GENERATION_TIMEOUT_SECONDS) long generationTimeoutSeconds,
            Logger logger) {
        return new SubprocessReportGenerator(
                env,
                fs,
                targetConnectionManager,
                javaProcessBuilder,
                generationTimeoutSeconds,
                logger);
    }

    @Provides
    @Singleton
    static ReportService provideReportService(
            ActiveRecordingReportCache activeCache, ArchivedRecordingReportCache archivedCache) {
        return new ReportService(activeCache, archivedCache);
    }
}
