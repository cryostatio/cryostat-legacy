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

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import javax.script.ScriptException;

import io.cryostat.core.log.Logger;
import io.cryostat.platform.PlatformClient;
import io.cryostat.platform.ServiceRef;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import dagger.Lazy;

public class MatchExpressionManager {
    private final MatchExpressionValidator matchExpressionValidator;
    private final Lazy<MatchExpressionEvaluator> matchExpressionEvaluator;
    private final PlatformClient platformClient;
    private final MatchExpressionDao dao;
    private final Gson gson;
    private final Logger logger;

    MatchExpressionManager(
            MatchExpressionValidator matchExpressionValidator,
            Lazy<MatchExpressionEvaluator> matchExpressionEvaluator,
            PlatformClient platformClient,
            MatchExpressionDao dao,
            Gson gson,
            Logger logger) {
        this.matchExpressionValidator = matchExpressionValidator;
        this.matchExpressionEvaluator = matchExpressionEvaluator;
        this.platformClient = platformClient;
        this.dao = dao;
        this.gson = gson;
        this.logger = logger;
    }

    public int addMatchExpression(String matchExpression)
            throws MatchExpressionValidationException {
        matchExpressionValidator.validate(matchExpression);
        MatchExpression expression = dao.save(new MatchExpression(matchExpression));
        return expression.getId();
    }

    public Optional<MatchExpression> get(int id) {
        return dao.get(id);
    }

    public List<MatchExpression> getAll() {
        return dao.getAll();
    }

    public boolean delete(int id) {
        return dao.delete(id);
    }

    public Set<ServiceRef> resolveMatchingTargets(int id) {
        Optional<MatchExpression> matchExpression = dao.get(id);
        if (matchExpression.isEmpty()) {
            return Set.of();
        }
        return resolveMatchingTargets(matchExpression.get());
    }

    public Set<ServiceRef> resolveMatchingTargets(MatchExpression expr) {
        Set<ServiceRef> matchedTargets = new HashSet<>();
        for (ServiceRef target : platformClient.listDiscoverableServices()) {
            try {
                if (matchExpressionEvaluator.get().applies(expr.getMatchExpression(), target)) {
                    matchedTargets.add(target);
                }
            } catch (ScriptException e) {
                logger.error(e);
                break;
            }
        }
        return matchedTargets;
    }

    public Set<ServiceRef> resolveMatchingTargets(MatchExpression expr, List<String> targets) {
        return resolveMatchingTargets(expr.getMatchExpression(), targets);
    }

    public Set<ServiceRef> resolveMatchingTargets(String expr, List<String> targets) {
        Set<ServiceRef> matchedTargets = new HashSet<>();
        for (ServiceRef target :
                platformClient.listDiscoverableServices().stream()
                        .filter(t -> targets.contains(t.getServiceUri().toString()))
                        .toList()) {
            try {
                if (matchExpressionEvaluator.get().applies(expr, target)) {
                    matchedTargets.add(target);
                }
            } catch (ScriptException e) {
                logger.error(e);
                break;
            }
        }
        return matchedTargets;
    }

    public List<String> parseTargets(String targets) throws IllegalArgumentException {
        Objects.requireNonNull(targets, "Targets must not be null");

        try {
            Type mapType = new TypeToken<List<String>>() {}.getType();
            List<String> parsedTargets = gson.fromJson(targets, mapType);
            if (parsedTargets == null) {
                throw new IllegalArgumentException(targets);
            }
            return parsedTargets;
        } catch (JsonSyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static class MatchedMatchExpression {
        private final String expression;
        private final Collection<ServiceRef> targets;

        public MatchedMatchExpression(String expression) {
            this.expression = expression;
            this.targets = Collections.emptySet();
        }

        public MatchedMatchExpression(String expression, Collection<ServiceRef> targets) {
            this.expression = expression;
            this.targets = new HashSet<>(targets);
        }

        public MatchedMatchExpression(MatchExpression expression) {
            this.expression = expression.getMatchExpression();
            this.targets = Collections.emptySet();
        }

        public MatchedMatchExpression(MatchExpression expression, Set<ServiceRef> targets) {
            this.expression = expression.getMatchExpression();
            this.targets = new HashSet<>(targets);
        }

        public String getExpression() {
            return expression;
        }

        public Collection<ServiceRef> getTargets() {
            return Collections.unmodifiableCollection(targets);
        }
    }
}
