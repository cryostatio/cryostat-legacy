/*-
 * #%L
 * Container JFR
 * %%
 * Copyright (C) 2020 Red Hat, Inc.
 * %%
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
 * #L%
 */
package com.redhat.rhjmc.containerjfr.rules;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.management.remote.JMXServiceURL;

import org.apache.http.client.utils.URLEncodedUtils;

import com.redhat.rhjmc.containerjfr.configuration.CredentialsManager;
import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.net.Credentials;
import com.redhat.rhjmc.containerjfr.core.net.discovery.JvmDiscoveryClient.EventKind;
import com.redhat.rhjmc.containerjfr.platform.PlatformClient;
import com.redhat.rhjmc.containerjfr.platform.ServiceRef;
import com.redhat.rhjmc.containerjfr.platform.TargetDiscoveryEvent;
import com.redhat.rhjmc.containerjfr.util.HttpStatusCodeIdentifier;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.multipart.MultipartForm;

public class RuleProcessor implements Consumer<TargetDiscoveryEvent> {

    private final PlatformClient platformClient;
    private final RuleRegistry registry;
    private final ScheduledExecutorService scheduler;
    private final CredentialsManager credentialsManager;
    private final WebClient webClient;
    private final String postPath;
    private final PeriodicArchiverFactory periodicArchiverFactory;
    private final Function<Credentials, MultiMap> headersFactory;
    private final Logger logger;

    private final Map<ServiceRef, Future<?>> tasks;

    RuleProcessor(
            PlatformClient platformClient,
            RuleRegistry registry,
            ScheduledExecutorService scheduler,
            CredentialsManager credentialsManager,
            WebClient webClient,
            String postPath,
            PeriodicArchiverFactory periodicArchiverFactory,
            Function<Credentials, MultiMap> headersFactory,
            Logger logger) {
        this.platformClient = platformClient;
        this.registry = registry;
        this.scheduler = scheduler;
        this.credentialsManager = credentialsManager;
        this.webClient = webClient;
        this.postPath = postPath;
        this.periodicArchiverFactory = periodicArchiverFactory;
        this.headersFactory = headersFactory;
        this.logger = logger;

        this.tasks = new HashMap<>();
    }

    public void enable() {
        this.platformClient.addTargetDiscoveryListener(this);
    }

    public void disable() {
        this.platformClient.removeTargetDiscoveryListener(this);
        this.tasks.forEach((serviceRef, future) -> future.cancel(true));
        this.tasks.clear();
    }

    // FIXME should the processor should also be able to apply new rules to targets that have
    // already appeared?
    @Override
    public void accept(TargetDiscoveryEvent tde) {
        if (EventKind.LOST.equals(tde.getEventKind())) {
            Future<?> task = tasks.remove(tde.getServiceRef());
            if (task != null) {
                task.cancel(true);
            }
            return;
        }
        if (!EventKind.FOUND.equals(tde.getEventKind())) {
            throw new UnsupportedOperationException(tde.getEventKind().toString());
        }
        registry.getRules(tde.getServiceRef())
                .forEach(
                        rule -> {
                            this.logger.trace(
                                    "Activating rule {} for target {}",
                                    rule.getName(),
                                    tde.getServiceRef().getJMXServiceUrl());

                            Credentials credentials =
                                    credentialsManager.getCredentials(
                                            tde.getServiceRef().getJMXServiceUrl().toString());
                            try {
                                Future<Boolean> success =
                                        startRuleRecording(
                                                tde.getServiceRef().getJMXServiceUrl(),
                                                rule.getRecordingName(),
                                                rule.getEventSpecifier(),
                                                rule.getMaxSizeBytes(),
                                                rule.getMaxAgeSeconds(),
                                                credentials);
                                if (!success.get()) {
                                    logger.trace("Rule activation failed");
                                    return;
                                }
                            } catch (InterruptedException | ExecutionException e) {
                                logger.error(e);
                            }

                            logger.trace("Rule activation successful");
                            if (rule.getPreservedArchives() <= 0
                                    || rule.getArchivalPeriodSeconds() <= 0) {
                                return;
                            }
                            tasks.put(
                                    tde.getServiceRef(),
                                    scheduler.scheduleAtFixedRate(
                                            periodicArchiverFactory.create(
                                                    tde.getServiceRef(), credentials, rule),
                                            rule.getArchivalPeriodSeconds(),
                                            rule.getArchivalPeriodSeconds(),
                                            TimeUnit.SECONDS));
                        });
    }

    private Future<Boolean> startRuleRecording(
            JMXServiceURL serviceUrl,
            String recordingName,
            String eventSpecifier,
            int maxSizeBytes,
            int maxAgeSeconds,
            Credentials credentials) {
        // FIXME using an HTTP request to localhost here works well enough, but is needlessly
        // complex. The API handler targeted here should be refactored to extract the logic that
        // creates the recording from the logic that simply figures out the recording parameters
        // from the POST form, path param, and headers. Then the handler should consume the API
        // exposed by this refactored chunk, and this refactored chunk can also be consumed here
        // rather than firing HTTP requests to ourselves
        MultipartForm form = MultipartForm.create();
        form.attribute("recordingName", recordingName);
        form.attribute("events", eventSpecifier);
        if (maxAgeSeconds > 0) {
            form.attribute("maxAge", String.valueOf(maxAgeSeconds));
        }
        if (maxSizeBytes > 0) {
            form.attribute("maxSize", String.valueOf(maxSizeBytes));
        }
        String path =
                postPath.replaceAll(
                        ":targetId", URLEncodedUtils.formatSegments(serviceUrl.toString()));

        CompletableFuture<Boolean> result = new CompletableFuture<>();
        this.webClient
                .post(path)
                .putHeaders(headersFactory.apply(credentials))
                .sendMultipartForm(
                        form,
                        ar -> {
                            if (ar.failed()) {
                                this.logger.error(
                                        new RuntimeException(
                                                "Activation of automatic rule failed", ar.cause()));
                                result.completeExceptionally(ar.cause());
                                return;
                            }
                            HttpResponse<Buffer> resp = ar.result();
                            if (!HttpStatusCodeIdentifier.isSuccessCode(resp.statusCode())) {
                                this.logger.error(resp.bodyAsString());
                                result.complete(false);
                                return;
                            }
                            result.complete(true);
                        });
        return result;
    }
}
