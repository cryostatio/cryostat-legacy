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
package io.cryostat.rules;

import java.util.function.Function;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.platform.ServiceRef;
import io.cryostat.recordings.RecordingArchiveHelper;

import org.apache.commons.lang3.tuple.Pair;

class PeriodicArchiverFactory {

    private final Logger logger;

    PeriodicArchiverFactory(Logger logger) {
        this.logger = logger;
    }

    PeriodicArchiver create(
            ServiceRef serviceRef,
            CredentialsManager credentialsManager,
            Rule rule,
            RecordingArchiveHelper recordingArchiveHelper,
            Function<Pair<String, Rule>, Void> failureNotifier) {
        return new PeriodicArchiver(
                serviceRef,
                credentialsManager,
                rule,
                recordingArchiveHelper,
                failureNotifier,
                logger);
    }
}
