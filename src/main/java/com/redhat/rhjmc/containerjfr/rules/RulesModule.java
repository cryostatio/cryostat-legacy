/*-
 * #%L
 * Container JFR
 * %%
 * Copyright (C) 2020 Red Hat, Inc.
 * %%
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
 * #L%
 */
package com.redhat.rhjmc.containerjfr.rules;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

import javax.inject.Named;
import javax.inject.Singleton;

import com.google.gson.Gson;

import com.redhat.rhjmc.containerjfr.configuration.ConfigurationModule;
import com.redhat.rhjmc.containerjfr.configuration.CredentialsManager;
import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.net.HttpServer;
import com.redhat.rhjmc.containerjfr.net.NetworkConfiguration;
import com.redhat.rhjmc.containerjfr.net.web.http.api.v1.TargetRecordingsPostHandler;
import com.redhat.rhjmc.containerjfr.platform.PlatformClient;

import dagger.Module;
import dagger.Provides;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

@Module
public abstract class RulesModule {
    public static final String RULES_SUBDIRECTORY = "rules";

    @Provides
    @Singleton
    static RuleRegistry provideRuleRegistry(
            @Named(ConfigurationModule.CONFIGURATION_PATH) Path confDir,
            FileSystem fs,
            CredentialsManager credentialsManager,
            PlatformClient platformClient,
            NetworkConfiguration netConf,
            Vertx vertx,
            HttpServer server,
            TargetRecordingsPostHandler postHandler,
            Gson gson,
            Logger logger) {
        try {
            Path rulesDir = confDir.resolve(RULES_SUBDIRECTORY);
            if (!fs.isDirectory(rulesDir)) {
                Files.createDirectory(rulesDir);
            }
            return new RuleRegistry(rulesDir, fs, gson, logger);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Provides
    @Singleton
    static RuleProcessor provideRuleProcessor(
            PlatformClient platformClient,
            RuleRegistry registry,
            CredentialsManager credentialsManager,
            NetworkConfiguration netConf,
            Vertx vertx,
            HttpServer server,
            TargetRecordingsPostHandler postHandler,
            PeriodicArchiverFactory periodicArchiverFactory,
            Logger logger) {
        WebClientOptions opts =
                new WebClientOptions()
                        .setSsl(server.isSsl())
                        .setDefaultHost("localhost")
                        .setDefaultPort(netConf.getInternalWebServerPort())
                        .setTrustAll(true)
                        .setVerifyHost(false);
        return new RuleProcessor(
                platformClient,
                registry,
                Executors.newScheduledThreadPool(1),
                credentialsManager,
                WebClient.create(vertx, opts),
                postHandler,
                periodicArchiverFactory,
                logger);
    }

    @Provides
    @Singleton
    static PeriodicArchiverFactory providePeriodicArchivedFactory(
            WebClient webClient, Logger logger) {
        return new PeriodicArchiverFactory(webClient, logger);
    }
}
