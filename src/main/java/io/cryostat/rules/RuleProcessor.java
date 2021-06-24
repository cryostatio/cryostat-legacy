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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.openjdk.jmc.flightrecorder.configuration.recording.RecordingOptionsBuilder;

import io.cryostat.commands.internal.RecordingOptionsBuilderFactory;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.Credentials;
import io.cryostat.core.templates.TemplateType;
import io.cryostat.net.ConnectionDescriptor;
import io.cryostat.net.TargetConnectionManager;
import io.cryostat.platform.PlatformClient;
import io.cryostat.platform.ServiceRef;
import io.cryostat.platform.TargetDiscoveryEvent;
import io.cryostat.recordings.RecordingCreationHelper;
import io.cryostat.rules.RuleRegistry.RuleEvent;
import io.cryostat.util.events.Event;
import io.cryostat.util.events.EventListener;

import org.apache.commons.lang3.tuple.Pair;

public class RuleProcessor
        implements Consumer<TargetDiscoveryEvent>, EventListener<RuleRegistry.RuleEvent, Rule> {

    private final PlatformClient platformClient;
    private final RuleRegistry registry;
    private final ScheduledExecutorService scheduler;
    private final CredentialsManager credentialsManager;
    private final RecordingOptionsBuilderFactory recordingOptionsBuilderFactory;
    private final TargetConnectionManager targetConnectionManager;
    private final RecordingCreationHelper recordingCreationHelper;
    private final PeriodicArchiverFactory periodicArchiverFactory;
    private final Logger logger;

    private final Map<Pair<ServiceRef, Rule>, Future<?>> tasks;

    RuleProcessor(
            PlatformClient platformClient,
            RuleRegistry registry,
            ScheduledExecutorService scheduler,
            CredentialsManager credentialsManager,
            RecordingOptionsBuilderFactory recordingOptionsBuilderFactory,
            TargetConnectionManager targetConnectionManager,
            RecordingCreationHelper recordingCreationHelper,
            PeriodicArchiverFactory periodicArchiverFactory,
            Logger logger) {
        this.platformClient = platformClient;
        this.registry = registry;
        this.scheduler = scheduler;
        this.credentialsManager = credentialsManager;
        this.recordingOptionsBuilderFactory = recordingOptionsBuilderFactory;
        this.targetConnectionManager = targetConnectionManager;
        this.recordingCreationHelper = recordingCreationHelper;
        this.periodicArchiverFactory = periodicArchiverFactory;
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
                platformClient.listDiscoverableServices().stream()
                        .filter(serviceRef -> registry.applies(event.getPayload(), serviceRef))
                        .forEach(serviceRef -> activate(event.getPayload(), serviceRef));
                break;
            case REMOVED:
                deactivate(event.getPayload(), null);
                break;
            default:
                throw new UnsupportedOperationException(event.getEventType().toString());
        }
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

        Credentials credentials =
                credentialsManager.getCredentials(serviceRef.getServiceUri().toString());
        try {
            startRuleRecording(new ConnectionDescriptor(serviceRef, credentials), rule);
        } catch (Exception e) {
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
        Iterator<Map.Entry<Pair<ServiceRef, Rule>, Future<?>>> it = tasks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Pair<ServiceRef, Rule>, Future<?>> entry = it.next();
            boolean sameRule = Objects.equals(entry.getKey().getRight(), rule);
            boolean sameTarget = Objects.equals(entry.getKey().getLeft(), serviceRef);
            if (sameRule || sameTarget) {
                Future<?> task = entry.getValue();
                if (task != null) {
                    task.cancel(true);
                }
                it.remove();
            }
        }
    }

    private Void archivalFailureHandler(Pair<ServiceRef, Rule> id) {
        Future<?> task = tasks.get(id);
        if (task != null) {
            task.cancel(true);
        }
        return null;
    }

    private void startRuleRecording(ConnectionDescriptor connectionDescriptor, Rule rule)
            throws Exception {

        targetConnectionManager.executeConnectedTask(
                connectionDescriptor,
                connection -> {
                    RecordingOptionsBuilder builder =
                            recordingOptionsBuilderFactory
                                    .create(connection.getService())
                                    .name(rule.getRecordingName())
                                    .toDisk(true);
                    if (rule.getMaxAgeSeconds() > 0) {
                        builder = builder.maxAge(rule.getMaxAgeSeconds());
                    }
                    if (rule.getMaxSizeBytes() > 0) {
                        builder = builder.maxSize(rule.getMaxSizeBytes());
                    }
                    Pair<String, TemplateType> template =
                            RecordingCreationHelper.parseEventSpecifierToTemplate(
                                    rule.getEventSpecifier());
                    recordingCreationHelper.startRecording(
                            connectionDescriptor,
                            builder.build(),
                            template.getLeft(),
                            template.getRight());
                    return null;
                });
    }
}
