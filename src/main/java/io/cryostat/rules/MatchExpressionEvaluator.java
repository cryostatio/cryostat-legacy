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

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionException;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.platform.ServiceRef;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import org.apache.commons.lang3.tuple.Pair;

public class MatchExpressionEvaluator {

    private final ScriptEngine scriptEngine;
    private final LoadingCache<Pair<String, ServiceRef>, Boolean> cache;
    private final Logger logger;

    MatchExpressionEvaluator(
            ScriptEngine scriptEngine,
            CredentialsManager credentialsManager,
            RuleRegistry ruleRegistry,
            Logger logger) {
        this.scriptEngine = scriptEngine;
        this.logger = logger;
        this.cache =
                Caffeine.newBuilder()
                        .maximumSize(1024) // should this be configurable?
                        .build(k -> compute(k.getKey(), k.getValue()));

        credentialsManager.addListener(
                e -> {
                    switch (e.getEventType()) {
                        case REMOVED:
                            invalidateCache(e.getPayload());
                            break;
                        default:
                            // ignore
                            break;
                    }
                });
        ruleRegistry.addListener(
                e -> {
                    switch (e.getEventType()) {
                        case REMOVED:
                            invalidateCache(e.getPayload().getMatchExpression());
                            break;
                        default:
                            // ignore
                            break;
                    }
                });
    }

    private boolean compute(String matchExpression, ServiceRef serviceRef) throws ScriptException {
        Object r = this.scriptEngine.eval(matchExpression, createBindings(serviceRef));
        if (r == null) {
            throw new ScriptException(
                    String.format(
                            "Null match expression evaluation result: %s (%s)",
                            matchExpression, serviceRef));
        } else if (r instanceof Boolean) {
            return (Boolean) r;
        } else {
            throw new ScriptException(
                    String.format(
                            "Non-boolean match expression evaluation result: %s (%s) -> %s",
                            matchExpression, serviceRef, r));
        }
    }

    private void invalidateCache(String matchExpression) {
        var it = cache.asMap().keySet().iterator();
        while (it.hasNext()) {
            Pair<String, ServiceRef> entry = it.next();
            if (Objects.equals(matchExpression, entry.getKey())) {
                cache.invalidate(entry);
            }
        }
    }

    public void validate(String matchExpression) throws ScriptException {
        ServiceRef dummyRef = new ServiceRef("jvmId", URI.create("file:///foo/bar"), "alias");
        compute(matchExpression, dummyRef);
    }

    public boolean applies(String matchExpression, ServiceRef serviceRef) throws ScriptException {
        Pair<String, ServiceRef> key = Pair.of(matchExpression, serviceRef);
        MatchExpressionAppliesEvent evt = new MatchExpressionAppliesEvent(matchExpression);
        try {
            evt.begin();
            Boolean result = cache.get(key);
            if (result == null) {
                throw new IllegalStateException();
            }
            return result;
        } catch (CompletionException e) {
            if (e.getCause() instanceof ScriptException) {
                throw (ScriptException) e.getCause();
            }
            throw e;
        } finally {
            evt.end();
            if (evt.shouldCommit()) {
                evt.commit();
            }
        }
    }

    Bindings createBindings(ServiceRef serviceRef) {
        BindingsCreationEvent evt = new BindingsCreationEvent();
        try {
            evt.begin();
            Bindings bindings = this.scriptEngine.createBindings();
            Map<String, String> cryostatAnnotations =
                    new HashMap<>(serviceRef.getCryostatAnnotations().size());
            for (Map.Entry<ServiceRef.AnnotationKey, String> entry :
                    serviceRef.getCryostatAnnotations().entrySet()) {
                cryostatAnnotations.put(entry.getKey().name(), entry.getValue());
            }
            Map<String, Object> target = new HashMap<>();
            target.put("connectUrl", serviceRef.getServiceUri().toString());
            target.put("jvmId", serviceRef.getJvmId());
            target.put("alias", serviceRef.getAlias().orElse(null));
            target.put("labels", serviceRef.getLabels());
            target.put(
                    "annotations",
                    Map.of(
                            "platform",
                            serviceRef.getPlatformAnnotations(),
                            "cryostat",
                            cryostatAnnotations));
            bindings.put("target", target);
            return bindings;
        } finally {
            evt.end();
            if (evt.shouldCommit()) {
                evt.commit();
            }
        }
    }

    @Name("io.cryostat.rules.MatchExpressionEvaluator.MatchExpressionAppliesEvent")
    @Label("Match Expression Evaluation")
    @Category("Cryostat")
    @SuppressFBWarnings(
            value = "URF_UNREAD_FIELD",
            justification = "The event fields are recorded with JFR instead of accessed directly")
    public static class MatchExpressionAppliesEvent extends Event {

        String matchExpression;

        MatchExpressionAppliesEvent(String matchExpression) {
            this.matchExpression = matchExpression;
        }
    }

    @Name("io.cryostat.rules.MatchExpressionEvaluator.BindingsCreationEvent")
    @Label("Match Expression Binding Creation")
    @Category("Cryostat")
    @SuppressFBWarnings(
            value = "URF_UNREAD_FIELD",
            justification = "The event fields are recorded with JFR instead of accessed directly")
    public static class BindingsCreationEvent extends Event {}
}
