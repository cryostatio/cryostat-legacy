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
package io.cryostat.net.openshift;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;

import javax.inject.Named;
import javax.inject.Singleton;

import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Environment;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.net.AuthManager;
import io.cryostat.util.resource.ClassPropertiesLoader;

import com.github.benmanes.caffeine.cache.Scheduler;
import com.google.gson.Gson;
import dagger.Binds;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.IntoSet;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.OpenShiftConfigBuilder;
import org.apache.commons.lang3.StringUtils;

@Module
public abstract class OpenShiftNetworkModule {

    static final String OPENSHIFT_SERVICE_ACCOUNT_TOKEN = "OPENSHIFT_SERVICE_ACCOUNT_TOKEN";
    static final String OPENSHIFT_NAMESPACE = "OPENSHIFT_NAMESPACE";
    static final String TOKENED_CLIENT = "TOKENED_CLIENT";

    @Provides
    @Singleton
    @Named(OPENSHIFT_NAMESPACE)
    @SuppressFBWarnings(
            value = "DMI_HARDCODED_ABSOLUTE_FILENAME",
            justification = "Kubernetes namespace file path is well-known and absolute")
    static String provideNamespace(FileSystem fs) {
        try (BufferedReader br = fs.readFile(Paths.get(Config.KUBERNETES_NAMESPACE_PATH))) {
            return br.lines().filter(StringUtils::isNotBlank).findFirst().get();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Provides
    @Singleton
    @Named(OPENSHIFT_SERVICE_ACCOUNT_TOKEN)
    @SuppressFBWarnings(
            value = "DMI_HARDCODED_ABSOLUTE_FILENAME",
            justification = "Kubernetes serviceaccount file path is well-known and absolute")
    static String provideServiceAccountToken(FileSystem fs) {
        try (BufferedReader br =
                fs.readFile(Paths.get(Config.KUBERNETES_SERVICE_ACCOUNT_TOKEN_PATH))) {
            return br.lines().filter(StringUtils::isNotBlank).findFirst().get();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Provides
    @Named(TOKENED_CLIENT)
    static Function<String, OpenShiftClient> provideTokenedClient() {
        return token ->
                new DefaultOpenShiftClient(
                        new OpenShiftConfigBuilder().withOauthToken(token).build());
    }

    @Provides
    @Singleton
    static OpenShiftClient provideServiceAccountClient(
            @Named(OPENSHIFT_SERVICE_ACCOUNT_TOKEN) String serviceAccountToken,
            @Named(TOKENED_CLIENT) Function<String, OpenShiftClient> tokenedClient) {
        return tokenedClient.apply(serviceAccountToken);
    }

    @Provides
    @Singleton
    static OpenShiftAuthManager provideOpenShiftAuthManager(
            Environment env,
            @Named(OPENSHIFT_NAMESPACE) Lazy<String> namespace,
            Lazy<OpenShiftClient> serviceAccountClient,
            @Named(TOKENED_CLIENT) Function<String, OpenShiftClient> clientProvider,
            ClassPropertiesLoader classPropertiesLoader,
            Gson gson,
            Logger logger) {
        return new OpenShiftAuthManager(
                env,
                namespace,
                serviceAccountClient,
                clientProvider,
                classPropertiesLoader,
                gson,
                ForkJoinPool.commonPool(),
                Scheduler.systemScheduler(),
                logger);
    }

    @Binds
    @IntoSet
    abstract AuthManager bindOpenShiftAuthManager(OpenShiftAuthManager mgr);
}
