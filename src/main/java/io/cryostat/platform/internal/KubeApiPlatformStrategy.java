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

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executor;

import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.Environment;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.net.AuthManager;

import dagger.Lazy;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.apache.commons.lang3.StringUtils;

class KubeApiPlatformStrategy implements PlatformDetectionStrategy<KubeApiPlatformClient> {

    protected final Logger logger;
    protected final Executor executor;
    protected final Lazy<? extends AuthManager> authMgr;
    protected final Environment env;
    protected final FileSystem fs;
    protected final Lazy<String> installNamespace;
    protected final Lazy<List<String>> targetNamespaces;

    KubeApiPlatformStrategy(
            Logger logger,
            Executor executor,
            Lazy<? extends AuthManager> authMgr,
            Environment env,
            FileSystem fs,
            Lazy<String> installNamespace,
            Lazy<List<String>> targetNamespaces) {
        this.logger = logger;
        this.executor = executor;
        this.authMgr = authMgr;
        this.env = env;
        this.fs = fs;
        this.installNamespace = installNamespace;
        this.targetNamespaces = targetNamespaces;
    }

    @Override
    public int getPriority() {
        return PRIORITY_PLATFORM + 10;
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
        return new KubeApiPlatformClient(targetNamespaces.get(), createClient(), logger);
    }

    @Override
    public AuthManager getAuthManager() {
        return authMgr.get();
    }

    protected KubernetesClient createClient() {
        return new KubernetesClientBuilder().withTaskExecutor(executor).build();
    }

    @SuppressFBWarnings("DMI_HARDCODED_ABSOLUTE_FILENAME")
    protected boolean testAvailability(KubernetesClient client) {
        boolean hasNamespace = StringUtils.isNotBlank(installNamespace.get());
        boolean hasSecrets = fs.isDirectory(Path.of("/var/run/secrets/kubernetes.io"));
        boolean hasServiceHost = env.hasEnv("KUBERNETES_SERVICE_HOST");
        return hasNamespace || hasSecrets || hasServiceHost;
    }
}
