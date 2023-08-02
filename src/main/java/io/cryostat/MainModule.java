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
package io.cryostat;

import java.lang.management.MemoryUsage;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import javax.inject.Named;
import javax.inject.Singleton;
import javax.management.remote.JMXServiceURL;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import io.cryostat.configuration.ConfigurationModule;
import io.cryostat.configuration.Variables;
import io.cryostat.core.agent.ProbeTemplate;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Environment;
import io.cryostat.core.tui.ClientWriter;
import io.cryostat.discovery.DiscoveryModule;
import io.cryostat.messaging.MessagingModule;
import io.cryostat.net.NetworkModule;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.platform.PlatformModule;
import io.cryostat.recordings.RecordingsModule;
import io.cryostat.rules.Rule;
import io.cryostat.rules.RulesModule;
import io.cryostat.storage.StorageModule;
import io.cryostat.sys.SystemModule;
import io.cryostat.templates.TemplatesModule;
import io.cryostat.util.GsonJmxServiceUrlAdapter;
import io.cryostat.util.HttpMimeTypeAdapter;
import io.cryostat.util.MemoryUsageTypeAdapter;
import io.cryostat.util.PathTypeAdapter;
import io.cryostat.util.PluggableJsonDeserializer;
import io.cryostat.util.PluggableTypeAdapter;
import io.cryostat.util.ProbeTemplateTypeAdapter;
import io.cryostat.util.RuleDeserializer;
import io.cryostat.util.resource.ResourceModule;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dagger.Module;
import dagger.Provides;
import io.vertx.core.Vertx;
import org.apache.commons.codec.binary.Base32;

@Module(
        includes = {
            StorageModule.class,
            ConfigurationModule.class,
            MessagingModule.class,
            NetworkModule.class,
            DiscoveryModule.class,
            PlatformModule.class,
            RecordingsModule.class,
            ResourceModule.class,
            RulesModule.class,
            SystemModule.class,
            TemplatesModule.class,
        })
public abstract class MainModule {
    public static final String RECORDINGS_PATH = "RECORDINGS_PATH";
    public static final String CONF_DIR = "CONF_DIR";
    public static final String UUID_FROM_STRING = "UUID_FROM_STRING";

    @Provides
    @Singleton
    static ApplicationVersion provideApplicationVersion(Logger logger) {
        return new ApplicationVersion(logger);
    }

    @Provides
    @Singleton
    static Logger provideLogger() {
        return Logger.INSTANCE;
    }

    @Provides
    static Base32 provideBase32() {
        return new Base32();
    }

    @Provides
    @Singleton
    // FIXME remove this outdated ClientWriter abstraction and simply replace with an injected
    // Logger at all ClientWriter injection sites
    static ClientWriter provideClientWriter(Logger logger) {
        return new ClientWriter() {
            @Override
            public void print(String s) {
                logger.info(s);
            }

            @Override
            public void println(Exception e) {
                logger.warn(e);
            }
        };
    }

    // testing-only when extra adapters aren't needed
    public static Gson provideGson(Logger logger) {
        return provideGson(Set.of(), Set.of(), logger);
    }

    // public since this is useful to use directly in tests
    @Provides
    @Singleton
    public static Gson provideGson(
            Set<PluggableTypeAdapter<?>> extraAdapters,
            Set<PluggableJsonDeserializer<?>> deserializers,
            Logger logger) {
        GsonBuilder builder =
                new GsonBuilder()
                        .serializeNulls()
                        .disableHtmlEscaping()
                        .registerTypeAdapter(
                                JMXServiceURL.class, new GsonJmxServiceUrlAdapter(logger))
                        .registerTypeAdapter(HttpMimeType.class, new HttpMimeTypeAdapter())
                        .registerTypeHierarchyAdapter(Path.class, new PathTypeAdapter())
                        .registerTypeAdapter(Rule.class, new RuleDeserializer())
                        .registerTypeAdapter(ProbeTemplate.class, new ProbeTemplateTypeAdapter())
                        .registerTypeAdapter(MemoryUsage.class, new MemoryUsageTypeAdapter());
        for (PluggableTypeAdapter<?> pta : extraAdapters) {
            builder = builder.registerTypeAdapter(pta.getAdaptedType(), pta);
        }
        for (PluggableJsonDeserializer<?> pjd : deserializers) {
            builder = builder.registerTypeAdapter(pjd.getAdaptedType(), pjd);
        }
        return builder.create();
    }

    @Provides
    @Singleton
    @Named(RECORDINGS_PATH)
    static Path provideSavedRecordingsPath(Logger logger, Environment env) {
        String archivePath = env.getEnv(Variables.ARCHIVE_PATH, "/flightrecordings");
        logger.info("Local save path for flight recordings set as {}", archivePath);
        return Paths.get(archivePath);
    }

    @Provides
    @Singleton
    public static ScriptEngine provideScriptEngine() {
        return new ScriptEngineManager().getEngineByName("nashorn");
    }

    @Provides
    @Singleton
    @Named(UUID_FROM_STRING)
    public static Function<String, UUID> provideUuidToString() {
        return UUID::fromString;
    }

    @Provides
    @Singleton
    @Named(Variables.VERTX_POOL_SIZE)
    public static int provideVertxPoolSize(Environment env) {
        return Math.max(1, Integer.parseInt(env.getEnv(Variables.VERTX_POOL_SIZE, "20")));
    }

    @Provides
    @Singleton
    public static VerticleDeployer provideVerticleDeployer(
            Vertx vertx, @Named(Variables.VERTX_POOL_SIZE) int poolSize, Logger logger) {
        return new VerticleDeployer(vertx, poolSize, logger);
    }
}
