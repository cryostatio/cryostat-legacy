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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import io.cryostat.MainModule;
import io.cryostat.core.log.Logger;
import io.cryostat.core.sys.FileSystem;
import io.cryostat.platform.ServiceRef;
import io.cryostat.rules.RuleRegistry.RuleEvent;
import io.cryostat.util.events.Event;

import com.google.gson.Gson;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RuleRegistryTest {

    RuleRegistry registry;
    @Mock Path rulesDir;
    @Mock MatchExpressionEvaluator matchExpressionEvaluator;
    @Mock FileSystem fs;
    @Mock Logger logger;
    Gson gson = Mockito.spy(MainModule.provideGson(logger));

    Rule testRule;
    String ruleJson;
    BufferedReader fileReader;

    @BeforeEach
    void setup() throws Exception {
        this.registry = new RuleRegistry(rulesDir, matchExpressionEvaluator, fs, gson, logger);
        this.testRule =
                new Rule.Builder()
                        .name("test rule")
                        .matchExpression("target.alias == 'com.example.App'")
                        .description("a simple test rule")
                        .eventSpecifier("template=Continuous")
                        .preservedArchives(5)
                        .archivalPeriodSeconds(1234)
                        .maxSizeBytes(56)
                        .maxAgeSeconds(78)
                        .enabled(true)
                        .build();
        this.ruleJson = MainModule.provideGson(logger).toJson(testRule);
        this.fileReader = new BufferedReader(new StringReader(ruleJson));
    }

    @Test
    void loadRulesShouldDoNothingIfFileEmpty() throws Exception {
        Mockito.when(fs.listDirectoryChildren(Mockito.any())).thenReturn(List.of());

        registry.loadRules();

        Mockito.verify(fs).listDirectoryChildren(rulesDir);
        Mockito.verifyNoMoreInteractions(fs);
        Mockito.verifyNoInteractions(gson);
    }

    @Test
    void testLoadRulesSkipsFilesWhenExceptionThrown() throws Exception {
        Path rulePath = Mockito.mock(Path.class);
        Mockito.when(rulesDir.resolve(Mockito.anyString())).thenReturn(rulePath);
        Mockito.when(fs.listDirectoryChildren(Mockito.any()))
                .thenReturn(List.of(testRule.getName()));
        Mockito.when(fs.readFile(rulePath)).thenThrow(IOException.class);

        registry.loadRules();

        Mockito.verify(fs).listDirectoryChildren(rulesDir);
        Mockito.verify(fs).readFile(rulePath);
        Mockito.verifyNoInteractions(gson);
    }

    @Test
    void testAddRule() throws Exception {
        Path rulePath = Mockito.mock(Path.class);
        Mockito.when(rulesDir.resolve(Mockito.anyString())).thenReturn(rulePath);
        Mockito.when(fs.listDirectoryChildren(rulesDir)).thenReturn(List.of("test_rule.json"));
        Mockito.when(fs.readFile(rulePath)).thenReturn(fileReader);

        CompletableFuture<Event<RuleEvent, Rule>> eventListener = new CompletableFuture<>();
        registry.addListener(eventListener::complete);

        registry.addRule(testRule);

        InOrder inOrder = Mockito.inOrder(gson, fs);

        inOrder.verify(gson).toJson(testRule);
        inOrder.verify(fs)
                .writeString(
                        rulePath,
                        ruleJson,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
        inOrder.verify(fs).listDirectoryChildren(rulesDir);

        Event<RuleEvent, Rule> event = eventListener.get(1, TimeUnit.SECONDS);
        MatcherAssert.assertThat(event.getEventType(), Matchers.equalTo(RuleEvent.ADDED));
        MatcherAssert.assertThat(event.getPayload(), Matchers.sameInstance(testRule));
    }

    @Test
    void testAddRulePropagatesException() throws Exception {
        Path rulePath = Mockito.mock(Path.class);
        Mockito.when(rulesDir.resolve(Mockito.anyString())).thenReturn(rulePath);
        Mockito.when(
                        fs.writeString(
                                Mockito.any(Path.class),
                                Mockito.any(String.class),
                                Mockito.eq(StandardOpenOption.WRITE),
                                Mockito.eq(StandardOpenOption.CREATE),
                                Mockito.eq(StandardOpenOption.TRUNCATE_EXISTING)))
                .thenThrow(IOException.class);

        Assertions.assertThrows(IOException.class, () -> registry.addRule(testRule));
    }

    @Test
    void testAddRuleThrowsExceptionOnDuplicateName() throws Exception {
        Path rulePath = Mockito.mock(Path.class);
        Mockito.when(rulesDir.resolve(Mockito.anyString())).thenReturn(rulePath);
        Mockito.when(fs.listDirectoryChildren(rulesDir)).thenReturn(List.of("test_rule.json"));
        Mockito.when(fs.readFile(rulePath)).thenReturn(fileReader);

        registry.loadRules();

        Assertions.assertThrows(IOException.class, () -> registry.addRule(testRule));
    }

    @Test
    void testAddRuleAllowsDuplicateNameOnArchivers() throws Exception {
        Path rulePath = Mockito.mock(Path.class);
        Mockito.when(rulesDir.resolve(Mockito.anyString())).thenReturn(rulePath);
        Mockito.when(fs.listDirectoryChildren(rulesDir)).thenReturn(List.of("test_rule.json"));
        Mockito.when(fs.readFile(rulePath)).thenReturn(fileReader);

        registry.loadRules();

        Rule archiver =
                new Rule.Builder()
                        .name(testRule.getName())
                        .matchExpression(testRule.getMatchExpression())
                        .description(testRule.getDescription())
                        .eventSpecifier("archive")
                        .build();

        CompletableFuture<Event<RuleEvent, Rule>> eventListener = new CompletableFuture<>();
        registry.addListener(eventListener::complete);

        Assertions.assertDoesNotThrow(() -> registry.addRule(archiver));
        Event<RuleEvent, Rule> event = eventListener.get(1, TimeUnit.SECONDS);
        MatcherAssert.assertThat(event.getEventType(), Matchers.equalTo(RuleEvent.ADDED));
        MatcherAssert.assertThat(event.getPayload(), Matchers.sameInstance(archiver));
    }

    @Test
    void testGetRulebyName() throws Exception {
        Path rulePath = Mockito.mock(Path.class);
        Mockito.when(rulesDir.resolve(Mockito.anyString())).thenReturn(rulePath);
        Mockito.when(fs.listDirectoryChildren(rulesDir)).thenReturn(List.of("test_rule.json"));
        Mockito.when(fs.readFile(rulePath)).thenReturn(fileReader);

        registry.addRule(testRule);
        Optional<Rule> getResult = registry.getRule("test_rule");
        MatcherAssert.assertThat(getResult.get(), Matchers.equalTo(testRule));
    }

    @Test
    void testGetAllRules() throws Exception {
        Path rulePath = Mockito.mock(Path.class);
        Mockito.when(rulesDir.resolve(Mockito.anyString())).thenReturn(rulePath);
        Mockito.when(fs.listDirectoryChildren(rulesDir)).thenReturn(List.of("test_rule.json"));
        Mockito.when(fs.readFile(rulePath)).thenReturn(fileReader);

        registry.addRule(testRule);

        MatcherAssert.assertThat(registry.getRules(), Matchers.equalTo(Set.of(testRule)));
    }

    @Test
    void testGetRulesByServiceRef() throws Exception {
        Path rulePath = Mockito.mock(Path.class);
        Mockito.when(rulesDir.resolve(Mockito.anyString())).thenReturn(rulePath);
        Mockito.when(fs.listDirectoryChildren(rulesDir)).thenReturn(List.of("test_rule.json"));
        Mockito.when(fs.readFile(rulePath)).thenReturn(fileReader);

        Mockito.when(matchExpressionEvaluator.applies(Mockito.any(), Mockito.any()))
                .thenReturn(true);

        registry.addRule(testRule);

        MatcherAssert.assertThat(
                registry.getRules(
                        new ServiceRef(
                                URI.create("service:jmx:rmi:///jndi/rmi://app:9091/jmxrmi"),
                                "com.example.App")),
                Matchers.equalTo(Set.of(testRule)));
    }

    @Test
    void testGetRulesByServiceRefIgnoresArchivers() throws Exception {
        Rule archiverRule =
                new Rule.Builder()
                        .name(testRule.getName())
                        .matchExpression(testRule.getMatchExpression())
                        .description(testRule.getDescription())
                        .eventSpecifier("archive")
                        .build();

        registry.addRule(archiverRule);

        MatcherAssert.assertThat(
                registry.getRules(
                        new ServiceRef(
                                URI.create("service:jmx:rmi:///jndi/rmi://app:9091/jmxrmi"),
                                "com.example.App")),
                Matchers.equalTo(Set.of()));
    }

    @Test
    void testGetRulesReturnsCopy() throws Exception {
        Path rulePath = Mockito.mock(Path.class);
        Mockito.when(rulesDir.resolve(Mockito.anyString())).thenReturn(rulePath);
        Mockito.when(fs.listDirectoryChildren(rulesDir)).thenReturn(List.of("test_rule.json"));
        Mockito.when(fs.readFile(rulePath)).thenReturn(fileReader);

        registry.addRule(testRule);

        Set<Rule> firstSet = registry.getRules();
        firstSet.clear();
        Set<Rule> secondSet = registry.getRules();
        MatcherAssert.assertThat(secondSet, Matchers.not(Matchers.equalTo(firstSet)));
        MatcherAssert.assertThat(secondSet, Matchers.equalTo(Set.of(testRule)));
    }

    @Test
    void testDeleteRuleDoesNothingIfNoneAdded() throws Exception {
        registry.deleteRule(testRule.getName());
        Mockito.verifyNoInteractions(gson);
        Mockito.verify(fs).listDirectoryChildren(Mockito.any());
        Mockito.verifyNoMoreInteractions(fs);
        Mockito.verifyNoInteractions(rulesDir);
    }

    @Test
    void testDelete() throws Exception {
        Path rulePath = Mockito.mock(Path.class);
        Mockito.when(rulesDir.resolve(Mockito.anyString())).thenReturn(rulePath);
        Mockito.when(fs.listDirectoryChildren(rulesDir)).thenReturn(List.of("test_rule.json"));
        Mockito.when(fs.readFile(rulePath)).thenReturn(fileReader);

        registry.addRule(testRule);

        registry.deleteRule(testRule.getName());

        MatcherAssert.assertThat(registry.getRules(), Matchers.emptyCollectionOf(Rule.class));
    }

    @Test
    void testDeletePropagatesListingException() throws Exception {
        Mockito.when(fs.listDirectoryChildren(Mockito.any())).thenThrow(IOException.class);

        Assertions.assertThrows(IOException.class, () -> registry.deleteRule(testRule.getName()));
    }

    @Test
    void testDeletePropagatesFileDeletionException() throws Exception {
        Path rulePath = Mockito.mock(Path.class);
        Mockito.when(rulesDir.resolve(Mockito.anyString())).thenReturn(rulePath);
        Mockito.when(fs.listDirectoryChildren(rulesDir)).thenReturn(List.of("test_rule.json"));
        Mockito.when(fs.readFile(rulePath)).thenReturn(fileReader);
        Mockito.when(fs.deleteIfExists(rulePath)).thenThrow(IOException.class);

        registry.addRule(testRule);

        Assertions.assertThrows(IOException.class, () -> registry.deleteRule(testRule.getName()));
    }

    @Test
    void testEnable() throws Exception {

        String name = "test rule";

        Rule rule =
                new Rule.Builder()
                        .name(name)
                        .matchExpression("target.alias == 'com.example.App'")
                        .description("a simple test rule")
                        .eventSpecifier("template=Continuous")
                        .preservedArchives(5)
                        .archivalPeriodSeconds(1234)
                        .maxSizeBytes(56)
                        .maxAgeSeconds(78)
                        .enabled(false)
                        .build();

        Path rulePath = Mockito.mock(Path.class);
        Mockito.when(rulesDir.resolve(Mockito.anyString())).thenReturn(rulePath);
        Mockito.when(fs.listDirectoryChildren(rulesDir)).thenReturn(List.of("test_rule.json"));
        Mockito.when(fs.readFile(rulePath)).thenReturn(fileReader);

        registry.addRule(rule);
        registry.enableRule(rule, true);

        MatcherAssert.assertThat(
                registry.getRule(testRule.getName()).get().isEnabled(), Matchers.is(true));
    }
}
