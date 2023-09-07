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

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
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
import io.cryostat.recordings.RecordingTargetHelper.ReplacementPolicy;
import io.cryostat.rules.RuleRegistry.RuleEvent;
import io.cryostat.util.events.Event;
import io.cryostat.util.events.EventListener;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import org.apache.commons.lang3.tuple.Pair;

public class RuleProcessor extends AbstractVerticle implements Consumer<TargetDiscoveryEvent> {

    private final ExecutorService executor;
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

    private final Map<Pair<ServiceRef, Rule>, Set<Long>> tasks;

    RuleProcessor(
            Vertx vertx,
            ExecutorService executor,
            PlatformClient platformClient,
            RuleRegistry registry,
            CredentialsManager credentialsManager,
            RecordingOptionsBuilderFactory recordingOptionsBuilderFactory,
            TargetConnectionManager targetConnectionManager,
            RecordingArchiveHelper recordingArchiveHelper,
            RecordingTargetHelper recordingTargetHelper,
            RecordingMetadataManager metadataManager,
            PeriodicArchiverFactory periodicArchiverFactory,
            Logger logger) {
        this.vertx = vertx;
        this.executor = executor;
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
                        executor.submit(
                                () -> {
                                    platformClient.listUniqueReachableServices().stream()
                                            .filter(
                                                    serviceRef ->
                                                            event.getPayload().isEnabled()
                                                                    && registry.applies(
                                                                            event.getPayload(),
                                                                            serviceRef))
                                            .forEach(
                                                    serviceRef ->
                                                            activate(
                                                                    event.getPayload(),
                                                                    serviceRef));
                                });
                        break;
                    case REMOVED:
                        deactivate(event.getPayload(), null);
                        break;
                    case UPDATED:
                        if (!event.getPayload().isEnabled()) {
                            deactivate(event.getPayload(), null);
                        } else {
                            executor.submit(
                                    () -> {
                                        platformClient.listUniqueReachableServices().stream()
                                                .filter(
                                                        serviceRef ->
                                                                registry.applies(
                                                                        event.getPayload(),
                                                                        serviceRef))
                                                .forEach(
                                                        serviceRef ->
                                                                activate(
                                                                        event.getPayload(),
                                                                        serviceRef));
                                    });
                        }
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
                        credentialsManager
                                .resolveMatchingTargets(event.getPayload())
                                .forEach(
                                        sr -> {
                                            registry.getRules(sr).stream()
                                                    .filter(Rule::isEnabled)
                                                    .forEach(rule -> activate(rule, sr));
                                        });
                        break;
                    case REMOVED:
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
                activateAllRulesFor(tde.getServiceRef());
                break;
            case LOST:
                deactivate(null, tde.getServiceRef());
                break;
            case MODIFIED:
                activateAllRulesFor(tde.getServiceRef());
                break;
            default:
                throw new UnsupportedOperationException(tde.getEventKind().toString());
        }
    }

    private void activateAllRulesFor(ServiceRef serviceRef) {
        registry.getRules(serviceRef)
                .forEach(
                        rule -> {
                            if (rule.isEnabled()) {
                                activate(rule, serviceRef);
                            }
                        });
    }

    private void activate(Rule rule, ServiceRef serviceRef) {
        if (!rule.isEnabled()) {
            this.logger.trace(
                    "Activating rule {} for target {} aborted, rule is disabled {} ",
                    rule.getName(),
                    serviceRef.getServiceUri(),
                    rule.isEnabled());
            return;
        }
        if (tasks.containsKey(Pair.of(serviceRef, rule))) {
            this.logger.trace(
                    "Activating rule {} for target {} aborted, rule is already active",
                    rule.getName(),
                    serviceRef.getServiceUri());
            return;
        }
        this.logger.trace(
                "Activating rule {} for target {}", rule.getName(), serviceRef.getServiceUri());

        executor.submit(
                () -> {
                    try {
                        Credentials credentials = credentialsManager.getCredentials(serviceRef);
                        if (rule.isArchiver()) {
                            try {
                                archiveRuleRecording(
                                        new ConnectionDescriptor(serviceRef, credentials), rule);
                            } catch (Exception e) {
                                logger.error(e);
                            }
                        } else {
                            try {
                                startRuleRecording(
                                        new ConnectionDescriptor(serviceRef, credentials), rule);
                            } catch (Exception e) {
                                logger.error(e);
                            }

                            PeriodicArchiver periodicArchiver =
                                    periodicArchiverFactory.create(
                                            serviceRef,
                                            credentialsManager,
                                            rule,
                                            recordingArchiveHelper,
                                            this::archivalFailureHandler);
                            Pair<ServiceRef, Rule> key = Pair.of(serviceRef, rule);
                            Set<Long> ids = tasks.computeIfAbsent(key, k -> new HashSet<>());
                            int initialDelay = rule.getInitialDelaySeconds();
                            int archivalPeriodSeconds = rule.getArchivalPeriodSeconds();
                            if (initialDelay <= 0) {
                                initialDelay = archivalPeriodSeconds;
                            }
                            if (rule.getPreservedArchives() <= 0 || archivalPeriodSeconds <= 0) {
                                return;
                            }
                            long initialTask =
                                    vertx.setTimer(
                                            Duration.ofSeconds(initialDelay).toMillis(),
                                            initialId -> {
                                                tasks.get(key).remove(initialId);
                                                periodicArchiver.run();
                                                long periodicTask =
                                                        vertx.setPeriodic(
                                                                Duration.ofSeconds(
                                                                                archivalPeriodSeconds)
                                                                        .toMillis(),
                                                                periodicId ->
                                                                        executor.submit(
                                                                                periodicArchiver
                                                                                        ::run));
                                                ids.add(periodicTask);
                                            });
                            ids.add(initialTask);
                        }
                    } catch (ScriptException e) {
                        logger.error(e);
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
                ids.forEach(
                        (id) -> {
                            vertx.cancelTimer(id);
                            logger.trace("Cancelled timer {}", id);
                        });
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
        try {
            targetConnectionManager
                    .executeConnectedTaskAsync(
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
                            })
                    .get();
        } catch (Exception e) {
            logger.error(new RuleException(e));
        }
    }

    private void startRuleRecording(ConnectionDescriptor connectionDescriptor, Rule rule) {
        CompletableFuture<IRecordingDescriptor> future =
                targetConnectionManager.executeConnectedTaskAsync(
                        connectionDescriptor,
                        connection -> {
                            RecordingOptionsBuilder builder =
                                    recordingOptionsBuilderFactory
                                            .create(connection.getService())
                                            .name(rule.getRecordingName());
                            if (rule.getMaxAgeSeconds() > 0) {
                                builder = builder.maxAge(rule.getMaxAgeSeconds()).toDisk(true);
                            }
                            if (rule.getMaxSizeBytes() > 0) {
                                builder = builder.maxSize(rule.getMaxSizeBytes()).toDisk(true);
                            }
                            Pair<String, TemplateType> template =
                                    RecordingTargetHelper.parseEventSpecifierToTemplate(
                                            rule.getEventSpecifier());
                            return recordingTargetHelper.startRecording(
                                    ReplacementPolicy.STOPPED,
                                    connectionDescriptor,
                                    builder.build(),
                                    template.getLeft(),
                                    template.getRight(),
                                    new Metadata(),
                                    false);
                        });
        try {
            future.handleAsync(
                            (recording, throwable) -> {
                                if (throwable != null) {
                                    logger.error(new RuleException(throwable));
                                    return null;
                                }
                                try {
                                    Map<String, String> labels =
                                            new HashMap<>(
                                                    metadataManager
                                                            .getMetadata(
                                                                    connectionDescriptor,
                                                                    recording.getName())
                                                            .getLabels());
                                    labels.put("rule", rule.getName());
                                    metadataManager.setRecordingMetadata(
                                            connectionDescriptor,
                                            recording.getName(),
                                            new Metadata(labels));
                                } catch (IOException ioe) {
                                    logger.error(ioe);
                                }
                                return null;
                            })
                    .get();
        } catch (InterruptedException | ExecutionException e) {
            logger.error(new RuleException(e));
        }
    }
}
