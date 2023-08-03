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
package io.cryostat.configuration;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.cryostat.MainModule;
import io.cryostat.core.log.Logger;
import io.cryostat.core.net.Credentials;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.platform.PlatformClient;
import io.cryostat.platform.ServiceRef;
import io.cryostat.rules.MatchExpressionEvaluator;
import io.cryostat.rules.MatchExpressionValidator;

import com.google.gson.Gson;
import org.apache.commons.codec.binary.Base32;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    @Mock PlatformClient platformClient;
    @Mock StoredCredentialsDao dao;
    @Mock FileSystem fs;
    @Mock Logger logger;
    Gson gson = MainModule.provideGson(logger);
    Base32 base32 = new Base32();

    @BeforeEach
    void setup() {
        this.credentialsManager =
                new CredentialsManager(
                        credentialsDir,
                        matchExpressionValidator,
                        () -> matchExpressionEvaluator,
                        platformClient,
                        dao,
                        fs,
                        gson,
                        logger);
    }

    @Test
    void initializesEmpty() throws Exception {
        Mockito.when(platformClient.listDiscoverableServices()).thenReturn(List.of());

        MatcherAssert.assertThat(
                credentialsManager.getServiceRefsWithCredentials(), Matchers.empty());
        MatcherAssert.assertThat(credentialsManager.removeCredentials("foo"), Matchers.lessThan(0));
        MatcherAssert.assertThat(
                credentialsManager.getCredentials(new ServiceRef("id", new URI("foo"), "foo")),
                Matchers.nullValue());
        MatcherAssert.assertThat(
                credentialsManager.getCredentialsByTargetId("foo"), Matchers.nullValue());
    }

    @Test
    void canAddThenGet() throws Exception {
        String targetId = "foo";
        String matchExpression = String.format("target.connectUrl == \"%s\"", targetId);

        String username = "user";
        String password = "pass";
        Credentials credentials = new Credentials(username, password);

        StoredCredentials stored = new StoredCredentials(1, matchExpression, credentials);

        ServiceRef serviceRef = new ServiceRef("id", new URI(targetId), "foo");
        Mockito.when(matchExpressionEvaluator.applies(matchExpression, serviceRef))
                .thenReturn(true);

        Mockito.when(dao.save(Mockito.any())).thenReturn(stored);

        credentialsManager.addCredentials(matchExpression, credentials);

        Mockito.verify(dao).save(Mockito.any());
        Mockito.when(dao.getAll()).thenReturn(List.of(stored));

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

        MatcherAssert.assertThat(
                credentialsManager.removeCredentials(matchExpression), Matchers.lessThan(0));

        Mockito.when(dao.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));

        credentialsManager.addCredentials(matchExpression, credentials);

        ArgumentCaptor<StoredCredentials> storedCaptor =
                ArgumentCaptor.forClass(StoredCredentials.class);
        Mockito.verify(dao).save(storedCaptor.capture());

        StoredCredentials stored = storedCaptor.getValue();
        MatcherAssert.assertThat(stored.getMatchExpression(), Matchers.equalTo(matchExpression));
        MatcherAssert.assertThat(stored.getCredentials(), Matchers.equalTo(credentials));

        Mockito.when(dao.getAll()).thenReturn(List.of(stored));

        MatcherAssert.assertThat(
                credentialsManager.removeCredentials(matchExpression),
                Matchers.greaterThanOrEqualTo(0));
    }

    @Test
    void canAddThenRemoveMultiple() throws Exception {
        String targetId1 = "foo";
        String targetId2 = "bar";
        String matchExpression1 = String.format("target.connectUrl == \"%s\"", targetId1);
        String matchExpression2 = String.format("target.connectUrl == \"%s\"", targetId2);

        String username = "user";
        String password = "pass";
        Credentials credentials = new Credentials(username, password);
        StoredCredentials stored1 = new StoredCredentials(1, matchExpression1, credentials);
        StoredCredentials stored2 = new StoredCredentials(2, matchExpression2, credentials);

        Mockito.when(dao.getAll()).thenReturn(List.of());

        MatcherAssert.assertThat(
                credentialsManager.removeCredentials(matchExpression1), Matchers.lessThan(0));

        MatcherAssert.assertThat(
                credentialsManager.removeCredentials(matchExpression2), Matchers.lessThan(0));
        Mockito.when(dao.getAll()).thenReturn(List.of(stored1));

        MatcherAssert.assertThat(
                credentialsManager.removeCredentials(matchExpression1),
                Matchers.greaterThanOrEqualTo(0));

        Mockito.when(dao.getAll()).thenReturn(List.of());

        MatcherAssert.assertThat(
                credentialsManager.removeCredentials(matchExpression2), Matchers.lessThan(0));
        Mockito.when(dao.getAll()).thenReturn(List.of(stored2));

        MatcherAssert.assertThat(
                credentialsManager.removeCredentials(matchExpression2),
                Matchers.greaterThanOrEqualTo(0));

        Mockito.when(dao.getAll()).thenReturn(List.of());

        MatcherAssert.assertThat(
                credentialsManager.removeCredentials(matchExpression2), Matchers.lessThan(0));
    }

    @Test
    void addedCredentialsCanMatchMultipleTargets() throws Exception {
        ServiceRef target1 = new ServiceRef("id1", new URI("target1"), "target1Alias");
        ServiceRef target2 = new ServiceRef("id2", new URI("target2"), "target2Alias");
        ServiceRef target3 = new ServiceRef("id3", new URI("target3"), "target3Alias");
        ServiceRef target4 = new ServiceRef("id4", new URI("target4"), "target4Alias");

        Mockito.when(platformClient.listDiscoverableServices())
                .thenReturn(List.of(target1, target2, target3, target4));

        String matchExpression = "some expression";
        String username = "user";
        String password = "pass";
        Credentials credentials = new Credentials(username, password);

        StoredCredentials stored = new StoredCredentials(1, matchExpression, credentials);
        Mockito.when(dao.getAll()).thenReturn(List.of(stored));

        Mockito.when(matchExpressionEvaluator.applies(Mockito.eq(matchExpression), Mockito.any()))
                .thenAnswer(
                        invocation -> Set.of(target1, target2).contains(invocation.getArgument(1)));

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
        ServiceRef target1 = new ServiceRef("id1", new URI("target1"), "target1Alias");
        ServiceRef target2 = new ServiceRef("id2", new URI("target2"), "target2Alias");
        ServiceRef target3 = new ServiceRef("id3", new URI("target3"), "target3Alias");
        ServiceRef target4 = new ServiceRef("id4", new URI("target4"), "target4Alias");

        Mockito.when(platformClient.listDiscoverableServices())
                .thenReturn(List.of(target1, target2, target3, target4));

        String matchExpression = "some expression";
        String username = "user";
        String password = "pass";
        Credentials credentials = new Credentials(username, password);

        StoredCredentials stored = new StoredCredentials(1, matchExpression, credentials);
        Mockito.when(dao.getAll()).thenReturn(List.of(stored));

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

        MatcherAssert.assertThat(
                credentialsManager.getServiceRefsWithCredentials(),
                Matchers.equalTo(List.of(target1, target2)));
    }

    @Test
    void canQueryMatchExpressions() throws Exception {
        String matchExpression = "some expression";
        String username = "user";
        String password = "pass";
        Credentials credentials = new Credentials(username, password);

        StoredCredentials stored = new StoredCredentials(5, matchExpression, credentials);
        Mockito.when(dao.getAll()).thenReturn(List.of(stored));

        Map<Integer, String> expected = Map.of(5, matchExpression);
        MatcherAssert.assertThat(credentialsManager.getAll(), Matchers.equalTo(expected));
    }

    @Test
    void canResolveMatchExpressions() throws Exception {
        String matchExpression = "some expression";
        String username = "user";
        String password = "pass";
        Credentials credentials = new Credentials(username, password);

        ServiceRef serviceRef =
                new ServiceRef(
                        "id",
                        URI.create("service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi"),
                        "mytarget");

        Mockito.when(platformClient.listDiscoverableServices()).thenReturn(List.of(serviceRef));
        Mockito.when(matchExpressionEvaluator.applies(matchExpression, serviceRef))
                .thenReturn(true);

        Set<ServiceRef> expected = Set.of(serviceRef);

        StoredCredentials stored = new StoredCredentials(7, matchExpression, credentials);
        Mockito.when(dao.get(Mockito.anyInt())).thenReturn(Optional.of(stored));

        MatcherAssert.assertThat(
                credentialsManager.resolveMatchingTargets(7), Matchers.equalTo(expected));
    }
}
