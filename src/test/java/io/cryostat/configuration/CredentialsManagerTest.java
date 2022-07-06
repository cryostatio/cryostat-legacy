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
package io.cryostat.configuration;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.cryostat.MainModule;
import io.cryostat.configuration.CredentialsManager.MatchedCredentials;
import io.cryostat.configuration.CredentialsManager.StoredCredentials;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.Credentials;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.platform.PlatformClient;
import io.cryostat.platform.ServiceRef;
import io.cryostat.rules.MatchExpressionEvaluator;
import io.cryostat.rules.MatchExpressionValidator;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.commons.codec.binary.Base32;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith(MockitoExtension.class)
class CredentialsManagerTest {

    CredentialsManager credentialsManager;
    @Mock Path credentialsDir;
    @Mock MatchExpressionValidator matchExpressionValidator;
    @Mock MatchExpressionEvaluator matchExpressionEvaluator;
    @Mock FileSystem fs;
    @Mock PlatformClient platformClient;
    @Mock NotificationFactory notificationFactory;
    @Mock Logger logger;
    Base32 base32 = new Base32();
    Gson gson = MainModule.provideGson(logger);

    @BeforeEach
    void setup() {
        this.credentialsManager =
                new CredentialsManager(
                        credentialsDir,
                        matchExpressionValidator,
                        matchExpressionEvaluator,
                        fs,
                        platformClient,
                        notificationFactory,
                        gson,
                        logger);
    }

    @Test
    void initializesEmpty() throws Exception {
        Mockito.when(platformClient.listDiscoverableServices()).thenReturn(List.of());

        MatcherAssert.assertThat(credentialsManager.getMatchExpressions(), Matchers.empty());
        MatcherAssert.assertThat(
                credentialsManager.getServiceRefsWithCredentials(), Matchers.empty());
        MatcherAssert.assertThat(
                credentialsManager.getMatchExpressionsWithMatchedTargets(), Matchers.empty());
        Assertions.assertThrows(
                FileNotFoundException.class, () -> credentialsManager.removeCredentials("foo"));
        MatcherAssert.assertThat(
                credentialsManager.getCredentials(new ServiceRef(new URI("foo"), "foo")),
                Matchers.nullValue());
        MatcherAssert.assertThat(
                credentialsManager.getCredentialsByTargetId("foo"), Matchers.nullValue());
    }

    @Test
    void canAddThenGet() throws Exception {
        String targetId = "foo";
        String matchExpression = String.format("target.connectUrl == \"%s\"", targetId);

        String filename = "0";
        Path path = Mockito.mock(Path.class);
        Mockito.when(credentialsDir.resolve(filename)).thenReturn(path);

        String username = "user";
        String password = "pass";
        Credentials credentials = new Credentials(username, password);

        ServiceRef serviceRef = new ServiceRef(new URI(targetId), "foo");
        Mockito.when(matchExpressionEvaluator.applies(matchExpression, serviceRef))
                .thenReturn(true);

        credentialsManager.addCredentials(matchExpression, credentials);

        ArgumentCaptor<String> contentsCaptor = ArgumentCaptor.forClass(String.class);

        InOrder inOrder = Mockito.inOrder(fs, credentialsDir);
        inOrder.verify(credentialsDir).resolve(filename);
        inOrder.verify(fs)
                .writeString(
                        Mockito.eq(path),
                        contentsCaptor.capture(),
                        Mockito.eq(StandardOpenOption.WRITE),
                        Mockito.eq(StandardOpenOption.CREATE),
                        Mockito.eq(StandardOpenOption.TRUNCATE_EXISTING));
        inOrder.verify(fs)
                .setPosixFilePermissions(
                        path,
                        Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        Mockito.verifyNoMoreInteractions(fs);

        Mockito.when(fs.listDirectoryChildren(credentialsDir)).thenReturn(List.of(filename));
        Mockito.when(credentialsDir.resolve(filename)).thenReturn(path);
        Mockito.when(fs.readFile(path))
                .thenAnswer(
                        new Answer<BufferedReader>() {
                            @Override
                            public BufferedReader answer(InvocationOnMock invocation)
                                    throws Throwable {
                                return new BufferedReader(
                                        new StringReader(
                                                gson.toJson(
                                                        new StoredCredentials(
                                                                matchExpression, credentials))));
                            }
                        });

        String newContents = contentsCaptor.getValue();

        JsonObject json = gson.fromJson(newContents, JsonObject.class);
        MatcherAssert.assertThat(
                json.getAsJsonPrimitive("matchExpression").getAsString(),
                Matchers.equalTo(matchExpression));
        JsonObject foundCredentials = json.getAsJsonObject("credentials");
        String foundUsername = foundCredentials.getAsJsonPrimitive("username").getAsString();
        String foundPassword = foundCredentials.getAsJsonPrimitive("password").getAsString();
        MatcherAssert.assertThat(foundUsername, Matchers.equalTo(username));
        MatcherAssert.assertThat(foundPassword, Matchers.equalTo(password));

        Credentials found = credentialsManager.getCredentials(serviceRef);
        MatcherAssert.assertThat(found.getUsername(), Matchers.equalTo(username));
        MatcherAssert.assertThat(found.getPassword(), Matchers.equalTo(password));
    }

    @Test
    void canAddThenRemove() throws Exception {
        String targetId = "foo";
        String matchExpression = String.format("target.connectUrl == \"%s\"", targetId);

        String username = "user";
        String password = "pass";
        Credentials credentials = new Credentials(username, password);

        Assertions.assertThrows(
                FileNotFoundException.class,
                () -> credentialsManager.removeCredentials(matchExpression));

        String filename = "1";
        Path writePath = Mockito.mock(Path.class);
        Mockito.when(credentialsDir.resolve(Mockito.any(String.class))).thenReturn(writePath);
        Path filenamePath = Mockito.mock(Path.class);
        Mockito.when(filenamePath.toString()).thenReturn(filename);
        Mockito.when(writePath.getFileName()).thenReturn(filenamePath);

        credentialsManager.addCredentials(matchExpression, credentials);

        Mockito.when(fs.listDirectoryChildren(credentialsDir)).thenReturn(List.of(filename));
        Mockito.when(credentialsDir.resolve(filename)).thenReturn(writePath);
        Mockito.when(fs.readFile(writePath))
                .thenAnswer(
                        new Answer<BufferedReader>() {
                            @Override
                            public BufferedReader answer(InvocationOnMock invocation)
                                    throws Throwable {
                                return new BufferedReader(
                                        new StringReader(
                                                gson.toJson(
                                                        new StoredCredentials(
                                                                matchExpression, credentials))));
                            }
                        });

        MatcherAssert.assertThat(
                credentialsManager.removeCredentials(matchExpression), Matchers.equalTo(1));
    }

    @Test
    void addedCredentialsCanMatchMultipleTargets() throws Exception {
        ServiceRef target1 = new ServiceRef(new URI("target1"), "target1Alias");
        ServiceRef target2 = new ServiceRef(new URI("target2"), "target2Alias");
        ServiceRef target3 = new ServiceRef(new URI("target3"), "target3Alias");
        ServiceRef target4 = new ServiceRef(new URI("target4"), "target4Alias");

        Mockito.when(platformClient.listDiscoverableServices())
                .thenReturn(List.of(target1, target2, target3, target4));

        String matchExpression = "some expression";
        String username = "user";
        String password = "pass";
        Credentials credentials = new Credentials(username, password);

        Mockito.when(matchExpressionEvaluator.applies(Mockito.eq(matchExpression), Mockito.any()))
                .thenAnswer(
                        new Answer<Boolean>() {
                            @Override
                            public Boolean answer(InvocationOnMock invocation) throws Throwable {
                                ServiceRef sr = (ServiceRef) invocation.getArgument(1);
                                String alias = sr.getAlias().orElseThrow();
                                return Set.of(target1.getAlias().get(), target2.getAlias().get())
                                        .contains(alias);
                            }
                        });

        Path writePath = Mockito.mock(Path.class);
        Mockito.when(credentialsDir.resolve(Mockito.any(String.class))).thenReturn(writePath);

        credentialsManager.addCredentials(matchExpression, credentials);

        Mockito.verify(fs)
                .writeString(
                        Mockito.eq(writePath),
                        Mockito.anyString(),
                        Mockito.eq(StandardOpenOption.WRITE),
                        Mockito.eq(StandardOpenOption.CREATE),
                        Mockito.eq(StandardOpenOption.TRUNCATE_EXISTING));
        Mockito.verify(fs)
                .setPosixFilePermissions(
                        writePath,
                        Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));

        Mockito.when(fs.listDirectoryChildren(credentialsDir)).thenReturn(List.of("0"));
        Mockito.when(credentialsDir.resolve("0")).thenReturn(writePath);
        Mockito.when(fs.readFile(writePath))
                .thenAnswer(
                        new Answer<BufferedReader>() {
                            @Override
                            public BufferedReader answer(InvocationOnMock invocation)
                                    throws Throwable {
                                return new BufferedReader(
                                        new StringReader(
                                                gson.toJson(
                                                        new StoredCredentials(
                                                                matchExpression, credentials))));
                            }
                        });

        MatcherAssert.assertThat(
                credentialsManager.getCredentials(target1), Matchers.equalTo(credentials));
        MatcherAssert.assertThat(
                credentialsManager.getCredentials(target2), Matchers.equalTo(credentials));
        MatcherAssert.assertThat(credentialsManager.getCredentials(target3), Matchers.nullValue());
        MatcherAssert.assertThat(credentialsManager.getCredentials(target4), Matchers.nullValue());

        MatcherAssert.assertThat(
                credentialsManager.getCredentialsByTargetId("target1"),
                Matchers.equalTo(credentials));
        MatcherAssert.assertThat(
                credentialsManager.getCredentialsByTargetId("target2"),
                Matchers.equalTo(credentials));
        MatcherAssert.assertThat(
                credentialsManager.getCredentialsByTargetId("target3"), Matchers.nullValue());
        MatcherAssert.assertThat(
                credentialsManager.getCredentialsByTargetId("target4"), Matchers.nullValue());
    }

    @Test
    void canQueryDiscoveredTargetsWithConfiguredCredentials() throws Exception {
        ServiceRef target1 = new ServiceRef(new URI("target1"), "target1Alias");
        ServiceRef target2 = new ServiceRef(new URI("target2"), "target2Alias");
        ServiceRef target3 = new ServiceRef(new URI("target3"), "target3Alias");
        ServiceRef target4 = new ServiceRef(new URI("target4"), "target4Alias");

        Mockito.when(platformClient.listDiscoverableServices())
                .thenReturn(List.of(target1, target2, target3, target4));

        String matchExpression = "some expression";
        String username = "user";
        String password = "pass";
        Credentials credentials = new Credentials(username, password);

        Mockito.when(matchExpressionEvaluator.applies(Mockito.eq(matchExpression), Mockito.any()))
                .thenAnswer(
                        new Answer<Boolean>() {
                            @Override
                            public Boolean answer(InvocationOnMock invocation) throws Throwable {
                                ServiceRef sr = (ServiceRef) invocation.getArgument(1);
                                String alias = sr.getAlias().orElseThrow();
                                return Set.of(target1.getAlias().get(), target2.getAlias().get())
                                        .contains(alias);
                            }
                        });

        Path writePath = Mockito.mock(Path.class);
        Mockito.when(credentialsDir.resolve(Mockito.any(String.class))).thenReturn(writePath);

        credentialsManager.addCredentials(matchExpression, credentials);

        Mockito.when(fs.listDirectoryChildren(credentialsDir)).thenReturn(List.of("0"));
        Mockito.when(credentialsDir.resolve("0")).thenReturn(writePath);
        Mockito.when(fs.readFile(writePath))
                .thenAnswer(
                        new Answer<BufferedReader>() {
                            @Override
                            public BufferedReader answer(InvocationOnMock invocation)
                                    throws Throwable {
                                return new BufferedReader(
                                        new StringReader(
                                                gson.toJson(
                                                        new StoredCredentials(
                                                                matchExpression, credentials))));
                            }
                        });

        MatcherAssert.assertThat(
                credentialsManager.getServiceRefsWithCredentials(),
                Matchers.equalTo(List.of(target1, target2)));
    }

    @Test
    void canQueryExpressionsWithMatchingTargets() throws Exception {
        ServiceRef target1 = new ServiceRef(new URI("target1"), "target1Alias");
        ServiceRef target2 = new ServiceRef(new URI("target2"), "target2Alias");
        ServiceRef target3 = new ServiceRef(new URI("target3"), "target3Alias");
        ServiceRef target4 = new ServiceRef(new URI("target4"), "target4Alias");

        Mockito.when(platformClient.listDiscoverableServices())
                .thenReturn(List.of(target1, target2, target3, target4));

        String matchExpression = "some expression";
        String username = "user";
        String password = "pass";
        Credentials credentials = new Credentials(username, password);

        Mockito.when(matchExpressionEvaluator.applies(Mockito.eq(matchExpression), Mockito.any()))
                .thenAnswer(
                        new Answer<Boolean>() {
                            @Override
                            public Boolean answer(InvocationOnMock invocation) throws Throwable {
                                ServiceRef sr = (ServiceRef) invocation.getArgument(1);
                                String alias = sr.getAlias().orElseThrow();
                                return Set.of("target1Alias", "target2Alias").contains(alias);
                            }
                        });

        Path writePath = Mockito.mock(Path.class);
        Mockito.when(credentialsDir.resolve(Mockito.any(String.class))).thenReturn(writePath);

        credentialsManager.addCredentials(matchExpression, credentials);

        Mockito.verify(fs)
                .writeString(
                        Mockito.eq(writePath),
                        Mockito.anyString(),
                        Mockito.eq(StandardOpenOption.WRITE),
                        Mockito.eq(StandardOpenOption.CREATE),
                        Mockito.eq(StandardOpenOption.TRUNCATE_EXISTING));
        Mockito.verify(fs)
                .setPosixFilePermissions(
                        writePath,
                        Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));

        Mockito.when(fs.listDirectoryChildren(credentialsDir)).thenReturn(List.of("0"));
        Mockito.when(credentialsDir.resolve("0")).thenReturn(writePath);
        Mockito.when(fs.readFile(writePath))
                .thenAnswer(
                        new Answer<BufferedReader>() {
                            @Override
                            public BufferedReader answer(InvocationOnMock invocation)
                                    throws Throwable {
                                return new BufferedReader(
                                        new StringReader(
                                                gson.toJson(
                                                        new StoredCredentials(
                                                                matchExpression, credentials))));
                            }
                        });

        credentialsManager.addCredentials(matchExpression, credentials);

        MatchedCredentials matchedCredentials =
                new MatchedCredentials(matchExpression, Set.of(target1, target2));

        MatcherAssert.assertThat(
                credentialsManager.getMatchExpressionsWithMatchedTargets(),
                Matchers.equalTo(List.of(matchedCredentials)));
    }

    @Test
    void canListMatchExpressions() throws Exception {
        String matchExpression = "some expression";
        String username = "user";
        String password = "pass";
        Credentials credentials = new Credentials(username, password);

        Path writePath = Mockito.mock(Path.class);
        Mockito.when(credentialsDir.resolve(Mockito.any(String.class))).thenReturn(writePath);

        credentialsManager.addCredentials(matchExpression, credentials);

        Mockito.verify(fs)
                .writeString(
                        Mockito.eq(writePath),
                        Mockito.anyString(),
                        Mockito.eq(StandardOpenOption.WRITE),
                        Mockito.eq(StandardOpenOption.CREATE),
                        Mockito.eq(StandardOpenOption.TRUNCATE_EXISTING));
        Mockito.verify(fs)
                .setPosixFilePermissions(
                        writePath,
                        Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));

        Mockito.when(fs.listDirectoryChildren(credentialsDir)).thenReturn(List.of("0"));
        Mockito.when(credentialsDir.resolve("0")).thenReturn(writePath);
        Mockito.when(fs.readFile(writePath))
                .thenAnswer(
                        new Answer<BufferedReader>() {
                            @Override
                            public BufferedReader answer(InvocationOnMock invocation)
                                    throws Throwable {
                                return new BufferedReader(
                                        new StringReader(
                                                gson.toJson(
                                                        new StoredCredentials(
                                                                matchExpression, credentials))));
                            }
                        });

        MatcherAssert.assertThat(
                credentialsManager.getMatchExpressions(),
                Matchers.equalTo(Set.of(matchExpression)));
    }

    @Nested
    class Migration {

        @Test
        void doesNothingIfNoFiles() throws Exception {
            Mockito.when(fs.listDirectoryChildren(Mockito.any())).thenReturn(List.of());

            Mockito.verifyNoInteractions(fs);

            credentialsManager.migrate();

            Mockito.verify(fs).listDirectoryChildren(credentialsDir);
            Mockito.verifyNoMoreInteractions(fs);
        }

        @Test
        void doesNothingIfAllFilesInNewFormat() throws Exception {
            String matchExpression = "target.connectUrl == \"foo\"";
            String filename = "0";
            Mockito.when(fs.listDirectoryChildren(Mockito.any())).thenReturn(List.of(filename));

            Path path = Mockito.mock(Path.class);
            Mockito.when(credentialsDir.resolve(filename)).thenReturn(path);

            String contents =
                    gson.toJson(
                            Map.of(
                                    "matchExpression",
                                    matchExpression,
                                    "credentials",
                                    Map.of("username", "user", "password", "pass")));
            Mockito.when(fs.readFile(path))
                    .thenReturn(new BufferedReader(new StringReader(contents)));

            credentialsManager.migrate();

            InOrder inOrder = Mockito.inOrder(fs, credentialsDir);
            inOrder.verify(fs).listDirectoryChildren(credentialsDir);
            inOrder.verify(credentialsDir).resolve(filename);
            inOrder.verify(fs).readFile(path);
            Mockito.verifyNoMoreInteractions(fs);
        }

        @Test
        void migratesOldFormatToNewFormat() throws Exception {
            String targetId = "foo";
            String matchExpression = String.format("target.connectUrl == \"%s\"", targetId);

            String originalFilename =
                    String.format(
                            "%s.json",
                            base32.encodeToString(targetId.getBytes(StandardCharsets.UTF_8)));
            Mockito.when(fs.listDirectoryChildren(Mockito.any()))
                    .thenReturn(List.of(originalFilename));

            String newFilename = "0";

            Path originalPath = Mockito.mock(Path.class);
            Path newPath = Mockito.mock(Path.class);
            Mockito.when(credentialsDir.resolve(originalFilename)).thenReturn(originalPath);
            Mockito.when(credentialsDir.resolve(newFilename)).thenReturn(newPath);

            String username = "user";
            String password = "pass";

            String oldContents =
                    gson.toJson(
                            Map.of(
                                    "targetId",
                                    targetId,
                                    "credentials",
                                    Map.of("username", username, "password", password)));
            Mockito.when(fs.readFile(originalPath))
                    .thenReturn(new BufferedReader(new StringReader(oldContents)));

            credentialsManager.migrate();

            ArgumentCaptor<String> contentsCaptor = ArgumentCaptor.forClass(String.class);

            Mockito.verify(fs).listDirectoryChildren(credentialsDir);
            Mockito.verify(credentialsDir).resolve(originalFilename);
            Mockito.verify(fs).readFile(originalPath);
            Mockito.verify(fs)
                    .writeString(
                            Mockito.eq(newPath),
                            contentsCaptor.capture(),
                            Mockito.eq(StandardOpenOption.WRITE),
                            Mockito.eq(StandardOpenOption.CREATE),
                            Mockito.eq(StandardOpenOption.TRUNCATE_EXISTING));
            Mockito.verify(fs)
                    .setPosixFilePermissions(
                            newPath,
                            Set.of(
                                    PosixFilePermission.OWNER_READ,
                                    PosixFilePermission.OWNER_WRITE));
            Mockito.verify(fs).deleteIfExists(originalPath);
            Mockito.verifyNoMoreInteractions(fs);

            String newContents = contentsCaptor.getValue();
            JsonObject json = gson.fromJson(newContents, JsonObject.class);
            MatcherAssert.assertThat(
                    json.getAsJsonPrimitive("matchExpression").getAsString(),
                    Matchers.equalTo(matchExpression));
            JsonObject credentials = json.getAsJsonObject("credentials");
            String foundUsername = credentials.getAsJsonPrimitive("username").getAsString();
            String foundPassword = credentials.getAsJsonPrimitive("password").getAsString();
            MatcherAssert.assertThat(foundUsername, Matchers.equalTo(username));
            MatcherAssert.assertThat(foundPassword, Matchers.equalTo(password));
        }
    }

    @Nested
    class Loading {
        @Test
        void loadingFilesMakesContentsAvailable() throws Exception {
            String targetId = "foo";
            String matchExpression = String.format("target.connectUrl == \"%s\"", targetId);
            String filename =
                    String.format(
                            "%s.json",
                            base32.encodeToString(
                                    matchExpression.getBytes(StandardCharsets.UTF_8)));
            Mockito.when(fs.listDirectoryChildren(Mockito.any())).thenReturn(List.of(filename));

            Path path = Mockito.mock(Path.class);
            Path filenamePath = Mockito.mock(Path.class);
            Mockito.when(filenamePath.toString()).thenReturn("0");
            Mockito.when(path.getFileName()).thenReturn(filenamePath);

            String username = "user";
            String password = "pass";

            Mockito.when(fs.listDirectoryChildren(credentialsDir)).thenReturn(List.of("0"));
            Mockito.when(credentialsDir.resolve("0")).thenReturn(path);
            Mockito.when(fs.readFile(path))
                    .thenAnswer(
                            new Answer<BufferedReader>() {
                                @Override
                                public BufferedReader answer(InvocationOnMock invocation)
                                        throws Throwable {
                                    return new BufferedReader(
                                            new StringReader(
                                                    gson.toJson(
                                                            new StoredCredentials(
                                                                    matchExpression,
                                                                    new Credentials(
                                                                            username, password)))));
                                }
                            });

            ServiceRef serviceRef = new ServiceRef(new URI(targetId), "foo");
            Mockito.when(platformClient.listDiscoverableServices()).thenReturn(List.of(serviceRef));
            Mockito.when(matchExpressionEvaluator.applies(matchExpression, serviceRef))
                    .thenReturn(true);

            credentialsManager.load();

            Mockito.verify(fs).listDirectoryChildren(credentialsDir);
            Mockito.verifyNoMoreInteractions(fs);

            Credentials found = credentialsManager.getCredentialsByTargetId(targetId);
            MatcherAssert.assertThat(found.getUsername(), Matchers.equalTo(username));
            MatcherAssert.assertThat(found.getPassword(), Matchers.equalTo(password));
        }
    }
}
