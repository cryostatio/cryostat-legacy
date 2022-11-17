/*
 * Copyright The Cryostat Authors
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 *
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software (each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.cryostat.net.reports;

import java.util.Set;

import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import io.cryostat.configuration.Variables;
import io.cryostat.core.log.Logger;
import io.cryostat.core.reports.ReportTransformer;
import io.cryostat.core.sys.Environment;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.messaging.notifications.NotificationListener;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.web.http.HttpModule;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.util.JavaProcess;

import com.google.gson.Gson;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;

@Module(
        includes = {
            ReportTransformerModule.class,
        })
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
        return Long.parseLong(env.getEnv(Variables.ACTIVE_REPORTS_CACHE_EXPIRY_ENV, "1800"));
    }

    @Provides
    @Named(ACTIVE_REPORT_CACHE_REFRESH_SECONDS)
    static long provideActiveReportCacheRefreshSeconds(Environment env) {
        return Long.parseLong(env.getEnv(Variables.ACTIVE_REPORTS_CACHE_REFRESH_ENV, "300"));
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
            Gson gson,
            FileSystem fs,
            TargetConnectionManager targetConnectionManager,
            Set<ReportTransformer> reportTransformers,
            Provider<JavaProcess.Builder> javaProcessBuilder,
            @Named(REPORT_GENERATION_TIMEOUT_SECONDS) long generationTimeoutSeconds,
            Logger logger) {
        return new SubprocessReportGenerator(
                env,
                gson,
                fs,
                targetConnectionManager,
                reportTransformers,
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
