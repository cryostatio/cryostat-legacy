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
package io.cryostat.net;

import java.net.SocketException;
import java.net.UnknownHostException;

import io.cryostat.configuration.Variables;
import io.cryostat.core.sys.Environment;

public class NetworkConfiguration {

    private final Environment env;
    private final NetworkResolver resolver;

    NetworkConfiguration(Environment env, NetworkResolver resolver) {
        this.env = env;
        this.resolver = resolver;
    }

    public String getWebServerHost() throws SocketException, UnknownHostException {
        return env.getEnv(Variables.WEBSERVER_HOST, resolver.getHostAddress());
    }

    public int getDefaultWebServerPort() {
        return 8181;
    }

    public int getInternalWebServerPort() {
        return Integer.parseInt(
                env.getEnv(Variables.WEBSERVER_PORT, String.valueOf(getDefaultWebServerPort())));
    }

    public int getExternalWebServerPort() {
        return Integer.parseInt(
                env.getEnv(
                        Variables.WEBSERVER_PORT_EXT, String.valueOf(getInternalWebServerPort())));
    }

    public boolean isSslProxied() {
        return env.hasEnv(Variables.WEBSERVER_SSL_PROXIED);
    }

    public boolean isUntrustedSslAllowed() {
        return env.hasEnv(Variables.WEBSERVER_ALLOW_UNTRUSTED_SSL);
    }
}
