package com.redhat.rhjmc.containerjfr.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;

import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;

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
            Mockito.verify(logger).warn(messageCaptor.capture());
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
            Mockito.verify(logger).warn(messageCaptor.capture());
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
            Mockito.verify(logger).warn(messageCaptor.capture());
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
            Mockito.verify(logger).warn(messageCaptor.capture());
            MatcherAssert.assertThat(
                    messageCaptor.getValue(),
                    Matchers.stringContainsInOrder("User properties file", "does not exist"));
        }

        @Test
        void shouldFailAuthenticationWhenNoMatchFound() throws Exception {
            Assertions.assertFalse(mgr.validateToken(() -> "user:pass").get());
            ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
            Mockito.verify(logger).warn(messageCaptor.capture());
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
            Mockito.verifyZeroInteractions(logger);
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
            Mockito.verifyZeroInteractions(logger);
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
            Mockito.verifyZeroInteractions(logger);
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
