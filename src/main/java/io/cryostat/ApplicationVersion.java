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
package io.cryostat;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import io.cryostat.core.log.Logger;

public class ApplicationVersion {

    private static final String RESOURCE_LOCATION = "/io/cryostat/version";
    private volatile String version;
    private final Logger logger;

    ApplicationVersion(Logger logger) {
        this.logger = logger;
    }

    public synchronized String getVersionString() {
        if (version == null) {
            try (BufferedReader br =
                    new BufferedReader(
                            new InputStreamReader(
                                    getClass().getResourceAsStream(RESOURCE_LOCATION),
                                    StandardCharsets.UTF_8))) {
                version =
                        br.lines()
                                .findFirst()
                                .orElseThrow(
                                        () ->
                                                new IllegalStateException(
                                                        String.format(
                                                                "Resource file %s is empty",
                                                                RESOURCE_LOCATION)))
                                .trim();
            } catch (Exception e) {
                logger.error(e);
                version = "unknown";
            }
        }
        return version;
    }
}
