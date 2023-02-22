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
    public static VerticleDeployer provideVerticleDeployer(Vertx vertx, Logger logger) {
        return new VerticleDeployer(vertx, logger);
    }
}
