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
package io.cryostat.configuration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.script.ScriptException;

import io.cryostat.core.log.Logger;
import io.cryostat.core.net.Credentials;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.platform.PlatformClient;
import io.cryostat.platform.ServiceRef;
import io.cryostat.rules.MatchExpressionEvaluator;
import io.cryostat.rules.MatchExpressionValidationException;
import io.cryostat.rules.MatchExpressionValidator;
import io.cryostat.util.events.AbstractEventEmitter;
import io.cryostat.util.events.EventType;

import org.apache.commons.lang3.StringUtils;

public class CredentialsManager
        extends AbstractEventEmitter<CredentialsManager.CredentialsEvent, String> {

    private final MatchExpressionValidator matchExpressionValidator;
    private final MatchExpressionEvaluator matchExpressionEvaluator;
    private final PlatformClient platformClient;
    private final StoredCredentialsDao dao;
    private final Logger logger;

    CredentialsManager(
            MatchExpressionValidator matchExpressionValidator,
            MatchExpressionEvaluator matchExpressionEvaluator,
            PlatformClient platformClient,
            StoredCredentialsDao dao,
            NotificationFactory notificationFactory,
            Logger logger) {
        this.matchExpressionValidator = matchExpressionValidator;
        this.matchExpressionEvaluator = matchExpressionEvaluator;
        this.platformClient = platformClient;
        this.dao = dao;
        this.logger = logger;
    }

    public static String targetIdToMatchExpression(String targetId) {
        if (StringUtils.isBlank(targetId)) {
            return null;
        }
        return String.format("target.connectUrl == \"%s\"", targetId);
    }

    public int addCredentials(String matchExpression, Credentials credentials)
            throws MatchExpressionValidationException {
        matchExpressionValidator.validate(matchExpression);
        StoredCredentials saved = dao.save(new StoredCredentials(matchExpression, credentials));
        emit(CredentialsEvent.ADDED, matchExpression);
        return saved.getId();
    }

    public void removeCredentials(String matchExpression)
            throws MatchExpressionValidationException {
        matchExpressionValidator.validate(matchExpression);
        if (dao.deleteByMatchExpression(matchExpression) < 1) {
            throw new IllegalArgumentException();
        }
        emit(CredentialsEvent.REMOVED, matchExpression);
    }

    public Credentials getCredentialsByTargetId(String targetId) throws ScriptException {
        for (ServiceRef service : this.platformClient.listDiscoverableServices()) {
            if (Objects.equals(targetId, service.getServiceUri().toString())) {
                return getCredentials(service);
            }
        }
        return null;
    }

    public Credentials getCredentials(ServiceRef serviceRef) throws ScriptException {
        for (StoredCredentials sc : dao.getAll()) {
            if (matchExpressionEvaluator.applies(sc.getMatchExpression(), serviceRef)) {
                return sc.getCredentials();
            }
        }
        return null;
    }

    public Collection<ServiceRef> getServiceRefsWithCredentials() throws ScriptException {
        List<ServiceRef> result = new ArrayList<>();
        for (ServiceRef service : this.platformClient.listDiscoverableServices()) {
            Credentials credentials = getCredentials(service);
            if (credentials != null) {
                result.add(service);
            }
        }
        return result;
    }

    public String get(int id) {
        return dao.get(id).orElseThrow(() -> new IllegalArgumentException()).getMatchExpression();
    }

    public Set<ServiceRef> resolveMatchingTargets(int id) {
        String matchExpression = get(id);
        Set<ServiceRef> matchedTargets = new HashSet<>();
        for (ServiceRef target : platformClient.listDiscoverableServices()) {
            try {
                if (matchExpressionEvaluator.applies(matchExpression, target)) {
                    matchedTargets.add(target);
                }
            } catch (ScriptException e) {
                logger.error(e);
                break;
            }
        }
        return matchedTargets;
    }

    public Set<ServiceRef> resolveMatchingTargets(String matchExpression) {
        Set<ServiceRef> matchedTargets = new HashSet<>();
        for (ServiceRef target : platformClient.listDiscoverableServices()) {
            try {
                if (matchExpressionEvaluator.applies(matchExpression, target)) {
                    matchedTargets.add(target);
                }
            } catch (ScriptException e) {
                logger.error(e);
                break;
            }
        }
        return matchedTargets;
    }

    public void delete(int id) {
        dao.delete(id);
    }

    public Map<Integer, String> getAll() {
        Map<Integer, String> result = new HashMap<>();
        for (StoredCredentials sc : dao.getAll()) {
            result.put(sc.getId(), sc.getMatchExpression());
        }
        return result;
    }

    public static class MatchedCredentials {
        private final String matchExpression;
        private final Collection<ServiceRef> targets;

        public MatchedCredentials(String matchExpression, Collection<ServiceRef> targets) {
            this.matchExpression = matchExpression;
            this.targets = new HashSet<>(targets);
        }

        public String getMatchExpression() {
            return matchExpression;
        }

        public Collection<ServiceRef> getTargets() {
            return Collections.unmodifiableCollection(targets);
        }

        @Override
        public int hashCode() {
            return Objects.hash(matchExpression, targets);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            MatchedCredentials other = (MatchedCredentials) obj;
            return Objects.equals(matchExpression, other.matchExpression)
                    && Objects.equals(targets, other.targets);
        }
    }

    @Deprecated(since = "2.2", forRemoval = true)
    static class TargetSpecificStoredCredentials {
        private final String targetId;
        private final Credentials credentials;

        TargetSpecificStoredCredentials(String targetId, Credentials credentials) {
            this.targetId = targetId;
            this.credentials = credentials;
        }

        String getTargetId() {
            return this.targetId;
        }

        Credentials getCredentials() {
            return this.credentials;
        }

        @Override
        public int hashCode() {
            return Objects.hash(credentials, targetId);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            TargetSpecificStoredCredentials other = (TargetSpecificStoredCredentials) obj;
            return Objects.equals(credentials, other.credentials)
                    && Objects.equals(targetId, other.targetId);
        }
    }

    public enum CredentialsEvent implements EventType {
        ADDED,
        REMOVED,
        ;
    }
}
