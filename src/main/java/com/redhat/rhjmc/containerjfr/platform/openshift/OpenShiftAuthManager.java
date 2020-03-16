package com.redhat.rhjmc.containerjfr.platform.openshift;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.net.AbstractAuthManager;
import com.redhat.rhjmc.containerjfr.net.AuthenticationScheme;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.OpenShiftConfigBuilder;

public class OpenShiftAuthManager extends AbstractAuthManager {

    public OpenShiftAuthManager(Logger logger, FileSystem fs) {
        super(logger, fs);
    }

    @Override
    public AuthenticationScheme getScheme() {
        return AuthenticationScheme.BEARER;
    }

    @Override
    public Future<Boolean> validateToken(Supplier<String> tokenProvider) {
        String token = tokenProvider.get();
        if (StringUtils.isBlank(token)) {
            return CompletableFuture.completedFuture(false);
        }
        return CompletableFuture.supplyAsync(
                        () -> {
                            try (OpenShiftClient authClient =
                                    new DefaultOpenShiftClient(
                                            new OpenShiftConfigBuilder()
                                                    .withOauthToken(token)
                                                    .build())) {
                                // only an authenticated user should be allowed to list routes
                                // in the namespace
                                // TODO find a better way to authenticate tokens
                                authClient.routes().inNamespace(getNamespace()).list();
                                return true;
                            } catch (KubernetesClientException e) {
                                logger.info(e);
                            } catch (Exception e) {
                                logger.error(e);
                            }
                            return false;
                        })
                .orTimeout(15, TimeUnit.SECONDS);
    }

    @Override
    public Future<Boolean> validateHttpHeader(Supplier<String> headerProvider) {
        String authorization = headerProvider.get();
        if (StringUtils.isBlank(authorization)) {
            return CompletableFuture.completedFuture(false);
        }
        Pattern bearerPattern = Pattern.compile("Bearer[\\s]+(.*)");
        Matcher matcher = bearerPattern.matcher(authorization);
        if (!matcher.matches()) {
            return CompletableFuture.completedFuture(false);
        }
        return validateToken(() -> matcher.group(1));
    }

    @Override
    public Future<Boolean> validateWebSocketSubProtocol(Supplier<String> subProtocolProvider) {
        String subprotocol = subProtocolProvider.get();
        if (StringUtils.isBlank(subprotocol)) {
            return CompletableFuture.completedFuture(false);
        }
        Pattern pattern =
                Pattern.compile(
                        "base64url\\.bearer\\.authorization\\.containerjfr\\.([\\S]+)",
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

    @SuppressFBWarnings(
            value = "DMI_HARDCODED_ABSOLUTE_FILENAME",
            justification = "Kubernetes namespace file path is well-known and absolute")
    private String getNamespace() throws IOException {
        return fs.readFile(Paths.get(Config.KUBERNETES_NAMESPACE_PATH))
                .lines()
                .filter(StringUtils::isNotBlank)
                .findFirst()
                .get();
    }
}
