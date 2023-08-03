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
        this.registry =
                new RuleRegistry(rulesDir, () -> matchExpressionEvaluator, fs, gson, logger);
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

        registry.addRule(testRule);
        Optional<Rule> getResult = registry.getRule("test_rule");
        MatcherAssert.assertThat(getResult.get(), Matchers.equalTo(testRule));
    }

    @Test
    void testGetAllRules() throws Exception {
        Path rulePath = Mockito.mock(Path.class);
        Mockito.when(rulesDir.resolve(Mockito.anyString())).thenReturn(rulePath);

        registry.addRule(testRule);

        MatcherAssert.assertThat(registry.getRules(), Matchers.equalTo(Set.of(testRule)));
    }

    @Test
    void testGetRulesByServiceRef() throws Exception {
        Mockito.when(matchExpressionEvaluator.applies(Mockito.any(), Mockito.any()))
                .thenReturn(true);

        registry.addRule(testRule);

        MatcherAssert.assertThat(
                registry.getRules(
                        new ServiceRef(
                                "id",
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
                                "id",
                                URI.create("service:jmx:rmi:///jndi/rmi://app:9091/jmxrmi"),
                                "com.example.App")),
                Matchers.equalTo(Set.of()));
    }

    @Test
    void testGetRulesReturnsCopy() throws Exception {
        Path rulePath = Mockito.mock(Path.class);
        Mockito.when(rulesDir.resolve(Mockito.anyString())).thenReturn(rulePath);

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
        Mockito.when(fs.listDirectoryChildren(rulesDir)).thenReturn(List.of("test_rule.json"));
        Mockito.when(rulesDir.resolve(Mockito.anyString())).thenReturn(rulePath);
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
        this.ruleJson = MainModule.provideGson(logger).toJson(rule);
        this.fileReader = new BufferedReader(new StringReader(ruleJson));

        registry.addRule(rule);

        MatcherAssert.assertThat(
                registry.getRule(testRule.getName()).get().isEnabled(), Matchers.is(false));

        registry.enableRule(rule, true);

        MatcherAssert.assertThat(
                registry.getRule(testRule.getName()).get().isEnabled(), Matchers.is(true));
    }
}
