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
package io.cryostat.net.web.http.api.v2;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.cryostat.MainModule;
import io.cryostat.configuration.CredentialsManager;
import io.cryostat.core.log.Logger;
import io.cryostat.messaging.notifications.Notification;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.net.AuthManager;
import io.cryostat.net.security.ResourceAction;
import io.cryostat.net.web.http.HttpMimeType;
import io.cryostat.net.web.http.api.ApiVersion;
import io.cryostat.rules.MatchExpressionValidationException;
import io.cryostat.rules.Rule;
import io.cryostat.rules.RuleRegistry;
import io.cryostat.util.RuleDeserializer;

import com.google.gson.Gson;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
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

@ExtendWith(MockitoExtension.class)
class RulesPostHandlerTest {

    RulesPostHandler handler;
    @Mock AuthManager authManager;
    @Mock CredentialsManager credentialsManager;
    @Mock RuleRegistry ruleRegistry;
    @Mock NotificationFactory notificationFactory;
    @Mock Notification notification;
    @Mock Notification.Builder notificationBuilder;
    @Mock Logger logger;
    Gson gson = MainModule.provideGson(logger, new RuleDeserializer());

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
        Mockito.lenient().when(notificationFactory.createBuilder()).thenReturn(notificationBuilder);
        Mockito.lenient()
                .when(notificationBuilder.metaCategory(Mockito.any()))
                .thenReturn(notificationBuilder);
        Mockito.lenient()
                .when(notificationBuilder.metaType(Mockito.any(Notification.MetaType.class)))
                .thenReturn(notificationBuilder);
        Mockito.lenient()
                .when(notificationBuilder.metaType(Mockito.any(HttpMimeType.class)))
                .thenReturn(notificationBuilder);
        Mockito.lenient()
                .when(notificationBuilder.message(Mockito.any()))
                .thenReturn(notificationBuilder);
        Mockito.lenient().when(notificationBuilder.build()).thenReturn(notification);
        this.handler =
                new RulesPostHandler(
                        authManager,
                        credentialsManager,
                        ruleRegistry,
                        notificationFactory,
                        gson,
                        logger);
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
        void shouldHaveExpectedRequiredPermissions() {
            MatcherAssert.assertThat(
                    handler.resourceActions(),
                    Matchers.equalTo(
                            Set.of(
                                    ResourceAction.CREATE_RULE,
                                    ResourceAction.READ_TARGET,
                                    ResourceAction.CREATE_RECORDING,
                                    ResourceAction.UPDATE_RECORDING,
                                    ResourceAction.READ_TEMPLATE)));
        }

        @Test
        void shouldProduceJson() {
            MatcherAssert.assertThat(
                    handler.produces(), Matchers.equalTo(List.of(HttpMimeType.JSON)));
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
        }

        @Test
        void unknownMimeShouldThrow() {
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            headers.set(HttpHeaders.CONTENT_TYPE, "NOTAMIME");
            Mockito.when(params.getHeaders()).thenReturn(headers);
            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(params));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(415));
        }

        @Test
        void unknownFirstMimeShouldThrow() {
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            headers.set(HttpHeaders.CONTENT_TYPE, "NOTAMIME;text/plain");
            Mockito.when(params.getHeaders()).thenReturn(headers);
            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(params));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(415));
        }

        @Test
        void unsupportedFirstMimeShouldThrow() {
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            headers.set(HttpHeaders.CONTENT_TYPE, "text/plain;NOTAMIME");
            Mockito.when(params.getHeaders()).thenReturn(headers);
            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(params));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(415));
        }

        @ParameterizedTest
        @CsvSource(
                value = {
                    ",target.annotations.cryostat.JAVA_MAIN=='es.andrewazor.demo.Main',template=Continuous",
                    "fooRule,,template=Continuous",
                    "fooRule,target.annotations.cryostat.JAVA_MAIN=='es.andrewazor.demo.Main',",
                })
        void throwsIfRequiredFormAttributesBlank(
                String name, String matchExpression, String eventSpecifier) {
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            Mockito.when(params.getHeaders()).thenReturn(headers);
            headers.set(HttpHeaders.CONTENT_TYPE, HttpMimeType.MULTIPART_FORM.mime());
            MultiMap form = MultiMap.caseInsensitiveMultiMap();
            Mockito.when(params.getFormAttributes()).thenReturn(form);
            form.set(Rule.Attribute.NAME.getSerialKey(), name);
            form.set(Rule.Attribute.MATCH_EXPRESSION.getSerialKey(), matchExpression);
            form.set(Rule.Attribute.EVENT_SPECIFIER.getSerialKey(), eventSpecifier);

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(params));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "", "one", "|", "1.2",
                })
        void throwsIfOptionalIntegerAttributesNonInteger(String val) {
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            Mockito.when(params.getHeaders()).thenReturn(headers);
            headers.set(HttpHeaders.CONTENT_TYPE, HttpMimeType.MULTIPART_FORM.mime());
            MultiMap form = MultiMap.caseInsensitiveMultiMap();
            Mockito.when(params.getFormAttributes()).thenReturn(form);
            form.set(Rule.Attribute.NAME.getSerialKey(), "fooRule");
            form.set(
                    Rule.Attribute.MATCH_EXPRESSION.getSerialKey(),
                    "target.annotations.cryostat.JAVA_MAIN == 'someTarget'");
            form.set(Rule.Attribute.EVENT_SPECIFIER.getSerialKey(), "template=Something");
            form.set(Rule.Attribute.ARCHIVAL_PERIOD_SECONDS.getSerialKey(), val);

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(params));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
            MatcherAssert.assertThat(ex.getFailureReason(), Matchers.containsString(val));
        }

        @ParameterizedTest
        @ValueSource(strings = {"-10", "", "one", "|", "[1, 2, 3]"})
        void throwsIfOptionalJsonAttributesNegativeOrNonInteger(String val) {
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            Mockito.when(params.getHeaders()).thenReturn(headers);
            headers.set(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime());

            String invalidRule =
                    gson.toJson(
                            Map.of(
                                    "name", "Auto Rule ",
                                    "description", "AutoRulesIT automated rule",
                                    "eventSpecifier", "template=Continuous,type=TARGET",
                                    "matchExpression",
                                            "target.annotations.cryostat.JAVA_MAIN =="
                                                    + " 'es.andrewazor.demo.Main'",
                                    "archivalPeriodSeconds", val));
            Mockito.when(params.getBody()).thenReturn(invalidRule);

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(params));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
            MatcherAssert.assertThat(ex.getFailureReason(), Matchers.containsString(val));
        }

        @Test
        void throwsIfJsonBodyNull() {
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            Mockito.when(params.getHeaders()).thenReturn(headers);
            headers.set(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime());

            Mockito.when(params.getBody()).thenReturn(null);

            ApiException ex =
                    Assertions.assertThrows(ApiException.class, () -> handler.handle(params));
            MatcherAssert.assertThat(ex.getStatusCode(), Matchers.equalTo(400));
        }

        @Test
        void addsRuleWithFormAndReturnsResponse() throws MatchExpressionValidationException {
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            Mockito.when(params.getHeaders()).thenReturn(headers);
            headers.set(HttpHeaders.CONTENT_TYPE, HttpMimeType.MULTIPART_FORM.mime());
            MultiMap form = MultiMap.caseInsensitiveMultiMap();
            Mockito.when(params.getFormAttributes()).thenReturn(form);
            form.set(Rule.Attribute.NAME.getSerialKey(), "fooRule");
            form.set(Rule.Attribute.DESCRIPTION.getSerialKey(), "rule description");
            form.set(
                    Rule.Attribute.MATCH_EXPRESSION.getSerialKey(),
                    "target.annotations.cryostat.JAVA_MAIN == 'someTarget'");
            form.set(Rule.Attribute.EVENT_SPECIFIER.getSerialKey(), "template=Something");
            form.set(Rule.Attribute.ARCHIVAL_PERIOD_SECONDS.getSerialKey(), "60");
            form.set(Rule.Attribute.PRESERVED_ARCHIVES.getSerialKey(), "5");
            form.set(Rule.Attribute.MAX_AGE_SECONDS.getSerialKey(), "60");
            form.set(Rule.Attribute.MAX_SIZE_BYTES.getSerialKey(), "8192");
            form.set(Rule.Attribute.ENABLED.getSerialKey(), "true");

            IntermediateResponse<String> response = handler.handle(params);
            MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(201));
            MatcherAssert.assertThat(
                    response.getHeaders().get(HttpHeaders.LOCATION),
                    Matchers.equalTo("/api/v2/rules/fooRule"));
            MatcherAssert.assertThat(response.getBody(), Matchers.equalTo("fooRule"));

            Mockito.verify(notificationFactory).createBuilder();
            Mockito.verify(notificationBuilder).metaCategory("RuleCreated");
            Mockito.verify(notificationBuilder).metaType(HttpMimeType.JSON);
            Mockito.verify(notificationBuilder)
                    .message(
                            new Rule.Builder()
                                    .name("fooRule")
                                    .description("rule description")
                                    .matchExpression(
                                            "target.annotations.cryostat.JAVA_MAIN == 'someTarget'")
                                    .eventSpecifier("template=Something")
                                    .archivalPeriodSeconds(60)
                                    .preservedArchives(5)
                                    .maxAgeSeconds(60)
                                    .maxSizeBytes(8192)
                                    .enabled(true)
                                    .build());
            Mockito.verify(notificationBuilder).build();
            Mockito.verify(notification).send();
        }

        @Test
        void addsRuleWithJsonAndReturnsResponse() throws MatchExpressionValidationException {
            MultiMap headers = MultiMap.caseInsensitiveMultiMap();
            Mockito.when(params.getHeaders()).thenReturn(headers);
            headers.set(HttpHeaders.CONTENT_TYPE, HttpMimeType.JSON.mime());
            String json =
                    gson.toJson(
                            Map.of(
                                    "name",
                                    "Auto Rule",
                                    "description",
                                    "AutoRulesIT automated rule",
                                    "eventSpecifier",
                                    "template=Continuous,type=TARGET",
                                    "matchExpression",
                                    "target.annotations.cryostat.JAVA_MAIN =="
                                            + " 'io.cryostat.Cryostat'",
                                    "archivalPeriodSeconds",
                                    60,
                                    "preservedArchives",
                                    5,
                                    "maxAgeSeconds",
                                    60,
                                    "maxSizeBytes",
                                    8192,
                                    "enabled",
                                    true));
            Mockito.when(params.getBody()).thenReturn(json);

            IntermediateResponse<String> response = handler.handle(params);
            MatcherAssert.assertThat(response.getStatusCode(), Matchers.equalTo(201));
            MatcherAssert.assertThat(
                    response.getHeaders().get(HttpHeaders.LOCATION),
                    Matchers.equalTo("/api/v2/rules/Auto_Rule"));
            MatcherAssert.assertThat(response.getBody(), Matchers.equalTo("Auto_Rule"));

            Mockito.verify(notificationFactory).createBuilder();
            Mockito.verify(notificationBuilder).metaCategory("RuleCreated");
            Mockito.verify(notificationBuilder).metaType(HttpMimeType.JSON);
            Mockito.verify(notificationBuilder)
                    .message(
                            new Rule.Builder()
                                    .name("Auto Rule")
                                    .description("AutoRulesIT automated rule")
                                    .matchExpression(
                                            "target.annotations.cryostat.JAVA_MAIN =="
                                                    + " 'io.cryostat.Cryostat'")
                                    .eventSpecifier("template=Continuous,type=TARGET")
                                    .archivalPeriodSeconds(60)
                                    .preservedArchives(5)
                                    .maxAgeSeconds(60)
                                    .maxSizeBytes(8192)
                                    .enabled(true)
                                    .build());
            Mockito.verify(notificationBuilder).build();
            Mockito.verify(notification).send();
        }
    }
}
