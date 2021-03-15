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
package com.redhat.rhjmc.containerjfr.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;

@ExtendWith(MockitoExtension.class)
class BasicAuthManagerTest {

    BasicAuthManager mgr;
    @Mock Logger logger;
    @Mock FileSystem fs;

    @BeforeEach
    void setup() {
        mgr = new BasicAuthManager(logger, fs);
    }

    @Nested
    class ConfigLoadingTest {
        @Test
        void shouldWarnWhenPropertiesNotFound() throws Exception {
            mgr.loadConfig();

            ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object> objectCaptor = ArgumentCaptor.forClass(Object.class);
            Mockito.verify(logger).warn(messageCaptor.capture(), objectCaptor.capture());
            MatcherAssert.assertThat(
                    messageCaptor.getValue(),
                    Matchers.stringContainsInOrder("User properties file", "does not exist"));
        }

        @Test
        void shouldWarnWhenPropertiesNotFile() throws Exception {
            Path mockPath = Mockito.mock(Path.class);
            Mockito.when(
                            fs.pathOf(
                                    System.getProperty("user.home"),
                                    BasicAuthManager.USER_PROPERTIES_FILENAME))
                    .thenReturn(mockPath);
            Mockito.when(fs.exists(mockPath)).thenReturn(true);
            Mockito.when(fs.isRegularFile(mockPath)).thenReturn(false);

            mgr.loadConfig();

            ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object> objectCaptor = ArgumentCaptor.forClass(Object.class);
            Mockito.verify(logger).warn(messageCaptor.capture(), objectCaptor.capture());
            MatcherAssert.assertThat(
                    messageCaptor.getValue(),
                    Matchers.stringContainsInOrder("User properties path", "is not a file"));
        }

        @Test
        void shouldWarnWhenPropertiesNotReadable() throws Exception {
            Path mockPath = Mockito.mock(Path.class);
            Mockito.when(
                            fs.pathOf(
                                    System.getProperty("user.home"),
                                    BasicAuthManager.USER_PROPERTIES_FILENAME))
                    .thenReturn(mockPath);
            Mockito.when(fs.exists(mockPath)).thenReturn(true);
            Mockito.when(fs.isRegularFile(mockPath)).thenReturn(true);
            Mockito.when(fs.isReadable(mockPath)).thenReturn(false);

            mgr.loadConfig();

            ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object> objectCaptor = ArgumentCaptor.forClass(Object.class);
            Mockito.verify(logger).warn(messageCaptor.capture(), objectCaptor.capture());
            MatcherAssert.assertThat(
                    messageCaptor.getValue(),
                    Matchers.stringContainsInOrder("User properties file", "is not readable"));
        }

        @Test
        void shouldLogFileReadErrors() throws Exception {
            Path mockPath = Mockito.mock(Path.class);
            Mockito.when(
                            fs.pathOf(
                                    System.getProperty("user.home"),
                                    BasicAuthManager.USER_PROPERTIES_FILENAME))
                    .thenReturn(mockPath);
            Mockito.when(fs.exists(mockPath)).thenReturn(true);
            Mockito.when(fs.isRegularFile(mockPath)).thenReturn(true);
            Mockito.when(fs.isReadable(mockPath)).thenReturn(true);
            IOException ex = new IOException("foo");
            Mockito.when(fs.readFile(mockPath)).thenThrow(ex);

            mgr.loadConfig();

            Mockito.verify(logger).error(ex);
        }
    }

    @Nested
    class TokenValidationTest {
        @Test
        void shouldFailAuthenticationWhenCredentialsMalformed() throws Exception {
            Assertions.assertFalse(mgr.validateToken(() -> "user").get());
            ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object> objectCaptor = ArgumentCaptor.forClass(Object.class);
            Mockito.verify(logger).warn(messageCaptor.capture(), objectCaptor.capture());
            MatcherAssert.assertThat(
                    messageCaptor.getValue(),
                    Matchers.stringContainsInOrder("User properties file", "does not exist"));
        }

        @Test
        void shouldFailAuthenticationWhenNoMatchFound() throws Exception {
            Assertions.assertFalse(mgr.validateToken(() -> "user:pass").get());
            ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object> objectCaptor = ArgumentCaptor.forClass(Object.class);
            Mockito.verify(logger).warn(messageCaptor.capture(), objectCaptor.capture());
            MatcherAssert.assertThat(
                    messageCaptor.getValue(),
                    Matchers.stringContainsInOrder("User properties file", "does not exist"));
        }

        @Test
        void shouldPassAuthenticationWhenMatchFound() throws Exception {
            Path mockPath = Mockito.mock(Path.class);
            Mockito.when(
                            fs.pathOf(
                                    System.getProperty("user.home"),
                                    BasicAuthManager.USER_PROPERTIES_FILENAME))
                    .thenReturn(mockPath);
            Mockito.when(fs.exists(mockPath)).thenReturn(true);
            Mockito.when(fs.isRegularFile(mockPath)).thenReturn(true);
            Mockito.when(fs.isReadable(mockPath)).thenReturn(true);
            BufferedReader props =
                    new BufferedReader(
                            new StringReader(
                                    "user:d74ff0ee8da3b9806b18c877dbf29bbde50b5bd8e4dad7a3a725000feb82e8f1"));
            Mockito.when(fs.readFile(mockPath)).thenReturn(props);
            Assertions.assertTrue(mgr.validateToken(() -> "user:pass").get());
            Mockito.verifyNoInteractions(logger);
        }

        @Test
        void shouldHandleMultipleAuthentications() throws Exception {
            Path mockPath = Mockito.mock(Path.class);
            Mockito.when(
                            fs.pathOf(
                                    System.getProperty("user.home"),
                                    BasicAuthManager.USER_PROPERTIES_FILENAME))
                    .thenReturn(mockPath);
            Mockito.when(fs.exists(mockPath)).thenReturn(true);
            Mockito.when(fs.isRegularFile(mockPath)).thenReturn(true);
            Mockito.when(fs.isReadable(mockPath)).thenReturn(true);
            BufferedReader props =
                    new BufferedReader(
                            new StringReader(
                                    "user:d74ff0ee8da3b9806b18c877dbf29bbde50b5bd8e4dad7a3a725000feb82e8f1"));
            Mockito.when(fs.readFile(mockPath)).thenReturn(props);
            Assertions.assertTrue(mgr.validateToken(() -> "user:pass").get());
            Assertions.assertFalse(mgr.validateToken(() -> "user:sass").get());
            Assertions.assertFalse(mgr.validateToken(() -> "user2:pass").get());
            Mockito.verifyNoInteractions(logger);
        }

        @Test
        void shouldIgnoreMalformedPropertiesLines() throws Exception {
            Path mockPath = Mockito.mock(Path.class);
            Mockito.when(
                            fs.pathOf(
                                    System.getProperty("user.home"),
                                    BasicAuthManager.USER_PROPERTIES_FILENAME))
                    .thenReturn(mockPath);
            Mockito.when(fs.exists(mockPath)).thenReturn(true);
            Mockito.when(fs.isRegularFile(mockPath)).thenReturn(true);
            Mockito.when(fs.isReadable(mockPath)).thenReturn(true);
            String creds1 = "user:d74ff0ee8da3b9806b18c877dbf29bbde50b5bd8e4dad7a3a725000feb82e8f1";
            // malformed intentionally, '+' is not a valid key-value separator
            String creds2 = "foo+fcde2b2edba56bf408601fb721fe9b5c338d10ee429ea04fae5511b68fbf8fb9";
            String creds3 =
                    "admin=8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918";
            BufferedReader props =
                    new BufferedReader(
                            new StringReader(
                                    String.join(System.lineSeparator(), creds1, creds2, creds3)));
            Mockito.when(fs.readFile(mockPath)).thenReturn(props);
            Assertions.assertTrue(mgr.validateToken(() -> "user:pass").get());
            Assertions.assertFalse(mgr.validateToken(() -> "foo:bar").get());
            Assertions.assertTrue(mgr.validateToken(() -> "admin:admin").get());
            Mockito.verifyNoInteractions(logger);
        }
    }

    @Nested
    class HttpHeaderValidationTest {
        @Test
        void shouldPassKnownCredentials() throws Exception {
            Path mockPath = Mockito.mock(Path.class);
            Mockito.when(
                            fs.pathOf(
                                    System.getProperty("user.home"),
                                    BasicAuthManager.USER_PROPERTIES_FILENAME))
                    .thenReturn(mockPath);
            Mockito.when(fs.exists(mockPath)).thenReturn(true);
            Mockito.when(fs.isRegularFile(mockPath)).thenReturn(true);
            Mockito.when(fs.isReadable(mockPath)).thenReturn(true);
            BufferedReader props =
                    new BufferedReader(
                            new StringReader(
                                    "user:d74ff0ee8da3b9806b18c877dbf29bbde50b5bd8e4dad7a3a725000feb82e8f1"));
            Mockito.when(fs.readFile(mockPath)).thenReturn(props);
            Assertions.assertTrue(mgr.validateHttpHeader(() -> "Basic dXNlcjpwYXNz").get());
        }

        @Test
        void shouldFailUnknownCredentials() throws Exception {
            Path mockPath = Mockito.mock(Path.class);
            Mockito.when(
                            fs.pathOf(
                                    System.getProperty("user.home"),
                                    BasicAuthManager.USER_PROPERTIES_FILENAME))
                    .thenReturn(mockPath);
            Mockito.when(fs.exists(mockPath)).thenReturn(true);
            Mockito.when(fs.isRegularFile(mockPath)).thenReturn(true);
            Mockito.when(fs.isReadable(mockPath)).thenReturn(true);
            BufferedReader props =
                    new BufferedReader(
                            new StringReader(
                                    "user:d74ff0ee8da3b9806b18c877dbf29bbde50b5bd8e4dad7a3a725000feb82e8f1"));
            Mockito.when(fs.readFile(mockPath)).thenReturn(props);
            Assertions.assertFalse(mgr.validateHttpHeader(() -> "Basic foo").get());
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "Bearer sometoken", "Basic (not_b64)"})
        @NullSource
        void shouldFailBadCredentials(String s) throws Exception {
            Assertions.assertFalse(mgr.validateHttpHeader(() -> s).get());
        }
    }

    @Nested
    class WebSocketSubProtocolValidationTest {
        @Test
        void shouldPassKnownCredentials() throws Exception {
            Path mockPath = Mockito.mock(Path.class);
            Mockito.when(
                            fs.pathOf(
                                    System.getProperty("user.home"),
                                    BasicAuthManager.USER_PROPERTIES_FILENAME))
                    .thenReturn(mockPath);
            Mockito.when(fs.exists(mockPath)).thenReturn(true);
            Mockito.when(fs.isRegularFile(mockPath)).thenReturn(true);
            Mockito.when(fs.isReadable(mockPath)).thenReturn(true);
            // credentials: "user:pass"
            BufferedReader props =
                    new BufferedReader(
                            new StringReader(
                                    "user:d74ff0ee8da3b9806b18c877dbf29bbde50b5bd8e4dad7a3a725000feb82e8f1"));
            Mockito.when(fs.readFile(mockPath)).thenReturn(props);
            Assertions.assertTrue(
                    mgr.validateWebSocketSubProtocol(
                                    () -> "basic.authorization.containerjfr.dXNlcjpwYXNz")
                            .get());
        }

        @Test
        void shouldPassKnownCredentialsWithPadding() throws Exception {
            Path mockPath = Mockito.mock(Path.class);
            Mockito.when(
                            fs.pathOf(
                                    System.getProperty("user.home"),
                                    BasicAuthManager.USER_PROPERTIES_FILENAME))
                    .thenReturn(mockPath);
            Mockito.when(fs.exists(mockPath)).thenReturn(true);
            Mockito.when(fs.isRegularFile(mockPath)).thenReturn(true);
            Mockito.when(fs.isReadable(mockPath)).thenReturn(true);
            // credentials: "user:pass1234"
            BufferedReader props =
                    new BufferedReader(
                            new StringReader(
                                    "user:bd94dcda26fccb4e68d6a31f9b5aac0b571ae266d822620e901ef7ebe3a11d4f"));
            Mockito.when(fs.readFile(mockPath)).thenReturn(props);
            Assertions.assertTrue(
                    mgr.validateWebSocketSubProtocol(
                                    () -> "basic.authorization.containerjfr.dXNlcjpwYXNzMTIzNA==")
                            .get());
        }

        @Test
        void shouldPassKnownCredentialsAndStrippedPadding() throws Exception {
            Path mockPath = Mockito.mock(Path.class);
            Mockito.when(
                            fs.pathOf(
                                    System.getProperty("user.home"),
                                    BasicAuthManager.USER_PROPERTIES_FILENAME))
                    .thenReturn(mockPath);
            Mockito.when(fs.exists(mockPath)).thenReturn(true);
            Mockito.when(fs.isRegularFile(mockPath)).thenReturn(true);
            Mockito.when(fs.isReadable(mockPath)).thenReturn(true);
            // credentials: "user:pass1234"
            BufferedReader props =
                    new BufferedReader(
                            new StringReader(
                                    "user:bd94dcda26fccb4e68d6a31f9b5aac0b571ae266d822620e901ef7ebe3a11d4f"));
            Mockito.when(fs.readFile(mockPath)).thenReturn(props);
            // the subprotocol token part here should be "dXNlcjpwYXNzMTIzNA==", but the '='s are
            // padding and stripped out. The decoder should treat these as optional, and the client
            // is likely not to send them since they are not permitted by the WebSocket
            // specification for the Sec-WebSocket-Protocol header
            Assertions.assertTrue(
                    mgr.validateWebSocketSubProtocol(
                                    () -> "basic.authorization.containerjfr.dXNlcjpwYXNzMTIzNA")
                            .get());
        }

        @Test
        void shouldFailUnknownCredentials() throws Exception {
            Path mockPath = Mockito.mock(Path.class);
            Mockito.when(
                            fs.pathOf(
                                    System.getProperty("user.home"),
                                    BasicAuthManager.USER_PROPERTIES_FILENAME))
                    .thenReturn(mockPath);
            Mockito.when(fs.exists(mockPath)).thenReturn(true);
            Mockito.when(fs.isRegularFile(mockPath)).thenReturn(true);
            Mockito.when(fs.isReadable(mockPath)).thenReturn(true);
            // credentials: "user:pass"
            BufferedReader props =
                    new BufferedReader(
                            new StringReader(
                                    "user:d74ff0ee8da3b9806b18c877dbf29bbde50b5bd8e4dad7a3a725000feb82e8f1"));
            Mockito.when(fs.readFile(mockPath)).thenReturn(props);
            Assertions.assertFalse(
                    mgr.validateWebSocketSubProtocol(() -> "basic.authorization.containerjfr.foo")
                            .get());
        }

        @ParameterizedTest
        @ValueSource(
                strings = {
                    "",
                    "basic.credentials.foo",
                    "basic.authorization.containerjfr.user:pass"
                })
        @NullSource
        void shouldFailBadCredentials(String s) throws Exception {
            Assertions.assertFalse(mgr.validateWebSocketSubProtocol(() -> s).get());
        }
    }
}
