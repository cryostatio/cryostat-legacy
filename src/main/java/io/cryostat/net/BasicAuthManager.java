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
import io.cryostat.recordings.RecordingMetadataManager.SecurityContext;

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
            Supplier<String> tokenProvider,
            SecurityContext securityContext,
            Set<ResourceAction> resourceActions) {
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
                                "user '{}' granted [{} {}] in context '{}'",
                                user,
                                action.getVerb(),
                                action.getResource(),
                                securityContext));
        return CompletableFuture.completedFuture(granted);
    }

    @Override
    public Future<Boolean> validateHttpHeader(
            Supplier<String> headerProvider,
            SecurityContext securityContext,
            Set<ResourceAction> resourceActions) {
        String decoded = getCredentialsFromHeader(headerProvider.get());
        if (decoded == null) {
            return CompletableFuture.completedFuture(false);
        }
        return validateToken(() -> decoded, securityContext, resourceActions);
    }

    @Override
    public Future<Boolean> validateWebSocketSubProtocol(
            Supplier<String> subProtocolProvider,
            SecurityContext securityContext,
            Set<ResourceAction> resourceActions) {
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
            return validateToken(() -> decoded, securityContext, resourceActions);
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
