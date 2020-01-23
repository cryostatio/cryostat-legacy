package com.redhat.rhjmc.containerjfr.net;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
            Mockito.when(fs.newInputStream(mockPath)).thenThrow(ex);

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
            InputStream props =
                    new ByteArrayInputStream(
                            "user:d74ff0ee8da3b9806b18c877dbf29bbde50b5bd8e4dad7a3a725000feb82e8f1"
                                    .getBytes(StandardCharsets.UTF_8));
            Mockito.when(fs.newInputStream(mockPath)).thenReturn(props);
            Assertions.assertTrue(mgr.validateToken(() -> "user:pass").get());
            Mockito.verifyNoMoreInteractions(logger);
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
            InputStream props =
                    new ByteArrayInputStream(
                            "user:d74ff0ee8da3b9806b18c877dbf29bbde50b5bd8e4dad7a3a725000feb82e8f1"
                                    .getBytes(StandardCharsets.UTF_8));
            Mockito.when(fs.newInputStream(mockPath)).thenReturn(props);
            Assertions.assertTrue(mgr.validateToken(() -> "user:pass").get());
            Assertions.assertFalse(mgr.validateToken(() -> "user:sass").get());
            Assertions.assertFalse(mgr.validateToken(() -> "user2:pass").get());
            Mockito.verifyNoMoreInteractions(logger);
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
            InputStream props =
                    new ByteArrayInputStream(
                            "user:d74ff0ee8da3b9806b18c877dbf29bbde50b5bd8e4dad7a3a725000feb82e8f1"
                                    .getBytes(StandardCharsets.UTF_8));
            Mockito.when(fs.newInputStream(mockPath)).thenReturn(props);
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
            InputStream props =
                    new ByteArrayInputStream(
                            "user:d74ff0ee8da3b9806b18c877dbf29bbde50b5bd8e4dad7a3a725000feb82e8f1"
                                    .getBytes(StandardCharsets.UTF_8));
            Mockito.when(fs.newInputStream(mockPath)).thenReturn(props);
            Assertions.assertFalse(mgr.validateHttpHeader(() -> "Basic foo").get());
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "Bearer sometoken", "Basic (not_b64)"})
        void shouldFailBadCredentials(String s) throws Exception {
            Assertions.assertFalse(mgr.validateHttpHeader(() -> s).get());
        }

        @Test
        void shouldFailNullHeader() throws Exception {
            Assertions.assertFalse(mgr.validateHttpHeader(() -> null).get());
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
            InputStream props =
                    new ByteArrayInputStream(
                            "user:d74ff0ee8da3b9806b18c877dbf29bbde50b5bd8e4dad7a3a725000feb82e8f1"
                                    .getBytes(StandardCharsets.UTF_8));
            Mockito.when(fs.newInputStream(mockPath)).thenReturn(props);
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
            InputStream props =
                    new ByteArrayInputStream(
                            "user:d74ff0ee8da3b9806b18c877dbf29bbde50b5bd8e4dad7a3a725000feb82e8f1"
                                    .getBytes(StandardCharsets.UTF_8));
            Mockito.when(fs.newInputStream(mockPath)).thenReturn(props);
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
        void shouldFailBadCredentials(String s) throws Exception {
            Assertions.assertFalse(mgr.validateWebSocketSubProtocol(() -> s).get());
        }

        @Test
        void shouldFailNullProtocol() throws Exception {
            Assertions.assertFalse(mgr.validateWebSocketSubProtocol(() -> null).get());
        }
    }
}
