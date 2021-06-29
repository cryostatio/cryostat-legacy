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
import java.util.concurrent.Executors;
import java.util.function.Function;

import javax.inject.Named;
import javax.inject.Singleton;

import io.cryostat.commands.internal.RecordingOptionsBuilderFactory;
import io.cryostat.configuration.ConfigurationModule;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.Credentials;
import io.cryostat.core.sys.Environment;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.net.HttpServer;
import io.cryostat.net.NetworkConfiguration;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.platform.PlatformClient;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingTargetHelper;

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
            RuleMatcher ruleMatcher,
            FileSystem fs,
            Gson gson,
            Logger logger) {
        try {
            Path rulesDir = confDir.resolve(RULES_SUBDIRECTORY);
            if (!fs.isDirectory(rulesDir)) {
                Files.createDirectory(rulesDir);
            }
            return new RuleRegistry(rulesDir, ruleMatcher, fs, gson, logger);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Provides
    @Singleton
    static RuleMatcher provideRuleMatcher(Environment env, Gson gson) {
        return new RuleMatcher(env, gson);
    }

    @Provides
    @Singleton
    static RuleProcessor provideRuleProcessor(
            PlatformClient platformClient,
            RuleRegistry registry,
            CredentialsManager credentialsManager,
            RecordingOptionsBuilderFactory recordingOptionsBuilderFactory,
            TargetConnectionManager targetConnectionManager,
            RecordingArchiveHelper recordingArchiveHelper,
            RecordingTargetHelper recordingTargetHelper,
            PeriodicArchiverFactory periodicArchiverFactory,
            Logger logger) {
        return new RuleProcessor(
                platformClient,
                registry,
                Executors.newScheduledThreadPool(1),
                credentialsManager,
                recordingOptionsBuilderFactory,
                targetConnectionManager,
                recordingArchiveHelper,
                recordingTargetHelper,
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
