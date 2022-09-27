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

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.cryostat.core.log.Logger;
import io.cryostat.core.net.Credentials;
import io.cryostat.messaging.notifications.NotificationFactory;
import io.cryostat.platform.PlatformClient;
import io.cryostat.platform.ServiceRef;
import io.cryostat.rules.MatchExpressionEvaluator;
import io.cryostat.rules.MatchExpressionValidator;

import org.apache.commons.codec.binary.Base32;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
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
    @Mock MatchExpressionValidator matchExpressionValidator;
    @Mock MatchExpressionEvaluator matchExpressionEvaluator;
    @Mock PlatformClient platformClient;
    @Mock StoredCredentialsDao dao;
    @Mock NotificationFactory notificationFactory;
    @Mock Logger logger;
    Base32 base32 = new Base32();

    @BeforeEach
    void setup() {
        this.credentialsManager =
                new CredentialsManager(
                        matchExpressionValidator,
                        matchExpressionEvaluator,
                        platformClient,
                        dao,
                        notificationFactory,
                        logger);
    }

    @Test
    void initializesEmpty() throws Exception {
        Mockito.when(platformClient.listDiscoverableServices()).thenReturn(List.of());

        MatcherAssert.assertThat(
                credentialsManager.getServiceRefsWithCredentials(), Matchers.empty());
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> credentialsManager.removeCredentials("foo"));
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

        String username = "user";
        String password = "pass";
        Credentials credentials = new Credentials(username, password);

        StoredCredentials stored = new StoredCredentials(1, matchExpression, credentials);

        ServiceRef serviceRef = new ServiceRef(new URI(targetId), "foo");
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

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> credentialsManager.removeCredentials(matchExpression));

        Mockito.when(dao.save(Mockito.any())).thenAnswer(invocation -> invocation.getArgument(0));

        credentialsManager.addCredentials(matchExpression, credentials);

        ArgumentCaptor<StoredCredentials> storedCaptor =
                ArgumentCaptor.forClass(StoredCredentials.class);
        Mockito.verify(dao).save(storedCaptor.capture());

        StoredCredentials stored = storedCaptor.getValue();
        MatcherAssert.assertThat(stored.getMatchExpression(), Matchers.equalTo(matchExpression));
        MatcherAssert.assertThat(stored.getCredentials(), Matchers.equalTo(credentials));

        Mockito.when(dao.deleteByMatchExpression(Mockito.eq(matchExpression))).thenReturn(1);

        Assertions.assertDoesNotThrow(() -> credentialsManager.removeCredentials(matchExpression));

        Mockito.verify(dao, Mockito.times(2)).deleteByMatchExpression(matchExpression);
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

        Mockito.when(dao.deleteByMatchExpression(Mockito.anyString())).thenReturn(0);

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> credentialsManager.removeCredentials(matchExpression1));

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> credentialsManager.removeCredentials(matchExpression2));

        Mockito.when(dao.deleteByMatchExpression(Mockito.anyString())).thenReturn(1);

        Assertions.assertDoesNotThrow(() -> credentialsManager.removeCredentials(matchExpression1));

        Mockito.when(dao.deleteByMatchExpression(Mockito.anyString())).thenReturn(0);

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> credentialsManager.removeCredentials(matchExpression1));

        Mockito.when(dao.deleteByMatchExpression(Mockito.anyString())).thenReturn(1);

        Assertions.assertDoesNotThrow(() -> credentialsManager.removeCredentials(matchExpression2));

        Mockito.when(dao.deleteByMatchExpression(Mockito.anyString())).thenReturn(0);

        Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> credentialsManager.removeCredentials(matchExpression2));
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
