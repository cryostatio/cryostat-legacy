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
                            invalidate(e.getPayload());
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
                            invalidate(e.getPayload().getMatchExpression());
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

    private void invalidate(String matchExpression) {
        var it = cache.asMap().keySet().iterator();
        while (it.hasNext()) {
            Pair<String, ServiceRef> entry = it.next();
            if (Objects.equals(matchExpression, entry.getKey())) {
                cache.invalidate(entry);
            }
        }
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
