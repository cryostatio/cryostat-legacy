/*-
 * #%L
 * Container JFR
 * %%
 * Copyright (C) 2020 Red Hat, Inc.
 * %%
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
 * #L%
 */
package io.cryostat.configuration;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import io.cryostat.core.log.Logger;
import io.cryostat.core.net.Credentials;
import io.cryostat.core.sys.FileSystem;

import com.google.gson.Gson;

public class CredentialsManager {

    private final Path credentialsDir;
    private final FileSystem fs;
    private final Gson gson;
    private final Logger logger;

    private final Map<String, Credentials> credentialsMap;

    CredentialsManager(Path credentialsDir, FileSystem fs, Gson gson, Logger logger) {
        this.credentialsDir = credentialsDir;
        this.fs = fs;
        this.gson = gson;
        this.logger = logger;
        this.credentialsMap = new HashMap<>();
    }

    public void load() throws IOException {
        this.fs.listDirectoryChildren(credentialsDir).stream()
                .peek(n -> logger.trace("Credentials file: " + n))
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
                .forEach(sc -> credentialsMap.put(sc.getTargetId(), sc.getCredentials()));
    }

    public boolean addCredentials(String targetId, Credentials credentials) throws IOException {
        return addCredentials(targetId, credentials, false);
    }

    // FIXME `persist` should not be a parameter here but rather a Strategy selected by ex. env var,
    // with corresponding backing storage either in-memory or on-disk (with in-memory cache?)
    boolean addCredentials(String targetId, Credentials credentials, boolean persist)
            throws IOException {
        boolean replaced = credentialsMap.containsKey(targetId);
        credentialsMap.put(targetId, credentials);
        if (persist) {
            fs.writeString(
                    getPersistedPath(targetId),
                    gson.toJson(new StoredCredentials(targetId, credentials)),
                    StandardOpenOption.WRITE,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            // FIXME abstract setPosixFilePermissions into FileSystem and uncomment this
            // TODO do we need to secure these file contents further than simply applying owner-only
            // permissions? Is it possible for other containers or processes to read target
            // credentials
            // in the mounted volume?
            // Files.setPosixFilePermissions(destination,
            //         PosixFilePermissions.asFileAttribute(Set.of(PosixFilePermission.OWNER_READ,
            //                 PosixFilePermission.OWNER_WRITE)));
        }
        return replaced;
    }

    public void removeCredentials(String targetId) throws IOException {
        this.credentialsMap.remove(targetId);
        fs.deleteIfExists(getPersistedPath(targetId));
    }

    public Credentials getCredentials(String targetId) {
        return this.credentialsMap.get(targetId);
    }

    private Path getPersistedPath(String targetId) {
        return credentialsDir.resolve(String.format("%d.json", targetId.hashCode()));
    }

    public static class StoredCredentials {
        private final String targetId;
        private final Credentials credentials;

        StoredCredentials(String targetId, Credentials credentials) {
            this.targetId = targetId;
            this.credentials = credentials;
        }

        public String getTargetId() {
            return this.targetId;
        }

        public Credentials getCredentials() {
            return this.credentials;
        }
    }
}
