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
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import io.cryostat.core.log.Logger;
import io.cryostat.core.net.JFRConnectionToolkit;
import io.cryostat.core.sys.Environment;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.net.AuthManager;

import dagger.Lazy;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.client.OpenShiftClient;
import org.apache.commons.lang3.StringUtils;

class OpenShiftPlatformStrategy extends KubeApiPlatformStrategy {

    static final String INSIGHTS_TOKEN_PATH =
            "/var/run/secrets/operator.cryostat.io/insights-token/token";

    OpenShiftPlatformStrategy(
            Logger logger,
            Lazy<? extends AuthManager> authMgr,
            Lazy<JFRConnectionToolkit> connectionToolkit,
            Environment env,
            FileSystem fs) {
        super(logger, authMgr, connectionToolkit, env, fs);
    }

    @Override
    protected boolean testAvailability(KubernetesClient client) {
        return super.testAvailability(client) && (((OpenShiftClient) client).isSupported());
    }

    @Override
    protected OpenShiftClient createClient() {
        return super.createClient().adapt(OpenShiftClient.class);
    }

    @Override
    public Map<String, String> environment() {
        Map<String, String> env = new HashMap<>(super.environment());
        String token = getInsightsToken();
        if (StringUtils.isNotBlank(token)) {
            env.put("INSIGHTS_TOKEN", token);
        }
        return env;
    }

    @SuppressFBWarnings(
            value = "DMI_HARDCODED_ABSOLUTE_FILENAME",
            justification =
                    "file path is well-known and absolute, injected by the Cryostat Operator")
    private String getInsightsToken() {
        try {
            return fs.readString(Paths.get(INSIGHTS_TOKEN_PATH));
        } catch (IOException e) {
            logger.warn(e);
            return null;
        }
    }
}
