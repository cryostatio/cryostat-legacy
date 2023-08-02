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
package io.cryostat.rules;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.cryostat.MainModule;
import io.cryostat.core.log.Logger;
import io.cryostat.platform.PlatformClient;
import io.cryostat.platform.ServiceRef;

import com.google.gson.Gson;
import org.apache.commons.codec.binary.Base32;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MatchExpressionManagerTest {

    MatchExpressionManager expressionManager;
    @Mock Path credentialsDir;
    @Mock MatchExpressionValidator matchExpressionValidator;
    @Mock MatchExpressionEvaluator matchExpressionEvaluator;
    @Mock PlatformClient platformClient;
    @Mock MatchExpressionDao dao;
    @Mock Logger logger;
    Gson gson = MainModule.provideGson(logger);
    Base32 base32 = new Base32();

    @BeforeEach
    void setup() {
        this.expressionManager =
                new MatchExpressionManager(
                        matchExpressionValidator,
                        () -> matchExpressionEvaluator,
                        platformClient,
                        dao,
                        gson,
                        logger);
    }

    @Test
    void canAddThenGetThenRemove() throws Exception {
        String targetId = "foo";
        String matchExpression = String.format("target.connectUrl == \"%s\"", targetId);

        MatchExpression stored = new MatchExpression(10, matchExpression);

        Mockito.when(dao.save(Mockito.any())).thenReturn(stored);

        expressionManager.addMatchExpression(matchExpression);

        Mockito.verify(dao).save(Mockito.any());

        Mockito.when(dao.get(10)).thenReturn(Optional.of(stored));
        Optional<MatchExpression> found = expressionManager.get(10);

        MatcherAssert.assertThat(found.get(), Matchers.equalTo(stored));

        Mockito.when(dao.delete(10)).thenReturn(true);
        boolean removed = expressionManager.delete(10);

        Assertions.assertTrue(removed);
    }

    @Test
    void throwMatchExpressionExceptionOnInvalidExpression() throws Exception {
        String matchExpression = "invalid expression";

        Mockito.when(matchExpressionValidator.validate(matchExpression))
                .thenThrow(MatchExpressionValidationException.class);

        Assertions.assertThrows(
                MatchExpressionValidationException.class,
                () -> expressionManager.addMatchExpression(matchExpression));
    }

    @Test
    void canResolveMatchExpressions() throws Exception {
        String matchExpression = "some expression";
        MatchExpression stored = new MatchExpression(7, matchExpression);

        ServiceRef serviceRef =
                new ServiceRef(
                        "id",
                        URI.create("service:jmx:rmi:///jndi/rmi://cryostat:9091/jmxrmi"),
                        "mytarget");

        Mockito.when(platformClient.listDiscoverableServices()).thenReturn(List.of(serviceRef));
        Mockito.when(matchExpressionEvaluator.applies(matchExpression, serviceRef))
                .thenReturn(true);

        Set<ServiceRef> expected = Set.of(serviceRef);

        Mockito.when(dao.get(Mockito.anyInt())).thenReturn(Optional.of(stored));

        MatcherAssert.assertThat(
                expressionManager.resolveMatchingTargets(7), Matchers.equalTo(expected));
    }
}
