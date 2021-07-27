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

import java.io.BufferedReader;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import io.cryostat.MainModule;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.net.OpenShiftAuthManager.PermissionDeniedException;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.security.ResourceType;
import io.cryostat.net.security.ResourceVerb;

import com.google.gson.Gson;
import io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectAccessReview;
import io.fabric8.kubernetes.api.model.authorization.v1.SelfSubjectAccessReviewBuilder;
import io.fabric8.openshift.client.OpenShiftClient;
import io.fabric8.openshift.client.server.mock.EnableOpenShiftMockClient;
import io.fabric8.openshift.client.server.mock.OpenShiftMockServer;
import io.fabric8.openshift.client.server.mock.OpenShiftMockServerExtension;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith({MockitoExtension.class, OpenShiftMockServerExtension.class})
@EnableOpenShiftMockClient(https = false, crud = false)
class OpenShiftAuthManagerTest {

    static final String SUBJECT_REVIEW_API_PATH =
            "/apis/authorization.k8s.io/v1/selfsubjectaccessreviews";
    static final String NAMESPACE_FS_PATH =
            "/var/run/secrets/kubernetes.io/serviceaccount/namespace";

    OpenShiftAuthManager mgr;
    @Mock FileSystem fs;
    @Mock Logger logger;
    OpenShiftClient client;
    OpenShiftMockServer server;
    TokenProvider tokenProvider;
    Gson gson = MainModule.provideGson(logger);

    @BeforeEach
    void setup() {
        client = Mockito.spy(client);
        tokenProvider = new TokenProvider(client);
        mgr = new OpenShiftAuthManager(logger, fs, tokenProvider);
    }

    @Test
    void shouldHandleBearerAuthentication() {
        MatcherAssert.assertThat(mgr.getScheme(), Matchers.equalTo(AuthenticationScheme.BEARER));
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldNotValidateBlankToken(String tok) throws Exception {
        MatcherAssert.assertThat(
                mgr.validateToken(() -> tok, ResourceAction.NONE).get(), Matchers.is(false));
    }

    @Test
    void shouldValidateTokenWithNoPermissions() throws Exception {
        MatcherAssert.assertThat(
                mgr.validateToken(() -> "token", ResourceAction.NONE).get(), Matchers.is(true));
    }

    @Test
    void shouldValidateTokenWithSufficientPermissions() throws Exception {
        SelfSubjectAccessReview accessReview =
                new SelfSubjectAccessReviewBuilder()
                        .withNewStatus()
                        .withAllowed(true)
                        .endStatus()
                        .build();
        server.expect()
                .post()
                .withPath(SUBJECT_REVIEW_API_PATH)
                .andReturn(HttpURLConnection.HTTP_CREATED, accessReview)
                .once();

        Mockito.when(fs.readFile(Mockito.any()))
                .thenReturn(new BufferedReader(new StringReader("mynamespace")));

        MatcherAssert.assertThat(
                mgr.validateToken(() -> "token", Set.of(ResourceAction.READ_TARGET)).get(),
                Matchers.is(true));

        ArgumentCaptor<Path> nsPathCaptor = ArgumentCaptor.forClass(Path.class);
        Mockito.verify(fs).readFile(nsPathCaptor.capture());
        MatcherAssert.assertThat(
                nsPathCaptor.getValue(), Matchers.equalTo(Paths.get(NAMESPACE_FS_PATH)));
    }

    @Test
    void shouldNotValidateTokenWithSufficientPermissions() throws Exception {
        SelfSubjectAccessReview accessReview =
                new SelfSubjectAccessReviewBuilder()
                        .withNewStatus()
                        .withAllowed(false)
                        .endStatus()
                        .build();
        server.expect()
                .post()
                .withPath(SUBJECT_REVIEW_API_PATH)
                .andReturn(HttpURLConnection.HTTP_CREATED, accessReview)
                .once();

        String namespace = "mynamespace";
        Mockito.when(fs.readFile(Mockito.any()))
                .thenReturn(new BufferedReader(new StringReader(namespace)));

        ExecutionException ee =
                Assertions.assertThrows(
                        ExecutionException.class,
                        () ->
                                mgr.validateToken(() -> "token", Set.of(ResourceAction.READ_TARGET))
                                        .get());
        MatcherAssert.assertThat(
                ExceptionUtils.getRootCause(ee),
                Matchers.instanceOf(PermissionDeniedException.class));
        PermissionDeniedException pde = (PermissionDeniedException) ExceptionUtils.getRootCause(ee);
        MatcherAssert.assertThat(pde.getNamespace(), Matchers.equalTo(namespace));
        MatcherAssert.assertThat(pde.getResourceType(), Matchers.equalTo("flightrecorders"));
        MatcherAssert.assertThat(pde.getVerb(), Matchers.equalTo("get"));

        ArgumentCaptor<Path> nsPathCaptor = ArgumentCaptor.forClass(Path.class);
        Mockito.verify(fs).readFile(nsPathCaptor.capture());
        MatcherAssert.assertThat(
                nsPathCaptor.getValue(), Matchers.equalTo(Paths.get(NAMESPACE_FS_PATH)));
    }

    @ParameterizedTest
    @EnumSource(mode = EnumSource.Mode.MATCH_ANY, names = "^([a-zA-Z]+_(RECORDING|TARGET))$")
    void shouldValidateExpectedPermissionsPerSecuredResource(ResourceAction resourceAction)
            throws Exception {
        Mockito.when(fs.readFile(Mockito.any()))
                .thenReturn(new BufferedReader(new StringReader("mynamespace")));

        SelfSubjectAccessReview accessReview =
                new SelfSubjectAccessReviewBuilder()
                        .withNewStatus()
                        .withAllowed(true)
                        .endStatus()
                        .build();
        server.expect()
                .post()
                .withPath(SUBJECT_REVIEW_API_PATH)
                .andReturn(HttpURLConnection.HTTP_CREATED, accessReview)
                .once();

        String token = "abcd1234";
        MatcherAssert.assertThat(
                mgr.validateToken(() -> token, Set.of(resourceAction)).get(), Matchers.is(true));

        RecordedRequest req = server.getLastRequest();
        MatcherAssert.assertThat(req.getPath(), Matchers.equalTo(SUBJECT_REVIEW_API_PATH));
        MatcherAssert.assertThat(tokenProvider.token, Matchers.equalTo(token));
        MatcherAssert.assertThat(req.getMethod(), Matchers.equalTo("POST"));

        SelfSubjectAccessReview body =
                gson.fromJson(req.getBody().readUtf8(), SelfSubjectAccessReview.class);

        String expectedVerb;
        if (resourceAction.getVerb() == ResourceVerb.CREATE) {
            expectedVerb = "create";
        } else if (resourceAction.getVerb() == ResourceVerb.READ) {
            expectedVerb = "get";
        } else if (resourceAction.getVerb() == ResourceVerb.UPDATE) {
            expectedVerb = "patch";
        } else if (resourceAction.getVerb() == ResourceVerb.DELETE) {
            expectedVerb = "delete";
        } else {
            throw new IllegalArgumentException(resourceAction.getVerb().toString());
        }
        MatcherAssert.assertThat(
                body.getSpec().getResourceAttributes().getVerb(), Matchers.equalTo(expectedVerb));

        String expectedResource;
        if (resourceAction.getResource() == ResourceType.TARGET) {
            expectedResource = "flightrecorders";
        } else if (resourceAction.getResource() == ResourceType.RECORDING) {
            expectedResource = "recordings";
        } else {
            throw new IllegalArgumentException(resourceAction.getResource().toString());
        }
        MatcherAssert.assertThat(
                body.getSpec().getResourceAttributes().getResource(),
                Matchers.equalTo(expectedResource));
    }

    @ParameterizedTest
    @EnumSource(
            mode = EnumSource.Mode.MATCH_ALL,
            names = {"^[a-zA-Z]+_(?!TARGET).*$", "^[a-zA-Z]+_(?!RECORDING).*$"})
    void shouldValidateExpectedPermissionsForUnsecuredResources(ResourceAction resourceAction)
            throws Exception {
        MatcherAssert.assertThat(
                mgr.validateToken(() -> "token", Set.of(resourceAction)).get(), Matchers.is(true));
    }

    private static class TokenProvider implements Function<String, OpenShiftClient> {

        private final OpenShiftClient osc;
        String token;

        TokenProvider(OpenShiftClient osc) {
            this.osc = osc;
        }

        @Override
        public OpenShiftClient apply(String token) {
            if (this.token != null) {
                throw new IllegalStateException("Token was already set!");
            }
            this.token = token;
            return osc;
        }
    }
}
