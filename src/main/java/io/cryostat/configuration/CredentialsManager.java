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

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
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
import io.cryostat.core.sys.FileSystem;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.platform.PlatformClient;
import io.cryostat.platform.ServiceRef;
import io.cryostat.rules.MatchExpressionEvaluator;

import com.google.gson.Gson;
import org.apache.commons.codec.binary.Base32;
import org.apache.commons.lang3.StringUtils;

public class CredentialsManager {

    private final Path credentialsDir;
    private final MatchExpressionEvaluator matchExpressionEvaluator;
    private final FileSystem fs;
    private final PlatformClient platformClient;
    private final Gson gson;
    private final Base32 base32;
    private final Logger logger;

    private final Map<String, Credentials> credentialsMap;

    CredentialsManager(
            Path credentialsDir,
            MatchExpressionEvaluator matchExpressionEvaluator,
            FileSystem fs,
            PlatformClient platformClient,
            NotificationFactory notificationFactory,
            Gson gson,
            Base32 base32,
            Logger logger) {
        this.credentialsDir = credentialsDir;
        this.matchExpressionEvaluator = matchExpressionEvaluator;
        this.fs = fs;
        this.platformClient = platformClient;
        this.gson = gson;
        this.base32 = base32;
        this.logger = logger;
        this.credentialsMap = new HashMap<>();
    }

    public void migrate() throws Exception {
        for (String file : this.fs.listDirectoryChildren(credentialsDir)) {
            BufferedReader reader;
            try {
                Path path = credentialsDir.resolve(file);
                reader = fs.readFile(path);
                TargetSpecificStoredCredentials targetSpecificCredential =
                        gson.fromJson(reader, TargetSpecificStoredCredentials.class);

                String targetId = targetSpecificCredential.getTargetId();
                if (StringUtils.isNotBlank(targetId)) {
                    addCredentials(
                            targetIdToMatchExpression(targetSpecificCredential.getTargetId()),
                            targetSpecificCredential.getCredentials());
                    fs.deleteIfExists(path);
                    logger.info("Migrated {}", path);
                }
            } catch (IOException e) {
                logger.warn(e);
                continue;
            }
        }
    }

    public static String targetIdToMatchExpression(String targetId) {
        if (StringUtils.isBlank(targetId)) {
            return null;
        }
        return String.format("target.connectUrl == \"%s\"", targetId);
    }

    public void load() throws IOException {
        this.fs.listDirectoryChildren(credentialsDir).stream()
                .peek(n -> logger.trace("Credentials file: {}", n))
                .map(credentialsDir::resolve)
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
                .map(reader -> gson.fromJson(reader, StoredCredentials.class))
                .forEach(sc -> credentialsMap.put(sc.getMatchExpression(), sc.getCredentials()));
    }

    public boolean addCredentials(String matchExpression, Credentials credentials)
            throws IOException {
        boolean replaced = credentialsMap.containsKey(matchExpression);
        credentialsMap.put(matchExpression, credentials);
        Path destination = getPersistedPath(matchExpression);
        fs.writeString(
                destination,
                gson.toJson(new StoredCredentials(matchExpression, credentials)),
                StandardOpenOption.WRITE,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
        fs.setPosixFilePermissions(
                destination,
                Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        return replaced;
    }

    public boolean removeCredentials(String matchExpression) throws IOException {
        Credentials deleted = this.credentialsMap.remove(matchExpression);
        fs.deleteIfExists(getPersistedPath(matchExpression));
        return deleted != null;
    }

    public Credentials getCredentialsByTargetId(String targetId) {
        for (ServiceRef service : this.platformClient.listDiscoverableServices()) {
            if (Objects.equals(targetId, service.getServiceUri().toString())) {
                return getCredentials(service);
            }
        }
        return null;
    }

    public Credentials getCredentials(ServiceRef serviceRef) {
        for (Map.Entry<String, Credentials> entry : credentialsMap.entrySet()) {
            try {
                if (matchExpressionEvaluator.applies(entry.getKey(), serviceRef)) {
                    return entry.getValue();
                }
            } catch (ScriptException e) {
                logger.error(e);
                continue;
            }
        }
        return null;
    }

    public Collection<ServiceRef> getServiceRefsWithCredentials() {
        List<ServiceRef> result = new ArrayList<>();
        for (ServiceRef service : this.platformClient.listDiscoverableServices()) {
            Credentials credentials = getCredentials(service);
            if (credentials != null) {
                result.add(service);
            }
        }
        return result;
    }

    public Collection<String> getMatchExpressions() {
        return credentialsMap.keySet();
    }

    public List<MatchedCredentials> getMatchExpressionsWithMatchedTargets() {
        List<MatchedCredentials> result = new ArrayList<>();
        List<ServiceRef> targets = platformClient.listDiscoverableServices();
        for (String expr : getMatchExpressions()) {
            Set<ServiceRef> matchedTargets = new HashSet<>();
            for (ServiceRef target : targets) {
                try {
                    if (matchExpressionEvaluator.applies(expr, target)) {
                        matchedTargets.add(target);
                    }
                } catch (ScriptException e) {
                    logger.error(e);
                    continue;
                }
            }
            MatchedCredentials match = new MatchedCredentials(expr, matchedTargets);
            result.add(match);
        }
        return result;
    }

    private Path getPersistedPath(String matchExpression) {
        return credentialsDir.resolve(
                String.format(
                        "%s.json",
                        base32.encodeAsString(matchExpression.getBytes(StandardCharsets.UTF_8))));
    }

    public static class MatchedCredentials {
        private final String matchExpression;
        private final Collection<ServiceRef> targets;

        MatchedCredentials(String matchExpression, Collection<ServiceRef> targets) {
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

    static class StoredCredentials {
        private final String matchExpression;
        private final Credentials credentials;

        StoredCredentials(String matchExpression, Credentials credentials) {
            this.matchExpression = matchExpression;
            this.credentials = credentials;
        }

        String getMatchExpression() {
            return this.matchExpression;
        }

        Credentials getCredentials() {
            return this.credentials;
        }

        @Override
        public int hashCode() {
            return Objects.hash(credentials, matchExpression);
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
            StoredCredentials other = (StoredCredentials) obj;
            return Objects.equals(credentials, other.credentials)
                    && Objects.equals(matchExpression, other.matchExpression);
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
}
