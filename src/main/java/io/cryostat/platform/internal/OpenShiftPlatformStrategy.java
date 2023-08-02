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

import io.cryostat.core.log.Logger;
import io.cryostat.core.net.JFRConnectionToolkit;
import io.cryostat.core.sys.Environment;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.net.AuthManager;

import dagger.Lazy;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.openshift.client.OpenShiftClient;

class OpenShiftPlatformStrategy extends KubeApiPlatformStrategy {

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
}
