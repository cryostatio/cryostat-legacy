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
