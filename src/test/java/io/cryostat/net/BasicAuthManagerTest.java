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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.net.security.ResourceAction;

import org.apache.commons.codec.binary.Base64;
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

@ExtendWith(MockitoExtension.class)
class BasicAuthManagerTest {

    BasicAuthManager mgr;
    @Mock Logger logger;
    @Mock FileSystem fs;
    @Mock Path confDir;

    @BeforeEach
    void setup() {
        mgr = new BasicAuthManager(logger, fs, confDir);
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
            Mockito.when(confDir.resolve(Mockito.anyString())).thenReturn(mockPath);
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
            Mockito.when(confDir.resolve(Mockito.anyString())).thenReturn(mockPath);
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
            Mockito.when(confDir.resolve(Mockito.anyString())).thenReturn(mockPath);
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
        void shouldReturnUserInfoWithSuppliedUsername() throws Exception {
            Path mockPath = Mockito.mock(Path.class);
            Mockito.when(confDir.resolve(Mockito.anyString())).thenReturn(mockPath);
            Mockito.when(fs.exists(mockPath)).thenReturn(true);
            Mockito.when(fs.isRegularFile(mockPath)).thenReturn(true);
            Mockito.when(fs.isReadable(mockPath)).thenReturn(true);
            BufferedReader props =
                    new BufferedReader(
                            new StringReader(
                                    "user:d74ff0ee8da3b9806b18c877dbf29bbde50b5bd8e4dad7a3a725000feb82e8f1"));
            Mockito.when(fs.readFile(mockPath)).thenReturn(props);
            String credentials =
                    Base64.encodeBase64String("user:pass".getBytes(StandardCharsets.UTF_8));
            UserInfo userInfo = mgr.getUserInfo(() -> "Basic " + credentials).get();
            MatcherAssert.assertThat(userInfo.getUsername(), Matchers.equalTo("user"));
        }

        @Test
        void shouldFailAuthenticationWhenCredentialsMalformed() throws Exception {
            Assertions.assertFalse(mgr.validateToken(() -> "user", ResourceAction.NONE).get());
            ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object> objectCaptor = ArgumentCaptor.forClass(Object.class);
            Mockito.verify(logger).warn(messageCaptor.capture(), objectCaptor.capture());
            MatcherAssert.assertThat(
                    messageCaptor.getValue(),
                    Matchers.stringContainsInOrder("User properties file", "does not exist"));
        }

        @Test
        void shouldFailAuthenticationWhenNoMatchFound() throws Exception {
            Assertions.assertFalse(mgr.validateToken(() -> "user:pass", ResourceAction.NONE).get());
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
            Mockito.when(confDir.resolve(Mockito.anyString())).thenReturn(mockPath);
            Mockito.when(fs.exists(mockPath)).thenReturn(true);
            Mockito.when(fs.isRegularFile(mockPath)).thenReturn(true);
            Mockito.when(fs.isReadable(mockPath)).thenReturn(true);
            BufferedReader props =
                    new BufferedReader(
                            new StringReader(
                                    "user:d74ff0ee8da3b9806b18c877dbf29bbde50b5bd8e4dad7a3a725000feb82e8f1"));
            Mockito.when(fs.readFile(mockPath)).thenReturn(props);
            Assertions.assertTrue(mgr.validateToken(() -> "user:pass", ResourceAction.NONE).get());
            Mockito.verifyNoInteractions(logger);
        }

        @Test
        void shouldHandleMultipleAuthentications() throws Exception {
            Path mockPath = Mockito.mock(Path.class);
            Mockito.when(confDir.resolve(Mockito.anyString())).thenReturn(mockPath);
            Mockito.when(fs.exists(mockPath)).thenReturn(true);
            Mockito.when(fs.isRegularFile(mockPath)).thenReturn(true);
            Mockito.when(fs.isReadable(mockPath)).thenReturn(true);
            BufferedReader props =
                    new BufferedReader(
                            new StringReader(
                                    "user:d74ff0ee8da3b9806b18c877dbf29bbde50b5bd8e4dad7a3a725000feb82e8f1"));
            Mockito.when(fs.readFile(mockPath)).thenReturn(props);
            Assertions.assertTrue(mgr.validateToken(() -> "user:pass", ResourceAction.NONE).get());
            Assertions.assertFalse(mgr.validateToken(() -> "user:sass", ResourceAction.NONE).get());
            Assertions.assertFalse(
                    mgr.validateToken(() -> "user2:pass", ResourceAction.NONE).get());
            Mockito.verifyNoInteractions(logger);
        }

        @Test
        void shouldIgnoreMalformedPropertiesLines() throws Exception {
            Path mockPath = Mockito.mock(Path.class);
            Mockito.when(confDir.resolve(Mockito.anyString())).thenReturn(mockPath);
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
            Assertions.assertTrue(mgr.validateToken(() -> "user:pass", ResourceAction.NONE).get());
            Assertions.assertFalse(mgr.validateToken(() -> "foo:bar", ResourceAction.NONE).get());
            Assertions.assertTrue(
                    mgr.validateToken(() -> "admin:admin", ResourceAction.NONE).get());
            Mockito.verifyNoInteractions(logger);
        }
    }

    @Nested
    class HttpHeaderValidationTest {
        @Test
        void shouldPassKnownCredentials() throws Exception {
            Path mockPath = Mockito.mock(Path.class);
            Mockito.when(confDir.resolve(Mockito.anyString())).thenReturn(mockPath);
            Mockito.when(fs.exists(mockPath)).thenReturn(true);
            Mockito.when(fs.isRegularFile(mockPath)).thenReturn(true);
            Mockito.when(fs.isReadable(mockPath)).thenReturn(true);
            BufferedReader props =
                    new BufferedReader(
                            new StringReader(
                                    "user:d74ff0ee8da3b9806b18c877dbf29bbde50b5bd8e4dad7a3a725000feb82e8f1"));
            Mockito.when(fs.readFile(mockPath)).thenReturn(props);
            Assertions.assertTrue(
                    mgr.validateHttpHeader(() -> "Basic dXNlcjpwYXNz", ResourceAction.NONE).get());
        }

        @Test
        void shouldFailUnknownCredentials() throws Exception {
            Path mockPath = Mockito.mock(Path.class);
            Mockito.when(confDir.resolve(Mockito.anyString())).thenReturn(mockPath);
            Mockito.when(fs.exists(mockPath)).thenReturn(true);
            Mockito.when(fs.isRegularFile(mockPath)).thenReturn(true);
            Mockito.when(fs.isReadable(mockPath)).thenReturn(true);
            BufferedReader props =
                    new BufferedReader(
                            new StringReader(
                                    "user:d74ff0ee8da3b9806b18c877dbf29bbde50b5bd8e4dad7a3a725000feb82e8f1"));
            Mockito.when(fs.readFile(mockPath)).thenReturn(props);
            Assertions.assertFalse(
                    mgr.validateHttpHeader(() -> "Basic foo", ResourceAction.NONE).get());
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "Bearer sometoken", "Basic (not_b64)", "Basic "})
        @NullSource
        void shouldFailBadCredentials(String s) throws Exception {
            Assertions.assertFalse(mgr.validateHttpHeader(() -> s, ResourceAction.NONE).get());
        }
    }

    @Nested
    class WebSocketSubProtocolValidationTest {
        @Test
        void shouldPassKnownCredentials() throws Exception {
            Path mockPath = Mockito.mock(Path.class);
            Mockito.when(confDir.resolve(Mockito.anyString())).thenReturn(mockPath);
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
                                    () -> "basic.authorization.cryostat.dXNlcjpwYXNz",
                                    ResourceAction.NONE)
                            .get());
        }

        @Test
        void shouldPassKnownCredentialsWithPadding() throws Exception {
            Path mockPath = Mockito.mock(Path.class);
            Mockito.when(confDir.resolve(Mockito.anyString())).thenReturn(mockPath);
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
                                    () -> "basic.authorization.cryostat.dXNlcjpwYXNzMTIzNA==",
                                    ResourceAction.NONE)
                            .get());
        }

        @Test
        void shouldPassKnownCredentialsAndStrippedPadding() throws Exception {
            Path mockPath = Mockito.mock(Path.class);
            Mockito.when(confDir.resolve(Mockito.anyString())).thenReturn(mockPath);
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
                                    () -> "basic.authorization.cryostat.dXNlcjpwYXNzMTIzNA",
                                    ResourceAction.NONE)
                            .get());
        }

        @Test
        void shouldFailUnknownCredentials() throws Exception {
            Path mockPath = Mockito.mock(Path.class);
            Mockito.when(confDir.resolve(Mockito.anyString())).thenReturn(mockPath);
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
                    mgr.validateWebSocketSubProtocol(
                                    () -> "basic.authorization.cryostat.foo", ResourceAction.NONE)
                            .get());
        }

        @ParameterizedTest
        @ValueSource(
                strings = {"", "basic.credentials.foo", "basic.authorization.cryostat.user:pass"})
        @NullSource
        void shouldFailBadCredentials(String s) throws Exception {
            Assertions.assertFalse(
                    mgr.validateWebSocketSubProtocol(() -> s, ResourceAction.NONE).get());
        }
    }
}
