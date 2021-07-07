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

import javax.script.Bindings;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import io.cryostat.platform.ServiceRef;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;

class RuleMatcher {

    private final ScriptEngine scriptEngine = new ScriptEngineManager().getEngineByName("nashorn");

    public boolean applies(Rule rule, ServiceRef serviceRef) throws ScriptException {
        RuleAppliesEvent evt = new RuleAppliesEvent(rule.getName());
        try {
            evt.begin();
            Object result =
                    this.scriptEngine.eval(rule.getMatchExpression(), createBindings(serviceRef));
            if (result instanceof Boolean) {
                return (Boolean) result;
            } else {
                throw new ScriptException(
                        String.format(
                                "Rule %s non-boolean match expression evaluation result: %s",
                                rule.getName(), result));
            }
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

    @Name("io.cryostat.rules.RuleMatcher.RuleAppliesEvent")
    @Label("Rule Expression Matching")
    @Category("Cryostat")
    @SuppressFBWarnings(
            value = "URF_UNREAD_FIELD",
            justification = "The event fields are recorded with JFR instead of accessed directly")
    public static class RuleAppliesEvent extends Event {

        String ruleName;

        RuleAppliesEvent(String ruleName) {
            this.ruleName = ruleName;
        }
    }

    @Name("io.cryostat.rules.RuleMatcher.BindingsCreationEvent")
    @Label("Rule Binding Creation")
    @Category("Cryostat")
    @SuppressFBWarnings(
            value = "URF_UNREAD_FIELD",
            justification = "The event fields are recorded with JFR instead of accessed directly")
    public static class BindingsCreationEvent extends Event {}
}
