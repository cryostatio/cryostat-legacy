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
package io.cryostat.rules;

import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Named;
import javax.security.sasl.SaslException;

import io.cryostat.MainModule;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.platform.ServiceRef;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingNotFoundException;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;

class PeriodicArchiver implements Runnable {

    private final ServiceRef serviceRef;
    private final CredentialsManager credentialsManager;
    private final Rule rule;
    private final RecordingArchiveHelper recordingArchiveHelper;
    private final Function<Pair<ServiceRef, Rule>, Void> failureNotifier;
    private final Logger logger;

    private final Queue<String> previousRecordings;

    PeriodicArchiver(
            ServiceRef serviceRef,
            CredentialsManager credentialsManager,
            Rule rule,
            @Named(MainModule.RECORDINGS_PATH) Path archivedRecordingsPath,
            RecordingArchiveHelper recordingArchiveHelper,
            Function<Pair<ServiceRef, Rule>, Void> failureNotifier,
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
            // If there are no previous recordings, either this is the first time this rule is being archived
            // or the Cryostat instance was restarted. Since it could be the latter, populate the array
            // with any previously archived recordings for this rule.
            if (previousRecordings.isEmpty()) {
                URI serviceUri = serviceRef.getServiceUri();
                JsonArray archivedRecordings = getArchivedRecordings().get();
                Iterator<Object> it = archivedRecordings.iterator();
                Pattern recordingFilenamePattern =
                        Pattern.compile(
                                String.format(
                                        "(%d)\\/([A-Za-z\\d-]*)_(%s)_([\\d]*T[\\d]*Z)(\\.[\\d]+)?",
                                        serviceUri.hashCode(), rule.getRecordingName()));

                while (it.hasNext()) {
                    JsonObject entry = (JsonObject) it.next();
                    String filename = entry.getString("name");
                    Matcher m = recordingFilenamePattern.matcher(filename);
                    if (m.matches()) {
                        previousRecordings.add(filename);
                    }
                }
            }

            while (this.previousRecordings.size() > this.rule.getPreservedArchives() - 1) {
                pruneArchive(this.previousRecordings.remove()).get();
            }

            performArchival().get();
        } catch (Exception e) {
            logger.error(e);

            if (ExceptionUtils.hasCause(e, ExecutionException.class)
                    || ExceptionUtils.hasCause(e, InterruptedException.class)
                    || ExceptionUtils.hasCause(e, RecordingNotFoundException.class)
                    || ExceptionUtils.hasCause(e, SecurityException.class)
                    || ExceptionUtils.hasCause(e, SaslException.class)
                    || ExceptionUtils.hasCause(e, ArchivePathException.class)) {

                failureNotifier.apply(Pair.of(serviceRef, rule));
            }
        }
    }

    public Future<Boolean> performArchival()
            throws InterruptedException, ExecutionException, Exception {

        CompletableFuture<Boolean> future = new CompletableFuture<>();

        try {
            String recordingName = rule.getRecordingName();
            ConnectionDescriptor connectionDescriptor =
                    new ConnectionDescriptor(
                            serviceRef, credentialsManager.getCredentials(serviceRef));

            String saveName =
                    recordingArchiveHelper.saveRecording(connectionDescriptor, recordingName);
            this.previousRecordings.add(saveName);
            future.complete(true);
        } catch (RecordingNotFoundException e) {
            future.completeExceptionally(e);
        }

        return future;
    }

    public Future<Boolean> pruneArchive(String recordingName) throws Exception {

        CompletableFuture<Boolean> future = new CompletableFuture<>();

        try {
            ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor(serviceRef);
            recordingArchiveHelper.deleteRecording(connectionDescriptor, recordingName);
            previousRecordings.remove(recordingName);
            future.complete(true);
        } catch (RecordingNotFoundException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    public Future<JsonArray> getArchivedRecordings() throws Exception {

        CompletableFuture<JsonArray> future = new CompletableFuture<>();

        try {
            List<Map<String, String>> archivedRecordings = recordingArchiveHelper.getRecordings();
            future.complete(new JsonArray(archivedRecordings));
        } catch (ArchivePathException e) {
            future.completeExceptionally(e);
        }
        return future;
    }
}
