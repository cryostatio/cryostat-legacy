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
package com.redhat.rhjmc.containerjfr.commands.internal;

import java.util.List;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import com.redhat.rhjmc.containerjfr.TestException;
import com.redhat.rhjmc.containerjfr.commands.Command;
import com.redhat.rhjmc.containerjfr.commands.SerializableCommand;
import com.redhat.rhjmc.containerjfr.commands.SerializableCommand.ListOutput;
import com.redhat.rhjmc.containerjfr.commands.SerializableCommand.Output;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.templates.Template;
import com.redhat.rhjmc.containerjfr.core.templates.TemplateService;
import com.redhat.rhjmc.containerjfr.core.templates.TemplateType;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;
import com.redhat.rhjmc.containerjfr.net.TargetConnectionManager;
import com.redhat.rhjmc.containerjfr.net.TargetConnectionManager.ConnectedTask;

@ExtendWith(MockitoExtension.class)
class ListEventTemplatesCommandTest implements ValidatesTargetId {

    ListEventTemplatesCommand cmd;
    @Mock TargetConnectionManager targetConnectionManager;
    @Mock JFRConnection connection;
    @Mock TemplateService templateSvc;
    @Mock ClientWriter cw;

    @Override
    public Command commandForValidationTesting() {
        return cmd;
    }

    @Override
    public List<String> argumentSignature() {
        return List.of(TARGET_ID);
    }

    @BeforeEach
    void setup() {
        cmd = new ListEventTemplatesCommand(cw, targetConnectionManager);
    }

    @Test
    void shouldBeNamedListEventTemplates() {
        MatcherAssert.assertThat(cmd.getName(), Matchers.equalTo("list-event-templates"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 2})
    void shouldNotValidateWrongArgc(int n) {
        Assertions.assertFalse(cmd.validate(new String[n]));
    }

    @Test
    void executeShouldPrintListOfTemplateNames() throws Exception {
        Mockito.when(
                        targetConnectionManager.executeConnectedTask(
                                Mockito.anyString(), Mockito.any()))
                .thenAnswer(
                        arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(connection));
        Mockito.when(connection.getTemplateService()).thenReturn(templateSvc);
        Template foo = new Template("Foo", "a foo-ing template", "Foo Inc.", TemplateType.TARGET);
        Template bar = new Template("Bar", "a bar-ing template", "Bar Inc.", TemplateType.TARGET);
        Template baz = new Template("Baz", "a baz-ing template", "Baz Inc.", TemplateType.CUSTOM);
        Mockito.when(templateSvc.getTemplates()).thenReturn(List.of(foo, bar, baz));

        cmd.execute(new String[] {"fooHost:9091"});

        InOrder inOrder = Mockito.inOrder(cw);
        inOrder.verify(cw).println("Available recording templates:");
        inOrder.verify(cw).println("\t[Foo Inc.]\tFoo:\ta foo-ing template");
        inOrder.verify(cw).println("\t[Bar Inc.]\tBar:\ta bar-ing template");
        inOrder.verify(cw).println("\t[Baz Inc.]\tBaz:\ta baz-ing template");
        inOrder.verify(cw)
                .println(
                        "\t[ContainerJFR]\tALL:\tEnable all available events in the target JVM, with default option values. This will be very expensive and is intended primarily for testing ContainerJFR's own capabilities.");
    }

    @Test
    void serializableExecuteShouldReturnListOfTemplateNames() throws Exception {
        Mockito.when(
                        targetConnectionManager.executeConnectedTask(
                                Mockito.anyString(), Mockito.any()))
                .thenAnswer(
                        arg0 -> ((ConnectedTask<Object>) arg0.getArgument(1)).execute(connection));
        Mockito.when(connection.getTemplateService()).thenReturn(templateSvc);
        Template foo = new Template("Foo", "a foo-ing template", "Foo Inc.", TemplateType.TARGET);
        Template bar = new Template("Bar", "a bar-ing template", "Bar Inc.", TemplateType.TARGET);
        Template baz = new Template("Baz", "a baz-ing template", "Baz Inc.", TemplateType.CUSTOM);
        List<Template> remoteList = List.of(foo, bar, baz);
        Mockito.when(templateSvc.getTemplates()).thenReturn(remoteList);

        Output<?> output = cmd.serializableExecute(new String[] {"fooHost:9091"});

        MatcherAssert.assertThat(output, Matchers.instanceOf(ListOutput.class));
        List<Template> expectedList =
                List.of(foo, bar, baz, AbstractRecordingCommand.ALL_EVENTS_TEMPLATE);
        List<Template> list = ((ListOutput<Template>) output).getPayload();
        MatcherAssert.assertThat(list, Matchers.equalTo(expectedList));
    }

    @Test
    void serializableExecuteShouldReturnExceptionOutputIfThrows() throws Exception {
        Mockito.when(
                        targetConnectionManager.executeConnectedTask(
                                Mockito.anyString(), Mockito.any()))
                .thenThrow(TestException.class);

        Output<?> output = cmd.serializableExecute(new String[] {"fooHost:9091"});
        MatcherAssert.assertThat(
                output, Matchers.instanceOf(SerializableCommand.ExceptionOutput.class));
    }
}
