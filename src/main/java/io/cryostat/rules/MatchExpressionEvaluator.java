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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import io.cryostat.platform.ServiceRef;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Scheduler;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import org.apache.commons.lang3.tuple.Pair;

public class MatchExpressionEvaluator {

    private final ScriptEngine scriptEngine;
    private final LoadingCache<Pair<String, ServiceRef>, Boolean> cache;

    MatchExpressionEvaluator(
            ScriptEngine scriptEngine, Executor executor, Scheduler scheduler, Duration cacheTtl) {
        this.scriptEngine = scriptEngine;
        this.cache =
                Caffeine.newBuilder()
                        .executor(executor)
                        .scheduler(scheduler)
                        .expireAfterAccess(cacheTtl)
                        .build(k -> compute(k.getKey(), k.getValue()));
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
            bindings.put(
                    "target",
                    Map.of(
                            "connectUrl",
                            serviceRef.getServiceUri(),
                            "alias",
                            serviceRef.getAlias().orElse(null),
                            "labels",
                            serviceRef.getLabels(),
                            "annotations",
                            Map.of(
                                    "platform",
                                    serviceRef.getPlatformAnnotations(),
                                    "cryostat",
                                    cryostatAnnotations)));
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
