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
package io.cryostat.net.openshift;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

import javax.inject.Named;
import javax.inject.Singleton;

import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Environment;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.net.AuthManager;
import io.cryostat.net.web.WebModule;
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
            @Named(WebModule.VERTX_EXECUTOR) ExecutorService executor,
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
                executor,
                Scheduler.systemScheduler(),
                logger);
    }

    @Binds
    @IntoSet
    abstract AuthManager bindOpenShiftAuthManager(OpenShiftAuthManager mgr);
}
