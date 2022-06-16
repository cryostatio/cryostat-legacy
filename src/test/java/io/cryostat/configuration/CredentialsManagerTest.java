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
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.cryostat.MainModule;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.platform.PlatformClient;
import io.cryostat.rules.MatchExpressionEvaluator;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.commons.codec.binary.Base32;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CredentialsManagerTest {

    CredentialsManager credentialsManager;
    @Mock Path credentialsDir;
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
                        matchExpressionEvaluator,
                        fs,
                        platformClient,
                        notificationFactory,
                        gson,
                        base32,
                        logger);
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
            String filename =
                    String.format(
                            "%s.json",
                            base32.encodeToString(
                                    matchExpression.getBytes(StandardCharsets.UTF_8)));
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

            String newFilename =
                    String.format(
                            "%s.json",
                            base32.encodeToString(
                                    matchExpression.getBytes(StandardCharsets.UTF_8)));

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
}
