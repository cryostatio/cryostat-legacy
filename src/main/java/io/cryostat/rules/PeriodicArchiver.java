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

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.web.http.AbstractAuthenticatedRequestHandler;
import io.cryostat.platform.ServiceRef;
import io.cryostat.recordings.RecordingArchiveHelper;

import org.apache.commons.lang3.tuple.Pair;

class PeriodicArchiver implements Runnable {

    private static final Pattern RECORDING_FILENAME_PATTERN =
            Pattern.compile(
                    "([A-Za-z\\d-]*)_([A-Za-z\\d-_]*)_([\\d]*T[\\d]*Z)(\\.[\\d]+)?(\\.jfr)?");

    private final ServiceRef serviceRef;
    private final CredentialsManager credentialsManager;
    private final Rule rule;
    private final RecordingArchiveHelper recordingArchiveHelper;
    private final Function<Pair<String, Rule>, Void> failureNotifier;
    private final Logger logger;

    private final Queue<String> previousRecordings;

    PeriodicArchiver(
            ServiceRef serviceRef,
            CredentialsManager credentialsManager,
            Rule rule,
            RecordingArchiveHelper recordingArchiveHelper,
            Function<Pair<String, Rule>, Void> failureNotifier,
            Logger logger) {
        this.serviceRef = serviceRef;
        this.credentialsManager = credentialsManager;
        this.recordingArchiveHelper = recordingArchiveHelper;
        this.rule = rule;
        this.failureNotifier = failureNotifier;
        this.logger = logger;

        this.previousRecordings = new ArrayDeque<>(this.rule.getPreservedArchives());
    }

    @Override
    public void run() {
        logger.trace("PeriodicArchiver for {} running", rule.getRecordingName());

        try {
            // If there are no previous recordings, either this is the first time this rule is being
            // archived or the Cryostat instance was restarted. Since it could be the latter,
            // populate the array with any previously archived recordings for this rule.
            if (previousRecordings.isEmpty()) {
                String serviceUri = serviceRef.getServiceUri().toString();
                List<ArchivedRecordingInfo> archivedRecordings =
                        recordingArchiveHelper
                                .getRecordings(serviceRef.getServiceUri().toString())
                                .get();

                for (ArchivedRecordingInfo archivedRecordingInfo : archivedRecordings) {
                    String fileName = archivedRecordingInfo.getName();
                    Matcher m = RECORDING_FILENAME_PATTERN.matcher(fileName);
                    if (m.matches()) {
                        String recordingName = m.group(2);

                        if (Objects.equals(serviceUri, archivedRecordingInfo.getServiceUri())
                                && Objects.equals(recordingName, rule.getRecordingName())) {
                            previousRecordings.add(fileName);
                        }
                    }
                }
            }

            while (previousRecordings.size() > rule.getPreservedArchives() - 1) {
                pruneArchive(previousRecordings.remove());
            }

            performArchival();
        } catch (Exception e) {
            logger.error(e);

            if (AbstractAuthenticatedRequestHandler.isJmxAuthFailure(e)
                    || AbstractAuthenticatedRequestHandler.isJmxSslFailure(e)
                    || AbstractAuthenticatedRequestHandler.isServiceTypeFailure(e)) {
                failureNotifier.apply(Pair.of(serviceRef.getJvmId(), rule));
            }
        }
    }

    private void performArchival() throws InterruptedException, ExecutionException, Exception {
        String recordingName = rule.getRecordingName();
        ConnectionDescriptor connectionDescriptor =
                new ConnectionDescriptor(serviceRef, credentialsManager.getCredentials(serviceRef));

        ArchivedRecordingInfo archivedRecordingInfo =
                recordingArchiveHelper.saveRecording(connectionDescriptor, recordingName).get();
        previousRecordings.add(archivedRecordingInfo.getName());
    }

    private void pruneArchive(String recordingName) throws Exception {
        recordingArchiveHelper
                .deleteRecording(serviceRef.getServiceUri().toString(), recordingName)
                .get();
        previousRecordings.remove(recordingName);
    }

    public Queue<String> getPreviousRecordings() {
        return previousRecordings;
    }
}
