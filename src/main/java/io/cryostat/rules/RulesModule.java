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
package io.cryostat.rules;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.script.ScriptEngine;

import io.cryostat.configuration.ConfigurationModule;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.Credentials;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.discovery.DiscoveryStorage;
import io.cryostat.net.HttpServer;
import io.cryostat.net.NetworkConfiguration;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.net.web.http.HttpModule;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingMetadataManager;
import io.cryostat.recordings.RecordingOptionsBuilderFactory;
import io.cryostat.recordings.RecordingTargetHelper;

import com.github.benmanes.caffeine.cache.Scheduler;
import com.google.gson.Gson;
import dagger.Module;
import dagger.Provides;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.apache.commons.codec.binary.Base64;

@Module
public abstract class RulesModule {
    public static final String RULES_SUBDIRECTORY = "rules";
    public static final String RULES_WEB_CLIENT = "RULES_WEB_CLIENT";
    public static final String RULES_HEADERS_FACTORY = "RULES_HEADERS_FACTORY";

    @Provides
    @Singleton
    static RuleRegistry provideRuleRegistry(
            @Named(ConfigurationModule.CONFIGURATION_PATH) Path confDir,
            MatchExpressionEvaluator matchExpressionEvaluator,
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
    static MatchExpressionValidator provideMatchExpressionValidator() {
        return new MatchExpressionValidator();
    }

    @Provides
    @Singleton
    static MatchExpressionEvaluator provideMatchExpressionEvaluator(
            ScriptEngine scriptEngine,
            @Named(HttpModule.HTTP_REQUEST_TIMEOUT_SECONDS) long cacheTtl) {
        // TODO reuses the report generation/sidecar HTTP request timeout duration as TTL. Determine
        // a better value, maybe make this configurable
        return new MatchExpressionEvaluator(
                scriptEngine,
                ForkJoinPool.commonPool(),
                Scheduler.systemScheduler(),
                Duration.ofSeconds(cacheTtl));
    }

    @Provides
    @Singleton
    static RuleProcessor provideRuleProcessor(
            Vertx vertx,
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
                vertx,
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
    static PeriodicArchiverFactory providePeriodicArchivedFactory(
            @Named(RULES_HEADERS_FACTORY) Function<Credentials, MultiMap> headersFactory,
            Logger logger) {
        return new PeriodicArchiverFactory(logger);
    }

    @Provides
    @Singleton
    @Named(RULES_WEB_CLIENT)
    static WebClient provideRulesWebClient(
            Vertx vertx, HttpServer server, NetworkConfiguration netConf) {
        WebClientOptions opts =
                new WebClientOptions()
                        .setSsl(server.isSsl())
                        .setDefaultHost("localhost")
                        .setDefaultPort(netConf.getInternalWebServerPort())
                        .setTrustAll(true)
                        .setVerifyHost(false);
        return WebClient.create(vertx, opts);
    }

    @Provides
    @Named(RULES_HEADERS_FACTORY)
    static Function<Credentials, MultiMap> provideRulesHeadersFactory() {
        return credentials -> {
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            if (credentials != null) {
                headers.add(
                        AbstractAuthenticatedRequestHandler.JMX_AUTHORIZATION_HEADER,
                        String.format(
                                "Basic %s",
                                Base64.encodeBase64String(
                                        String.format(
                                                        "%s:%s",
                                                        credentials.getUsername(),
                                                        credentials.getPassword())
                                                .getBytes(StandardCharsets.UTF_8))));
            }
            return headers;
        };
    }
}
