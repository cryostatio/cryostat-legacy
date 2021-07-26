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
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.ResourceType;
import io.cryostat.net.security.ResourceVerb;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectAccessReviewBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.OpenShiftConfigBuilder;
import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import org.apache.commons.lang3.StringUtils;

public class OpenShiftAuthManager extends AbstractAuthManager {

    private static final String PERMISSION_NOT_REQUIRED = "PERMISSION_NOT_REQUIRED";

    private final FileSystem fs;

    public OpenShiftAuthManager(Logger logger, FileSystem fs) {
        super(logger);
        this.fs = fs;
    }

    @Override
    public AuthenticationScheme getScheme() {
        return AuthenticationScheme.BEARER;
    }

    @Override
    public Future<Boolean> validateToken(
            Supplier<String> tokenProvider, Set<ResourceAction> resourceActions) {
        String token = tokenProvider.get();
        if (StringUtils.isBlank(token)) {
            return CompletableFuture.completedFuture(false);
        }

        try (OpenShiftClient authClient =
                new DefaultOpenShiftClient(
                        new OpenShiftConfigBuilder().withOauthToken(token).build())) {
            List<CompletableFuture<Boolean>> results =
                    resourceActions
                            .parallelStream()
                            .map(
                                    resourceAction -> {
                                        try {
                                            return CompletableFuture.<Boolean>completedFuture(
                                                    validateToken(authClient, resourceAction));
                                        } catch (IOException | PermissionDeniedException e) {
                                            return CompletableFuture.<Boolean>failedFuture(e);
                                        }
                                    })
                            .collect(Collectors.toList());

            CompletableFuture.allOf(results.toArray(new CompletableFuture[0]))
                    .get(15, TimeUnit.SECONDS);
            // if we get here then all requests were successful, otherwise an exception was thrown
            // on get() above
            boolean granted =
                    results.stream()
                            .map(
                                    result -> {
                                        try {
                                            return result.get();
                                        } catch (InterruptedException | ExecutionException e) {
                                            logger.error(e);
                                            return false;
                                        }
                                    })
                            .reduce(Boolean::logicalAnd)
                            // if the request set was empty, grant permission by default
                            .orElse(true);
            return CompletableFuture.completedFuture(granted);
        } catch (KubernetesClientException e) {
            logger.info(e);
            return CompletableFuture.failedFuture(e);
        } catch (Exception e) {
            logger.error(e);
            return CompletableFuture.failedFuture(e);
        }
    }

    private boolean validateToken(OpenShiftClient authClient, ResourceAction resourceAction)
            throws IOException, PermissionDeniedException {
        AuthRequest evt = new AuthRequest();
        evt.begin();

        try {
            String group = "operator.cryostat.io";
            String resource = map(resourceAction.getResource());
            String verb = map(resourceAction.getVerb());
            if (PERMISSION_NOT_REQUIRED.equals(resource)) {
                return true;
            }
            String namespace = getNamespace();
            var accessReview =
                    new SelfSubjectAccessReviewBuilder()
                            .withNewSpec()
                            .withNewResourceAttributes()
                            .withNamespace(namespace)
                            .withGroup(group)
                            .withResource(resource)
                            .withVerb(verb)
                            .endResourceAttributes()
                            .endSpec()
                            .build();
            var response =
                    authClient.authorization().v1().selfSubjectAccessReview().create(accessReview);
            boolean allowed = response.getStatus().getAllowed();
            evt.setRequestSuccessful(true);
            if (allowed) {
                return true;
            } else {
                throw new PermissionDeniedException(
                        namespace, group, resource, verb, response.getStatus().getReason());
            }
        } finally {
            if (evt.shouldCommit()) {
                evt.end();
                evt.commit();
            }
        }
    }

    @Name("io.cryostat.net.OpenShiftAuthManager.AuthRequest")
    @Label("AuthRequest")
    @Category("Cryostat")
    @SuppressFBWarnings(
            value = "URF_UNREAD_FIELD",
            justification = "Event fields are recorded with JFR instead of accessed directly")
    public static class AuthRequest extends Event {

        boolean requestSuccessful;

        public AuthRequest() {
            this.requestSuccessful = false;
        }

        public void setRequestSuccessful(boolean requestSuccessful) {
            this.requestSuccessful = requestSuccessful;
        }
    }

    @Override
    public Future<Boolean> validateHttpHeader(
            Supplier<String> headerProvider, Set<ResourceAction> resourceActions) {
        String authorization = headerProvider.get();
        if (StringUtils.isBlank(authorization)) {
            return CompletableFuture.completedFuture(false);
        }
        Pattern bearerPattern = Pattern.compile("Bearer[\\s]+(.*)");
        Matcher matcher = bearerPattern.matcher(authorization);
        if (!matcher.matches()) {
            return CompletableFuture.completedFuture(false);
        }
        return validateToken(() -> matcher.group(1), resourceActions);
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
                        "base64url\\.bearer\\.authorization\\.cryostat\\.([\\S]+)",
                        Pattern.CASE_INSENSITIVE);
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

    static String map(ResourceType resource) {
        switch (resource) {
            case TARGET:
                return "flightrecorders";
            case RECORDING:
                return "recordings";
            case TEMPLATE:
            case REPORT:
            case CREDENTIALS:
            case RULE:
            case CERTIFICATE:
            default:
                return PERMISSION_NOT_REQUIRED;
        }
    }

    static String map(ResourceVerb verb) {
        switch (verb) {
            case CREATE:
                return "create";
            case READ:
                return "get";
            case UPDATE:
                return "patch";
            case DELETE:
                return "delete";
            default:
                throw new IllegalArgumentException(
                        String.format("Unknown resource verb \"%s\"", verb));
        }
    }

    @SuppressWarnings("serial")
    public static class PermissionDeniedException extends Exception {
        public PermissionDeniedException(
                String namespace, String group, String resource, String verb, String reason) {
            super(
                    String.format(
                            "Requesting client in namespace \"%s\" cannot %s %s.%s: %s",
                            namespace, verb, resource, group, reason));
        }
    }
}
