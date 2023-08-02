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
package io.cryostat.net;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.net.security.ResourceAction;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

class BasicAuthManager extends AbstractAuthManager {

    static final String USER_PROPERTIES_FILENAME = "cryostat-users.properties";

    private final FileSystem fs;
    private final Path confDir;
    private final Properties users;
    private volatile boolean configLoaded = false;

    // TODO salted hashes
    BasicAuthManager(Logger logger, FileSystem fs, Path confDir) {
        super(logger);
        this.fs = fs;
        this.confDir = confDir;
        this.users = new Properties();
    }

    @Override
    public AuthenticationScheme getScheme() {
        return AuthenticationScheme.BASIC;
    }

    @Override
    public Future<UserInfo> getUserInfo(Supplier<String> httpHeaderProvider) {
        if (!configLoaded) {
            this.loadConfig();
        }
        String credentials = getCredentialsFromHeader(httpHeaderProvider.get());
        Pair<String, String> splitCredentials = splitCredentials(credentials);
        if (splitCredentials == null) {
            return CompletableFuture.failedFuture(new UnknownUserException(null));
        }
        String user = splitCredentials.getLeft();
        if (!users.containsKey(user)) {
            return CompletableFuture.failedFuture(new UnknownUserException(user));
        }
        return CompletableFuture.completedFuture(new UserInfo(user));
    }

    @Override
    public Optional<String> getLoginRedirectUrl(
            Supplier<String> headerProvider, Set<ResourceAction> resourceActions) {
        return Optional.empty();
    }

    @Override
    public Future<Boolean> validateToken(
            Supplier<String> tokenProvider, Set<ResourceAction> resourceActions) {
        if (!configLoaded) {
            this.loadConfig();
        }
        String credentials = tokenProvider.get();
        Pair<String, String> splitCredentials = splitCredentials(credentials);
        if (splitCredentials == null) {
            return CompletableFuture.completedFuture(false);
        }
        String user = splitCredentials.getLeft();
        String pass = splitCredentials.getRight();
        String passHashHex = DigestUtils.sha256Hex(pass);
        boolean granted = Objects.equals(users.getProperty(user), passHashHex);
        // FIXME actually implement this
        resourceActions.forEach(
                action ->
                        logger.trace(
                                "user {} granted {} {}",
                                user,
                                action.getVerb(),
                                action.getResource()));
        return CompletableFuture.completedFuture(granted);
    }

    @Override
    public Future<Boolean> validateHttpHeader(
            Supplier<String> headerProvider, Set<ResourceAction> resourceActions) {
        String decoded = getCredentialsFromHeader(headerProvider.get());
        if (decoded == null) {
            return CompletableFuture.completedFuture(false);
        }
        return validateToken(() -> decoded, resourceActions);
    }

    @Override
    public Future<Boolean> validateWebSocketSubProtocol(
            Supplier<String> subProtocolProvider, Set<ResourceAction> resourceActions) {
        String subprotocol = subProtocolProvider.get();
        if (StringUtils.isBlank(subprotocol)) {
            return CompletableFuture.completedFuture(false);
        }
        Pattern pattern =
                Pattern.compile(
                        "basic\\.authorization\\.cryostat\\.([\\S]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(subprotocol);
        if (!matcher.matches()) {
            return CompletableFuture.completedFuture(false);
        }
        String b64 = matcher.group(1);
        try {
            String decoded =
                    new String(Base64.getUrlDecoder().decode(b64), StandardCharsets.UTF_8).trim();
            return validateToken(() -> decoded, resourceActions);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(false);
        }
    }

    @Override
    public Optional<String> logout(Supplier<String> httpHeaderProvider) {
        return Optional.empty();
    }

    private Pair<String, String> splitCredentials(String credentials) {
        if (credentials == null) {
            return null;
        }
        Pattern credentialsPattern = Pattern.compile("([\\S]+):([\\S]+)");
        Matcher matcher = credentialsPattern.matcher(credentials);
        if (!matcher.matches()) {
            return null;
        }
        String user = matcher.group(1);
        String pass = matcher.group(2);
        return Pair.of(user, pass);
    }

    private String getCredentialsFromHeader(String rawHttpHeader) {
        if (StringUtils.isBlank(rawHttpHeader)) {
            return null;
        }
        Pattern basicPattern = Pattern.compile("Basic[\\s]+(.*)");
        Matcher matcher = basicPattern.matcher(rawHttpHeader);
        if (!matcher.matches()) {
            return null;
        }
        String b64 = matcher.group(1);
        try {
            return new String(Base64.getUrlDecoder().decode(b64), StandardCharsets.UTF_8).trim();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    synchronized void loadConfig() {
        Path properties = confDir.resolve(USER_PROPERTIES_FILENAME);
        if (!fs.exists(properties)) {
            logger.warn("User properties file \"{}\" does not exist", properties);
            return;
        }
        if (!fs.isRegularFile(properties)) {
            logger.warn("User properties path \"{}\" is not a file", properties);
            return;
        }
        if (!fs.isReadable(properties)) {
            logger.warn("User properties file \"{}\" is not readable", properties);
            return;
        }
        try (Reader br = fs.readFile(properties)) {
            users.load(br);
            this.configLoaded = true;
        } catch (IOException e) {
            logger.error(e);
        }
    }
}
