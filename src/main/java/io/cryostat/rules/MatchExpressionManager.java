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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import javax.script.ScriptException;

import io.cryostat.core.log.Logger;
import io.cryostat.platform.PlatformClient;
import io.cryostat.platform.ServiceRef;

import com.google.gson.Gson;
import dagger.Lazy;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

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
        return resolveMatchingTargets(expr.getMatchExpression(), s -> true);
    }

    public Set<ServiceRef> resolveMatchingTargets(String expr, Predicate<ServiceRef> targetFilter) {
        Set<ServiceRef> matchedTargets = new HashSet<>();
        for (ServiceRef target :
                platformClient.listDiscoverableServices().stream().filter(targetFilter).toList()) {
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

        @Override
        public boolean equals(Object other) {
            if (other == null) {
                return false;
            }
            if (other == this) {
                return true;
            }
            if (!(other instanceof MatchedMatchExpression)) {
                return false;
            }
            MatchedMatchExpression mme = (MatchedMatchExpression) other;
            return new EqualsBuilder()
                    .append(expression, mme.expression)
                    .append(targets, mme.targets)
                    .build();
        }

        @Override
        public int hashCode() {
            return new HashCodeBuilder().append(expression).append(targets).toHashCode();
        }
    }
}
