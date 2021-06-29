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
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.platform.ServiceRef;
import io.cryostat.rules.RuleRegistry.RuleEvent;
import io.cryostat.util.events.AbstractEventEmitter;
import io.cryostat.util.events.EventType;

import com.google.gson.Gson;

public class RuleRegistry extends AbstractEventEmitter<RuleEvent, Rule> {

    private final Path rulesDir;
    private final RuleMatcher ruleMatcher;
    private final FileSystem fs;
    private final Set<Rule> rules;
    private final Gson gson;
    private final Logger logger;

    RuleRegistry(Path rulesDir, RuleMatcher ruleMatcher, FileSystem fs, Gson gson, Logger logger) {
        this.rulesDir = rulesDir;
        this.ruleMatcher = ruleMatcher;
        this.fs = fs;
        this.gson = gson;
        this.logger = logger;
        this.rules = new HashSet<>();
    }

    public void loadRules() throws IOException {
        this.fs.listDirectoryChildren(rulesDir).stream()
                .peek(n -> logger.trace("Rules file: {}", n))
                .map(rulesDir::resolve)
                .map(
                        path -> {
                            try {
                                return fs.readFile(path);
                            } catch (IOException e) {
                                logger.warn(e);
                                return null;
                            }
                        })
                .filter(Objects::nonNull)
                .map(reader -> gson.fromJson(reader, Rule.class))
                .forEach(rules::add);
    }

    public Rule addRule(Rule rule) throws IOException {
        if (hasRuleByName(rule.getName())) {
            throw new RuleException(
                    String.format(
                            "Rule with name \"%s\" already exists; refusing to overwrite",
                            rule.getName()));
        }
        Path destination = rulesDir.resolve(rule.getName() + ".json");
        this.fs.writeString(
                destination,
                gson.toJson(rule),
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
        loadRules();
        emit(RuleEvent.ADDED, rule);
        return rule;
    }

    public boolean hasRuleByName(String name) {
        return getRule(name).isPresent();
    }

    public Optional<Rule> getRule(String name) {
        return this.rules.stream().filter(r -> Objects.equals(r.getName(), name)).findFirst();
    }

    public boolean applies(Rule rule, ServiceRef serviceRef) {
        return ruleMatcher.applies(rule, serviceRef);
    }

    public Set<Rule> getRules(ServiceRef serviceRef) {
        if (!serviceRef.getAlias().isPresent()) {
            return Set.of();
        }
        return rules.stream().filter(r -> applies(r, serviceRef)).collect(Collectors.toSet());
    }

    public Set<Rule> getRules() {
        return new HashSet<>(rules);
    }

    public void deleteRule(Rule rule) throws IOException {
        this.deleteRule(rule.getName());
    }

    public void deleteRule(String name) throws IOException {
        for (String child : this.fs.listDirectoryChildren(rulesDir)) {
            if (!Objects.equals(child, name + ".json")) {
                continue;
            }
            fs.deleteIfExists(rulesDir.resolve(child));
        }
        this.rules.stream()
                .filter(r -> Objects.equals(r.getName(), name))
                .findFirst()
                .ifPresent(
                        rule -> {
                            emit(RuleEvent.REMOVED, rule);
                            this.rules.remove(rule);
                        });
    }

    public void deleteRules(ServiceRef serviceRef) throws IOException {
        for (Rule rule : getRules(serviceRef)) {
            deleteRule(rule);
        }
    }

    enum RuleEvent implements EventType {
        ADDED,
        REMOVED,
        ;
    }
}
