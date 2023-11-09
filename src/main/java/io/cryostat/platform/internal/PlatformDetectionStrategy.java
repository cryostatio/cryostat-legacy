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

import java.util.HashMap;
import java.util.Map;

import io.cryostat.core.sys.Environment;
import io.cryostat.net.AuthManager;
import io.cryostat.platform.PlatformClient;

public interface PlatformDetectionStrategy<T extends PlatformClient> {
    boolean isAvailable();

    T getPlatformClient();

    AuthManager getAuthManager();

    default Map<String, String> environment(Environment env) {
        Map<String, String> map = new HashMap<>();
        if (env.hasEnv("INSIGHTS_PROXY")) {
            map.put("INSIGHTS_SVC", env.getEnv("INSIGHTS_PROXY"));
        }
        return map;
    }
}
