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
package com.redhat.rhjmc.containerjfr.net.web.http.api.v2;

import java.io.IOException;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import com.google.gson.Gson;

import com.redhat.rhjmc.containerjfr.MainModule;
import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.net.AuthManager;
import com.redhat.rhjmc.containerjfr.net.web.http.HttpMimeType;
import com.redhat.rhjmc.containerjfr.net.web.http.api.ApiVersion;
import com.redhat.rhjmc.containerjfr.rules.Rule;
import com.redhat.rhjmc.containerjfr.rules.RuleRegistry;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;

@ExtendWith(MockitoExtension.class)
class RulesPostHandlerTest {

    RulesPostHandler handler;
    @Mock AuthManager authManager;
    @Mock RuleRegistry ruleRegistry;
    @Mock Logger logger;
    Gson gson = MainModule.provideGson(logger);

    @BeforeEach
    void setup() throws IOException {
        Mockito.lenient()
                .when(ruleRegistry.addRule(Mockito.any()))
                .thenAnswer(
                        new Answer<>() {
                            @Override
                            public Rule answer(InvocationOnMock invocation) throws Throwable {
                                return (Rule) invocation.getArgument(0);
                            }
                        });
        this.handler = new RulesPostHandler(authManager, ruleRegistry, gson, logger);
    }

    @Nested
    class BasicHandlerDefinition {
        @Test
        void shouldRequireAuthentication() {
            Assertions.assertTrue(handler.requiresAuthentication());
        }

        @Test
        void shouldBeV2Handler() {
            MatcherAssert.assertThat(handler.apiVersion(), Matchers.equalTo(ApiVersion.V2));
        }

        @Test
        void shouldBePOSTHandler() {
            MatcherAssert.assertThat(handler.httpMethod(), Matchers.equalTo(HttpMethod.POST));
        }

        @Test
        void shouldHaveExpectedApiPath() {
            MatcherAssert.assertThat(handler.path(), Matchers.equalTo("/api/v2/rules"));
        }

        @Test
        void shouldHavePlaintextMimeType() {
            MatcherAssert.assertThat(handler.mimeType(), Matchers.equalTo(HttpMimeType.PLAINTEXT));
        }

        @Test
        void shouldNotBeAsyncHandler() {
            Assertions.assertFalse(handler.isAsync());
        }

        @Test
        void shouldBeOrderedHandler() {
            Assertions.assertTrue(handler.isOrdered());
        }
    }

    @Nested
    class Requests {
        @Mock RequestParameters params;

        @Test
        void nullMimeShouldThrow() {
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            Mockito.when(params.getHeaders()).thenReturn(headers);
            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(params));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(415));
            MatcherAssert.assertThat(ex.getFailureReason(), Matchers.containsString("null"));
        }

        @Test
        void unknownMimeShouldThrow() {
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            headers.set(HttpHeaders.CONTENT_TYPE, "NOTAMIME");
            Mockito.when(params.getHeaders()).thenReturn(headers);
            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(params));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(415));
            MatcherAssert.assertThat(ex.getFailureReason(), Matchers.containsString("NOTAMIME"));
        }

        @Test
        void unknownFirstMimeShouldThrow() {
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            headers.set(HttpHeaders.CONTENT_TYPE, "NOTAMIME;text/plain");
            Mockito.when(params.getHeaders()).thenReturn(headers);
            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(params));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(415));
            MatcherAssert.assertThat(ex.getFailureReason(), Matchers.containsString("NOTAMIME"));
        }

        @Test
        void unsupportedFirstMimeShouldThrow() {
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            headers.set(HttpHeaders.CONTENT_TYPE, "text/plain;NOTAMIME");
            Mockito.when(params.getHeaders()).thenReturn(headers);
            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(params));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(415));
            MatcherAssert.assertThat(ex.getFailureReason(), Matchers.containsString("text/plain"));
        }

        @ParameterizedTest
        @CsvSource(
                value = {
                    ",fooTarget,template=Continuous",
                    "fooRule,,template=Continuous",
                    "fooRule,fooTarget,",
                })
        void throwsIfRequiredFormAttributesBlank(
                String name, String targetAlias, String eventSpecifier) {
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            Mockito.when(params.getHeaders()).thenReturn(headers);
            headers.set(HttpHeaders.CONTENT_TYPE, "multipart/form-data");
            MultiMap form = MultiMap.caseInsensitiveMultiMap();
            Mockito.when(params.getFormAttributes()).thenReturn(form);
            form.set("name", name);
            form.set("targetAlias", targetAlias);
            form.set("eventSpecifier", eventSpecifier);

            Assertions.assertThrows(NullPointerException.class, () -> handler.handle(params));
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "archivalPeriodSeconds",
                    "preservedArchives",
                    "maxAgeSeconds",
                    "maxSizeBytes",
                })
        void throwsIfOptionalIntegerAttributesNegative(String attr) {
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            Mockito.when(params.getHeaders()).thenReturn(headers);
            headers.set(HttpHeaders.CONTENT_TYPE, "multipart/form-data");
            MultiMap form = MultiMap.caseInsensitiveMultiMap();
            Mockito.when(params.getFormAttributes()).thenReturn(form);
            form.set("name", "fooRule");
            form.set("targetAlias", "someTarget");
            form.set("eventSpecifier", "template=Something");
            form.set(attr, "-1");

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(params));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
            MatcherAssert.assertThat(ex.getFailureReason(), Matchers.containsString(attr));
            MatcherAssert.assertThat(ex.getFailureReason(), Matchers.containsString("-1"));
        }

        @Test
        void addsRuleAndReturnsResponse() {
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            Mockito.when(params.getHeaders()).thenReturn(headers);
            headers.set(HttpHeaders.CONTENT_TYPE, "multipart/form-data");
            MultiMap form = MultiMap.caseInsensitiveMultiMap();
            Mockito.when(params.getFormAttributes()).thenReturn(form);
            form.set("name", "fooRule");
            form.set("description", "rule description");
            form.set("targetAlias", "someTarget");
            form.set("eventSpecifier", "template=Something");
            form.set("archivalPeriodSeconds", "60");
            form.set("preservedArchives", "5");
            form.set("maxAgeSeconds", "60");
            form.set("maxSizeBytes", "8192");

            IntermediateResponse<String> response = handler.handle(params);
            MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(201));
            MatcherAssert.assertThat(
                    response.getHeaders().get(HttpHeaders.LOCATION),
                    Matchers.equalTo("/api/v2/rules/fooRule"));
            MatcherAssert.assertThat(response.getBody(), Matchers.equalTo("fooRule"));
        }
    }
}
