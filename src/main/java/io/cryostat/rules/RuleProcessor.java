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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.Credentials;
import io.cryostat.core.net.discovery.JvmDiscoveryClient.EventKind;
import io.cryostat.platform.PlatformClient;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.TargetDiscoveryEvent;
import io.cryostat.rules.RuleRegistry.RuleEvent;
import io.cryostat.util.HttpStatusCodeIdentifier;
import io.cryostat.util.events.Event;
import io.cryostat.util.events.EventListener;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.multipart.MultipartForm;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.client.utils.URLEncodedUtils;

public class RuleProcessor
        implements Consumer<TargetDiscoveryEvent>, EventListener<RuleRegistry.RuleEvent, Rule> {

    private final PlatformClient platformClient;
    private final RuleRegistry registry;
    private final ScheduledExecutorService scheduler;
    private final CredentialsManager credentialsManager;
    private final WebClient webClient;
    private final PeriodicArchiverFactory periodicArchiverFactory;
    private final Function<Credentials, MultiMap> headersFactory;
    private final Logger logger;

    private final Map<Pair<ServiceRef, Rule>, Future<?>> tasks;

    RuleProcessor(
            PlatformClient platformClient,
            RuleRegistry registry,
            ScheduledExecutorService scheduler,
            CredentialsManager credentialsManager,
            WebClient webClient,
            PeriodicArchiverFactory periodicArchiverFactory,
            Function<Credentials, MultiMap> headersFactory,
            Logger logger) {
        this.platformClient = platformClient;
        this.registry = registry;
        this.scheduler = scheduler;
        this.credentialsManager = credentialsManager;
        this.webClient = webClient;
        this.periodicArchiverFactory = periodicArchiverFactory;
        this.headersFactory = headersFactory;
        this.logger = logger;
        this.tasks = new HashMap<>();

        this.registry.addListener(this);
    }

    public void enable() {
        this.platformClient.addTargetDiscoveryListener(this);
    }

    public synchronized void disable() {
        this.platformClient.removeTargetDiscoveryListener(this);
        this.tasks.forEach((ruleExecution, future) -> future.cancel(true));
        this.tasks.clear();
    }

    @Override
    public synchronized void onEvent(Event<RuleEvent, Rule> event) {
        switch (event.getEventType()) {
            case ADDED:
                // FIXME the processor should also be able to apply new rules to targets that have
                // already appeared
                break;
            case REMOVED:
                Iterator<Map.Entry<Pair<ServiceRef, Rule>, Future<?>>> it =
                        tasks.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<Pair<ServiceRef, Rule>, Future<?>> entry = it.next();
                    if (!Objects.equals(entry.getKey().getRight(), event.getPayload())) {
                        continue;
                    }
                    Future<?> task = entry.getValue();
                    if (task != null) {
                        task.cancel(true);
                    }
                    it.remove();
                }
                break;
            default:
                throw new IllegalArgumentException(event.getEventType().toString());
        }
    }

    @Override
    public synchronized void accept(TargetDiscoveryEvent tde) {
        if (EventKind.LOST.equals(tde.getEventKind())) {
            registry.getRules(tde.getServiceRef())
                    .forEach(
                            rule -> {
                                Pair<ServiceRef, Rule> key = Pair.of(tde.getServiceRef(), rule);
                                Future<?> task = tasks.remove(key);
                                if (task != null) {
                                    task.cancel(true);
                                }
                            });
            return;
        }
        if (!EventKind.FOUND.equals(tde.getEventKind())) {
            throw new UnsupportedOperationException(tde.getEventKind().toString());
        }
        registry.getRules(tde.getServiceRef())
                .forEach(rule -> activateRule(rule, tde.getServiceRef()));
    }

    private void activateRule(Rule rule, ServiceRef serviceRef) {
        this.logger.trace(
                "Activating rule {} for target {}", rule.getName(), serviceRef.getServiceUri());

        Credentials credentials =
                credentialsManager.getCredentials(serviceRef.getServiceUri().toString());
        try {
            Future<Boolean> success =
                    startRuleRecording(
                            serviceRef.getServiceUri(),
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
        if (rule.getPreservedArchives() <= 0 || rule.getArchivalPeriodSeconds() <= 0) {
            return;
        }
        tasks.put(
                Pair.of(serviceRef, rule),
                scheduler.scheduleAtFixedRate(
                        periodicArchiverFactory.create(
                                serviceRef, credentialsManager, rule, this::archivalFailureHandler),
                        rule.getArchivalPeriodSeconds(),
                        rule.getArchivalPeriodSeconds(),
                        TimeUnit.SECONDS));
    }

    private Void archivalFailureHandler(Pair<ServiceRef, Rule> id) {
        Future<?> task = tasks.get(id);
        if (task != null) {
            task.cancel(true);
        }
        return null;
    }

    private Future<Boolean> startRuleRecording(
            URI serviceUri,
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
                URI.create(
                                String.format(
                                        "/api/v1/targets/%s/recordings",
                                        URLEncodedUtils.formatSegments(serviceUri.toString())))
                        .normalize()
                        .toString();

        this.logger.trace("POST {}", path);

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
