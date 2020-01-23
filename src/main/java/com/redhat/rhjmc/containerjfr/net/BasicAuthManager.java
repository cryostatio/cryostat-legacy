package com.redhat.rhjmc.containerjfr.net;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
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

import org.apache.commons.lang3.StringUtils;

class BasicAuthManager extends AbstractAuthManager {

    static final String USER_PROPERTIES_FILENAME = "container-jfr-users.properties";

    private final Properties users;
    private final FileSystem fs;

    // TODO inject FileSystem, but this also means changing the assumed constructor signature
    // TODO config file should contain password hashes, not cleartext passwords
    BasicAuthManager(Logger logger) {
        super(logger);
        this.users = new Properties();
        this.fs = new FileSystem();
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
        return CompletableFuture.completedFuture(Objects.equals(users.getProperty(user), pass));
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
            String decoded = new String(Base64.getDecoder().decode(b64)).trim();
            return validateToken(() -> decoded);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.completedFuture(false);
        }
    }

    @Override
    public Future<Boolean> validateWebSocketSubProtocol(Supplier<String> subProtocolProvider) {
        String subprotocol = subProtocolProvider.get();
        if (subprotocol == null) {
            return null;
        }
        Pattern pattern =
                Pattern.compile(
                        "basic\\.authorization\\.containerjfr\\.([\\S]+)",
                        Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(subprotocol);
        if (!matcher.matches()) {
            return null;
        }
        String b64 = matcher.group(1);
        try {
            String decoded = new String(Base64.getDecoder().decode(b64)).trim();
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
        // TODO abstract isRegularFile into FileSystem
        if (!Files.isRegularFile(properties)) {
            logger.warn(String.format("User properties path \"%s\" is not a file", properties));
            return;
        }
        // TODO abstract isReadable into FileSystem
        if (!Files.isReadable(properties)) {
            logger.warn(String.format("User properties file \"%s\" is not readable", properties));
            return;
        }
        try (InputStream s = Files.newInputStream(properties)) {
            users.load(s);
        } catch (IOException e) {
            logger.error(e);
        }
    }
}
