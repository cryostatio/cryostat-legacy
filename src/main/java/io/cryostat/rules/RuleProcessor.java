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

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import javax.script.ScriptException;

import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;
import org.openjdk.jmc.rjmx.services.jfr.IRecordingDescriptor;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.configuration.CredentialsManager.CredentialsEvent;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.Credentials;
import io.cryostat.core.templates.TemplateType;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.platform.PlatformClient;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.TargetDiscoveryEvent;
import io.cryostat.recordings.RecordingArchiveHelper;
import io.cryostat.recordings.RecordingMetadataManager;
import io.cryostat.recordings.RecordingMetadataManager.Metadata;
import io.cryostat.recordings.RecordingOptionsBuilderFactory;
import io.cryostat.recordings.RecordingTargetHelper;
import io.cryostat.rules.RuleRegistry.RuleEvent;
import io.cryostat.util.events.Event;
import io.cryostat.util.events.EventListener;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import org.apache.commons.codec.binary.Base32;
import org.apache.commons.lang3.tuple.Pair;

public class RuleProcessor extends AbstractVerticle implements Consumer<TargetDiscoveryEvent> {

    private final PlatformClient platformClient;
    private final RuleRegistry registry;
    private final CredentialsManager credentialsManager;
    private final RecordingOptionsBuilderFactory recordingOptionsBuilderFactory;
    private final TargetConnectionManager targetConnectionManager;
    private final RecordingArchiveHelper recordingArchiveHelper;
    private final RecordingTargetHelper recordingTargetHelper;
    private final RecordingMetadataManager metadataManager;
    private final PeriodicArchiverFactory periodicArchiverFactory;
    private final Logger logger;
    private final Base32 base32;

    private final Map<Pair<ServiceRef, Rule>, Set<Long>> tasks;

    RuleProcessor(
            Vertx vertx,
            PlatformClient platformClient,
            RuleRegistry registry,
            CredentialsManager credentialsManager,
            RecordingOptionsBuilderFactory recordingOptionsBuilderFactory,
            TargetConnectionManager targetConnectionManager,
            RecordingArchiveHelper recordingArchiveHelper,
            RecordingTargetHelper recordingTargetHelper,
            RecordingMetadataManager metadataManager,
            PeriodicArchiverFactory periodicArchiverFactory,
            Logger logger,
            Base32 base32) {
        this.vertx = vertx;
        this.platformClient = platformClient;
        this.registry = registry;
        this.credentialsManager = credentialsManager;
        this.recordingOptionsBuilderFactory = recordingOptionsBuilderFactory;
        this.targetConnectionManager = targetConnectionManager;
        this.recordingArchiveHelper = recordingArchiveHelper;
        this.recordingTargetHelper = recordingTargetHelper;
        this.metadataManager = metadataManager;
        this.periodicArchiverFactory = periodicArchiverFactory;
        this.logger = logger;
        this.base32 = base32;
        this.tasks = new HashMap<>();

        this.registry.addListener(this.ruleListener());
        this.credentialsManager.addListener(this.credentialsListener());
    }

    @Override
    public void start() {
        this.platformClient.addTargetDiscoveryListener(this);
    }

    @Override
    public void stop() {
        this.platformClient.removeTargetDiscoveryListener(this);
        this.tasks.forEach((ruleExecution, ids) -> ids.forEach(vertx::cancelTimer));
        this.tasks.clear();
    }

    public EventListener<RuleRegistry.RuleEvent, Rule> ruleListener() {
        return new EventListener<RuleRegistry.RuleEvent, Rule>() {

            @Override
            public void onEvent(Event<RuleEvent, Rule> event) {
                switch (event.getEventType()) {
                    case ADDED:
                        vertx.<List<ServiceRef>>executeBlocking(
                                promise ->
                                        promise.complete(platformClient.listDiscoverableServices()),
                                false,
                                result ->
                                        result.result().stream()
                                                .filter(
                                                        serviceRef ->
                                                                registry.applies(
                                                                        event.getPayload(),
                                                                        serviceRef))
                                                .forEach(
                                                        serviceRef ->
                                                                activate(
                                                                        event.getPayload(),
                                                                        serviceRef)));
                        break;
                    case REMOVED:
                        deactivate(event.getPayload(), null);
                        break;
                    default:
                        throw new UnsupportedOperationException(event.getEventType().toString());
                }
            }
        };
    }

    public EventListener<CredentialsManager.CredentialsEvent, String> credentialsListener() {
        return new EventListener<CredentialsManager.CredentialsEvent, String>() {

            @Override
            public void onEvent(Event<CredentialsEvent, String> event) {
                switch (event.getEventType()) {
                    case ADDED:
                        try {
                            Set<ServiceRef> servicesSet =
                                    credentialsManager.resolveMatchingTargets(event.getPayload());
                            for (ServiceRef servicesRef : servicesSet) {
                                registry.getRules(servicesRef)
                                        .forEach(rule -> activate(rule, servicesRef));
                            }
                        } catch (IOException e) {
                            logger.error(e);
                        }
                        break;
                    default:
                        throw new UnsupportedOperationException(event.getEventType().toString());
                }
            }
        };
    }

    @Override
    public synchronized void accept(TargetDiscoveryEvent tde) {
        switch (tde.getEventKind()) {
            case FOUND:
                registry.getRules(tde.getServiceRef())
                        .forEach(rule -> activate(rule, tde.getServiceRef()));
                break;
            case LOST:
                deactivate(null, tde.getServiceRef());
                break;
            default:
                throw new UnsupportedOperationException(tde.getEventKind().toString());
        }
    }

    private void activate(Rule rule, ServiceRef serviceRef) {
        this.logger.trace(
                "Activating rule {} for target {}", rule.getName(), serviceRef.getServiceUri());

        vertx.<Credentials>executeBlocking(
                        promise -> {
                            try {
                                Credentials creds = credentialsManager.getCredentials(serviceRef);
                                promise.complete(creds);
                            } catch (IOException | ScriptException e) {
                                promise.fail(e);
                            }
                        })
                .onSuccess(c -> logger.trace("Rule activation successful"))
                .onSuccess(
                        credentials -> {
                            if (rule.isArchiver()) {
                                try {
                                    archiveRuleRecording(
                                            new ConnectionDescriptor(serviceRef, credentials),
                                            rule);
                                } catch (Exception e) {
                                    logger.error(e);
                                }
                            } else {
                                try {
                                    startRuleRecording(
                                            new ConnectionDescriptor(serviceRef, credentials),
                                            rule);
                                } catch (Exception e) {
                                    logger.error(e);
                                }

                                PeriodicArchiver periodicArchiver =
                                        periodicArchiverFactory.create(
                                                serviceRef,
                                                credentialsManager,
                                                rule,
                                                recordingArchiveHelper,
                                                this::archivalFailureHandler,
                                                base32);
                                Pair<ServiceRef, Rule> key = Pair.of(serviceRef, rule);
                                Set<Long> ids = tasks.computeIfAbsent(key, k -> new HashSet<>());
                                long initialTask =
                                        vertx.setTimer(
                                                Duration.ofSeconds(rule.getInitialDelaySeconds())
                                                        .toMillis(),
                                                initialId -> {
                                                    tasks.get(key).remove(initialId);
                                                    periodicArchiver.run();
                                                    if (rule.getPreservedArchives() <= 0
                                                            || rule.getArchivalPeriodSeconds()
                                                                    <= 0) {
                                                        return;
                                                    }
                                                    long periodicTask =
                                                            vertx.setPeriodic(
                                                                    Duration.ofSeconds(
                                                                                    rule
                                                                                            .getArchivalPeriodSeconds())
                                                                            .toMillis(),
                                                                    periodicId ->
                                                                            periodicArchiver.run());
                                                    ids.add(periodicTask);
                                                });
                                ids.add(initialTask);
                            }
                        });
    }

    private void deactivate(Rule rule, ServiceRef serviceRef) {
        if (rule == null && serviceRef == null) {
            throw new IllegalArgumentException("Both parameters cannot be null");
        }
        if (rule != null) {
            logger.trace("Deactivating rule {}", rule.getName());
        }
        if (serviceRef != null) {
            logger.trace("Deactivating rules for {}", serviceRef.getServiceUri());
        }
        Iterator<Map.Entry<Pair<ServiceRef, Rule>, Set<Long>>> it = tasks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Pair<ServiceRef, Rule>, Set<Long>> entry = it.next();
            boolean sameRule = Objects.equals(entry.getKey().getRight(), rule);
            boolean sameTarget = Objects.equals(entry.getKey().getLeft(), serviceRef);
            if (sameRule || sameTarget) {
                Set<Long> ids = entry.getValue();
                ids.forEach(vertx::cancelTimer);
                it.remove();
            }
        }
    }

    private Void archivalFailureHandler(Pair<ServiceRef, Rule> key) {
        tasks.get(key).forEach(vertx::cancelTimer);
        tasks.remove(key);
        return null;
    }

    private void archiveRuleRecording(ConnectionDescriptor connectionDescriptor, Rule rule) {
        vertx.executeBlocking(
                promise -> {
                    try {
                        targetConnectionManager.executeConnectedTask(
                                connectionDescriptor,
                                connection -> {
                                    IRecordingDescriptor descriptor =
                                            connection.getService().getSnapshotRecording();
                                    try {
                                        recordingArchiveHelper
                                                .saveRecording(
                                                        connectionDescriptor, descriptor.getName())
                                                .get();
                                    } finally {
                                        connection.getService().close(descriptor);
                                    }

                                    return null;
                                },
                                false);
                        promise.complete();
                    } catch (Exception e) {
                        promise.fail(e);
                    }
                },
                false,
                result -> {
                    if (result.failed()) {
                        logger.error(new RuleException(result.cause()));
                    }
                });
    }

    private void startRuleRecording(ConnectionDescriptor connectionDescriptor, Rule rule) {
        vertx.<IRecordingDescriptor>executeBlocking(
                promise -> {
                    try {
                        IRecordingDescriptor recording =
                                targetConnectionManager.executeConnectedTask(
                                        connectionDescriptor,
                                        connection -> {
                                            RecordingOptionsBuilder builder =
                                                    recordingOptionsBuilderFactory
                                                            .create(connection.getService())
                                                            .name(rule.getRecordingName());
                                            if (rule.getMaxAgeSeconds() > 0) {
                                                builder =
                                                        builder.maxAge(rule.getMaxAgeSeconds())
                                                                .toDisk(true);
                                            }
                                            if (rule.getMaxSizeBytes() > 0) {
                                                builder =
                                                        builder.maxSize(rule.getMaxSizeBytes())
                                                                .toDisk(true);
                                            }
                                            Pair<String, TemplateType> template =
                                                    RecordingTargetHelper
                                                            .parseEventSpecifierToTemplate(
                                                                    rule.getEventSpecifier());
                                            return recordingTargetHelper.startRecording(
                                                    true,
                                                    connectionDescriptor,
                                                    builder.build(),
                                                    template.getLeft(),
                                                    template.getRight(),
                                                    new Metadata());
                                        },
                                        false);
                        promise.complete(recording);
                    } catch (Exception e) {
                        logger.error(
                                "Failed to start rule {} recording on {}",
                                rule.getName(),
                                connectionDescriptor.getTargetId());
                        promise.fail(e);
                    }
                },
                false,
                result -> {
                    if (result.failed()) {
                        logger.error(new RuleException(result.cause()));
                        return;
                    }
                    IRecordingDescriptor recording = result.result();
                    try {
                        Map<String, String> labels =
                                new HashMap<>(
                                        metadataManager
                                                .getMetadata(
                                                        connectionDescriptor, recording.getName())
                                                .getLabels());
                        labels.put("rule", rule.getName());
                        metadataManager.setRecordingMetadata(
                                connectionDescriptor, recording.getName(), new Metadata(labels));
                    } catch (IOException ioe) {
                        logger.error(ioe);
                    }
                });
    }
}
