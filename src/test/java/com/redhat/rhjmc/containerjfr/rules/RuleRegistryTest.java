/*-
 * #%L
 * Container JFR
 * %%
 * Copyright (C) 2020 Red Hat, Inc.
 * %%
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
 * #L%
 */
package com.redhat.rhjmc.containerjfr.rules;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
import com.google.gson.Gson;

import com.redhat.rhjmc.containerjfr.MainModule;
import com.redhat.rhjmc.containerjfr.core.log.Logger;
import com.redhat.rhjmc.containerjfr.core.sys.FileSystem;
import com.redhat.rhjmc.containerjfr.platform.ServiceRef;

@ExtendWith(MockitoExtension.class)
class RuleRegistryTest {

    RuleRegistry registry;
    @Mock Path rulesDir;
    @Mock FileSystem fs;
    @Mock Logger logger;
    Gson gson = Mockito.spy(MainModule.provideGson(logger));

    static final Rule TEST_RULE =
            new Rule.Builder()
                    .name("test rule")
                    .targetAlias("com.example.App")
                    .description("a simple test rule")
                    .eventSpecifier("template=Continuous")
                    .preservedArchives(5)
                    .archivalPeriodSeconds(1234)
                    .maxSizeBytes(56)
                    .maxAgeSeconds(78)
                    .build();
    final String ruleJson = MainModule.provideGson(logger).toJson(TEST_RULE);
    final BufferedReader fileReader = new BufferedReader(new StringReader(ruleJson));

    @BeforeEach
    void setup() {
        this.registry = new RuleRegistry(rulesDir, fs, gson, logger);
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
                .thenReturn(List.of(TEST_RULE.getName()));
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

        registry.addRule(TEST_RULE);

        InOrder inOrder = Mockito.inOrder(gson, fs);

        inOrder.verify(gson).toJson(TEST_RULE);
        inOrder.verify(fs)
                .writeString(
                        rulePath,
                        ruleJson,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
        inOrder.verify(fs).listDirectoryChildren(rulesDir);
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

        Assertions.assertThrows(IOException.class, () -> registry.addRule(TEST_RULE));
    }

    @Test
    void testAddRuleThrowsExceptionOnDuplicateName() throws Exception {
        Path rulePath = Mockito.mock(Path.class);
        Mockito.when(rulesDir.resolve(Mockito.anyString())).thenReturn(rulePath);
        Mockito.when(fs.listDirectoryChildren(rulesDir)).thenReturn(List.of("test_rule.json"));
        Mockito.when(fs.readFile(rulePath)).thenReturn(fileReader);

        registry.loadRules();

        Assertions.assertThrows(IOException.class, () -> registry.addRule(TEST_RULE));
    }

    @Test
    void testGetRulebyName() throws Exception {
        Path rulePath = Mockito.mock(Path.class);
        Mockito.when(rulesDir.resolve(Mockito.anyString())).thenReturn(rulePath);
        Mockito.when(fs.listDirectoryChildren(rulesDir)).thenReturn(List.of("test_rule.json"));
        Mockito.when(fs.readFile(rulePath)).thenReturn(fileReader);

        registry.addRule(TEST_RULE);
        Optional<Rule> getResult = registry.getRule("test_rule");
        MatcherAssert.assertThat(getResult.get(), Matchers.equalTo(TEST_RULE));
    }

    @Test
    void testGetAllRules() throws Exception {
        Path rulePath = Mockito.mock(Path.class);
        Mockito.when(rulesDir.resolve(Mockito.anyString())).thenReturn(rulePath);
        Mockito.when(fs.listDirectoryChildren(rulesDir)).thenReturn(List.of("test_rule.json"));
        Mockito.when(fs.readFile(rulePath)).thenReturn(fileReader);

        registry.addRule(TEST_RULE);

        MatcherAssert.assertThat(registry.getRules(), Matchers.equalTo(Set.of(TEST_RULE)));
    }

    @Test
    void testGetRulesByServiceRef() throws Exception {
        Path rulePath = Mockito.mock(Path.class);
        Mockito.when(rulesDir.resolve(Mockito.anyString())).thenReturn(rulePath);
        Mockito.when(fs.listDirectoryChildren(rulesDir)).thenReturn(List.of("test_rule.json"));
        Mockito.when(fs.readFile(rulePath)).thenReturn(fileReader);

        registry.addRule(TEST_RULE);

        MatcherAssert.assertThat(
                registry.getRules(new ServiceRef(null, "com.example.App")),
                Matchers.equalTo(Set.of(TEST_RULE)));
    }

    @Test
    void testGetRulesReturnsCopy() throws Exception {
        Path rulePath = Mockito.mock(Path.class);
        Mockito.when(rulesDir.resolve(Mockito.anyString())).thenReturn(rulePath);
        Mockito.when(fs.listDirectoryChildren(rulesDir)).thenReturn(List.of("test_rule.json"));
        Mockito.when(fs.readFile(rulePath)).thenReturn(fileReader);

        registry.addRule(TEST_RULE);

        Set<Rule> firstSet = registry.getRules();
        firstSet.clear();
        Set<Rule> secondSet = registry.getRules();
        MatcherAssert.assertThat(secondSet, Matchers.not(Matchers.equalTo(firstSet)));
        MatcherAssert.assertThat(secondSet, Matchers.equalTo(Set.of(TEST_RULE)));
    }

    @Test
    void testDeleteRuleDoesNothingIfNoneAdded() throws Exception {
        registry.deleteRule(TEST_RULE.getName());
        Mockito.verifyNoInteractions(gson);
        Mockito.verify(fs).listDirectoryChildren(rulesDir);
        Mockito.verifyNoMoreInteractions(fs);
        Mockito.verifyNoInteractions(rulesDir);
    }

    @Test
    void testDelete() throws Exception {
        Path rulePath = Mockito.mock(Path.class);
        Mockito.when(rulesDir.resolve(Mockito.anyString())).thenReturn(rulePath);
        Mockito.when(fs.listDirectoryChildren(rulesDir)).thenReturn(List.of("test_rule.json"));
        Mockito.when(fs.readFile(rulePath)).thenReturn(fileReader);

        registry.addRule(TEST_RULE);

        registry.deleteRule(TEST_RULE.getName());

        MatcherAssert.assertThat(registry.getRules(), Matchers.emptyCollectionOf(Rule.class));
    }

    @Test
    void testDeletePropagatesListingException() throws Exception {
        Mockito.when(fs.listDirectoryChildren(Mockito.any())).thenThrow(IOException.class);

        Assertions.assertThrows(IOException.class, () -> registry.deleteRule(TEST_RULE.getName()));
    }

    @Test
    void testDeleteLogsFileDeletionException() throws Exception {
        Path rulePath = Mockito.mock(Path.class);
        Mockito.when(rulesDir.resolve(Mockito.anyString())).thenReturn(rulePath);
        Mockito.when(fs.listDirectoryChildren(rulesDir)).thenReturn(List.of("test_rule.json"));
        Mockito.when(fs.readFile(rulePath)).thenReturn(fileReader);
        Mockito.when(fs.deleteIfExists(rulePath)).thenThrow(IOException.class);

        registry.addRule(TEST_RULE);

        registry.deleteRule(TEST_RULE.getName());

        Mockito.verify(logger).warn(Mockito.any(IOException.class));
    }
}
