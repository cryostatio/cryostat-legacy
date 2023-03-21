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
package io.cryostat.platform.internal;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import javax.inject.Named;
import javax.inject.Singleton;

import io.cryostat.configuration.Variables;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.JFRConnectionToolkit;
import io.cryostat.core.net.discovery.JvmDiscoveryClient;
import io.cryostat.core.sys.Environment;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.net.NetworkResolver;
import io.cryostat.net.NoopAuthManager;
import io.cryostat.net.openshift.OpenShiftAuthManager;
import io.cryostat.net.web.WebModule;

import com.google.gson.Gson;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import dagger.multibindings.ElementsIntoSet;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.fabric8.kubernetes.client.Config;
import io.vertx.core.Vertx;
import org.apache.commons.lang3.StringUtils;

@Module
public abstract class PlatformStrategyModule {

    public static final String K8S_INSTALL_NAMESPACE = "K8S_INSTALL_NAMESPACE";
    public static final String K8S_TARGET_NAMESPACES = "K8S_TARGET_NAMESPACES";

    @SuppressFBWarnings(
            value = "DMI_HARDCODED_ABSOLUTE_FILENAME",
            justification = "Kubernetes namespace file path is well-known and absolute")
    @Provides
    @Singleton
    @Named(K8S_INSTALL_NAMESPACE)
    static String provideK8sInstallNamespace(Environment env, FileSystem fs, Logger logger) {
        try {
            return fs.readFile(Paths.get(Config.KUBERNETES_NAMESPACE_PATH))
                    .lines()
                    .filter(StringUtils::isNotBlank)
                    .findFirst()
                    .get();
        } catch (IOException e) {
            logger.trace(e);
            return null;
        }
    }

    @Provides
    @Singleton
    @Named(K8S_TARGET_NAMESPACES)
    static List<String> provideK8sTargetNamespaces(Environment env) {
        List<String> list = new ArrayList<>();
        String cfg = env.getEnv(Variables.K8S_NAMESPACES, "");
        if (StringUtils.isNotBlank(cfg)) {
            list.addAll(Arrays.asList(cfg.split(",")));
        }
        return list;
    }

    @Provides
    @ElementsIntoSet
    static Set<PlatformDetectionStrategy<?>> providePlatformDetectionStrategies(
            @Named(K8S_INSTALL_NAMESPACE) Lazy<String> installNamespace,
            @Named(K8S_TARGET_NAMESPACES) Lazy<List<String>> targetNamespaces,
            Logger logger,
            Lazy<OpenShiftAuthManager> openShiftAuthManager,
            Lazy<NoopAuthManager> noopAuthManager,
            Lazy<JFRConnectionToolkit> connectionToolkit,
            @Named(WebModule.VERTX_EXECUTOR) ExecutorService executor,
            Vertx vertx,
            Gson gson,
            NetworkResolver resolver,
            Environment env,
            FileSystem fs) {
        return Set.of(
                new OpenShiftPlatformStrategy(
                        logger,
                        executor,
                        openShiftAuthManager,
                        env,
                        fs,
                        installNamespace,
                        targetNamespaces),
                new KubeApiPlatformStrategy(
                        logger,
                        executor,
                        noopAuthManager,
                        env,
                        fs,
                        installNamespace,
                        targetNamespaces),
                new KubeEnvPlatformStrategy(logger, fs, noopAuthManager, connectionToolkit, env),
                new PodmanPlatformStrategy(logger, noopAuthManager, vertx, gson, fs),
                new DefaultPlatformStrategy(
                        logger, noopAuthManager, () -> new JvmDiscoveryClient(logger)));
    }
}
