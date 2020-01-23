package com.redhat.rhjmc.containerjfr.net;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;

class BasicAuthManager extends AbstractAuthManager {

    static final String USER_PROPERTIES_FILENAME = "container-jfr-users.properties";

    private final FileSystem fs;
    private final Properties users;

    // TODO inject FileSystem, but this also means changing the assumed constructor signature
    // TODO salted hashes
    BasicAuthManager(Logger logger, FileSystem fs) {
        super(logger);
        this.fs = fs;
        this.users = new Properties();
        loadConfig();
    }

    @Override
    public Future<Boolean> validateToken(Supplier<String> tokenProvider) {
        String credentials = tokenProvider.get();
        Pattern credentialsPattern = Pattern.compile("([\\S]+):([\\S]+)");
        Matcher matcher = credentialsPattern.matcher(credentials);
        if (!matcher.matches()) {
            return CompletableFuture.completedFuture(false);
        }
        String user = matcher.group(1);
        String pass = matcher.group(2);
        String passHashHex = DigestUtils.sha256Hex(pass);
        return CompletableFuture.completedFuture(
                Objects.equals(users.getProperty(user), passHashHex));
    }

    @Override
    public Future<Boolean> validateHttpHeader(Supplier<String> headerProvider) {
        String authorization = headerProvider.get();
        if (StringUtils.isBlank(authorization)) {
            return CompletableFuture.completedFuture(false);
        }
        Pattern basicPattern = Pattern.compile("Basic[\\s]+(.*)");
        Matcher matcher = basicPattern.matcher(authorization);
        if (!matcher.matches()) {
            return CompletableFuture.completedFuture(false);
        }
        String b64 = matcher.group(1);
        try {
            String decoded =
                    new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8).trim();
            return validateToken(() -> decoded);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(false);
        }
    }

    @Override
    public Future<Boolean> validateWebSocketSubProtocol(Supplier<String> subProtocolProvider) {
        String subprotocol = subProtocolProvider.get();
        if (StringUtils.isBlank(subprotocol)) {
            return CompletableFuture.completedFuture(false);
        }
        Pattern pattern =
                Pattern.compile(
                        "basic\\.authorization\\.containerjfr\\.([\\S]+)",
                        Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(subprotocol);
        if (!matcher.matches()) {
            return CompletableFuture.completedFuture(false);
        }
        String b64 = matcher.group(1);
        try {
            String decoded =
                    new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8).trim();
            return validateToken(() -> decoded);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(false);
        }
    }

    private void loadConfig() {
        Path properties = fs.pathOf(System.getProperty("user.home"), USER_PROPERTIES_FILENAME);
        if (!fs.exists(properties)) {
            logger.warn(String.format("User properties file \"%s\" does not exist", properties));
            return;
        }
        if (!fs.isRegularFile(properties)) {
            logger.warn(String.format("User properties path \"%s\" is not a file", properties));
            return;
        }
        if (!fs.isReadable(properties)) {
            logger.warn(String.format("User properties file \"%s\" is not readable", properties));
            return;
        }
        try (InputStream s = fs.newInputStream(properties)) {
            users.load(s);
        } catch (IOException e) {
            logger.error(e);
        }
    }
}
