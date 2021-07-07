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

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Function;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.platform.ServiceRef;
import io.cryostat.recordings.RecordingHelper;
import io.cryostat.recordings.RecordingNotFoundException;

import io.vertx.ext.web.handler.impl.HttpStatusException;
import org.apache.commons.lang3.tuple.Pair;

class PeriodicArchiver implements Runnable {

    private final ServiceRef serviceRef;
    private final CredentialsManager credentialsManager;
    private final Rule rule;
    private final RecordingHelper recordingHelper;
    private final Function<Pair<ServiceRef, Rule>, Void> failureNotifier;
    private final Logger logger;

    private final Queue<String> previousRecordings;

    PeriodicArchiver(
            ServiceRef serviceRef,
            CredentialsManager credentialsManager,
            Rule rule,
            RecordingHelper recordingHelper,
            Function<Pair<ServiceRef, Rule>, Void> failureNotifier,
            Logger logger) {
        this.serviceRef = serviceRef;
        this.credentialsManager = credentialsManager;
        this.recordingHelper = recordingHelper;
        this.rule = rule;
        this.failureNotifier = failureNotifier;
        this.logger = logger;

        // FIXME this needs to be populated at startup by scanning the existing archived recordings,
        // in case we have been restarted and already previously processed archival for this rule
        this.previousRecordings = new ArrayDeque<>(this.rule.getPreservedArchives());
    }

    @Override
    public void run() {
        logger.trace("PeriodicArchiver for {} running", rule.getRecordingName());

        try {
            while (this.previousRecordings.size() > this.rule.getPreservedArchives() - 1) {
                pruneArchive(this.previousRecordings.remove()).get();
            }

            performArchival();
        } catch (InterruptedException | ExecutionException e) {
            logger.error(e);
            failureNotifier.apply(Pair.of(serviceRef, rule));
        } catch (RecordingNotFoundException e) {
            throw new HttpStatusException(404, e);
        } catch (Exception e) {
            throw new HttpStatusException(500, e);
        }
    }

    void performArchival() throws InterruptedException, ExecutionException, Exception {

        String recordingName = rule.getRecordingName();
        ConnectionDescriptor connectionDescriptor =
                new ConnectionDescriptor(serviceRef, credentialsManager.getCredentials(serviceRef));

        String saveName = recordingHelper.saveRecording(connectionDescriptor, recordingName);
        this.previousRecordings.add(saveName);
    }

    Future<Boolean> pruneArchive(String recordingName) {

        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor(serviceRef);
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        try {
            recordingHelper.deleteRecording(connectionDescriptor, recordingName);
            previousRecordings.remove(recordingName);
            future.complete(true);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }
}
