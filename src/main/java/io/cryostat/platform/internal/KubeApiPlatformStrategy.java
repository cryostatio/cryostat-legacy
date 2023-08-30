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
package io.cryostat.platform.internal;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

import io.cryostat.configuration.Variables;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.JFRConnectionToolkit;
import io.cryostat.core.sys.Environment;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.net.AuthManager;

import dagger.Lazy;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.apache.commons.lang3.StringUtils;

class KubeApiPlatformStrategy implements PlatformDetectionStrategy<KubeApiPlatformClient> {

    protected final Logger logger;
    protected final Lazy<? extends AuthManager> authMgr;
    protected final Environment env;
    protected final FileSystem fs;
    protected final Lazy<JFRConnectionToolkit> connectionToolkit;

    KubeApiPlatformStrategy(
            Logger logger,
            Lazy<? extends AuthManager> authMgr,
            Lazy<JFRConnectionToolkit> connectionToolkit,
            Environment env,
            FileSystem fs) {
        this.logger = logger;
        this.authMgr = authMgr;
        this.connectionToolkit = connectionToolkit;
        this.env = env;
        this.fs = fs;
    }

    @Override
    public boolean isAvailable() {
        logger.trace("Testing {} Availability", getClass().getSimpleName());
        try (KubernetesClient client = createClient()) {
            return testAvailability(client);
        } catch (Exception e) {
            logger.info(e);
        }
        return false;
    }

    @Override
    public KubeApiPlatformClient getPlatformClient() {
        logger.info("Selected {} Strategy", getClass().getSimpleName());
        return new KubeApiPlatformClient(
                getNamespaces(), createClient(), connectionToolkit, logger);
    }

    @Override
    public AuthManager getAuthManager() {
        return authMgr.get();
    }

    protected KubernetesClient createClient() {
        return new KubernetesClientBuilder().withTaskExecutor(ForkJoinPool.commonPool()).build();
    }

    @SuppressFBWarnings("DMI_HARDCODED_ABSOLUTE_FILENAME")
    protected boolean testAvailability(KubernetesClient client) {
        boolean hasNamespace = StringUtils.isNotBlank(getOwnNamespace());
        boolean hasSecrets = fs.isDirectory(Path.of("/var/run/secrets/kubernetes.io"));
        boolean hasServiceHost = env.hasEnv("KUBERNETES_SERVICE_HOST");
        return hasNamespace || hasSecrets || hasServiceHost;
    }

    @SuppressFBWarnings("DMI_HARDCODED_ABSOLUTE_FILENAME")
    protected String getOwnNamespace() {
        try {
            return fs.readString(Paths.get(Config.KUBERNETES_NAMESPACE_PATH));
        } catch (IOException e) {
            logger.trace(e);
            return null;
        }
    }

    protected List<String> getNamespaces() {
        List<String> list = new ArrayList<>();
        String cfg = env.getEnv(Variables.K8S_NAMESPACES, "");
        if (StringUtils.isNotBlank(cfg)) {
            list.addAll(Arrays.asList(cfg.split(",")));
        }
        return list;
    }
}
