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

import com.redhat.rhjmc.containerjfr.commands.SerializableCommand.ListOutput;
import com.redhat.rhjmc.containerjfr.commands.SerializableCommand.Output;
import com.redhat.rhjmc.containerjfr.core.net.JFRConnection;
import com.redhat.rhjmc.containerjfr.core.templates.Template;
import com.redhat.rhjmc.containerjfr.core.templates.TemplateService;
import com.redhat.rhjmc.containerjfr.core.tui.ClientWriter;

@ExtendWith(MockitoExtension.class)
class ListEventTemplatesCommandTest {

    ListEventTemplatesCommand cmd;
    @Mock JFRConnection connection;
    @Mock TemplateService templateSvc;
    @Mock ClientWriter cw;

    @BeforeEach
    void setup() {
        cmd = new ListEventTemplatesCommand(cw);
    }

    @Test
    void shouldBeNamedListEventTemplates() {
        MatcherAssert.assertThat(cmd.getName(), Matchers.equalTo("list-event-templates"));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void shouldNotValidateWrongArgc(int n) {
        Assertions.assertFalse(cmd.validate(new String[n]));
        Mockito.verify(cw).println("No arguments expected");
    }

    @Test
    void shouldValidateNoArgs() {
        Assertions.assertTrue(cmd.validate(new String[0]));
        Mockito.verifyZeroInteractions(cw);
    }

    @Test
    void executeShouldPrintListOfTemplateNames() throws Exception {
        Mockito.when(connection.getTemplateService()).thenReturn(templateSvc);
        Template foo = new Template("Foo", "a foo-ing template", "Foo Inc.");
        Template bar = new Template("Bar", "a bar-ing template", "Bar Inc.");
        Template baz = new Template("Baz", "a baz-ing template", "Baz Inc.");
        Mockito.when(templateSvc.getTemplates()).thenReturn(List.of(foo, bar, baz));

        cmd.connectionChanged(connection);

        Mockito.verifyZeroInteractions(cw);
        cmd.execute(new String[0]);
        InOrder inOrder = Mockito.inOrder(cw);
        inOrder.verify(cw).println("Available recording templates:");
        inOrder.verify(cw).println("\t[Foo Inc.]\tFoo:\ta foo-ing template");
        inOrder.verify(cw).println("\t[Bar Inc.]\tBar:\ta bar-ing template");
        inOrder.verify(cw).println("\t[Baz Inc.]\tBaz:\ta baz-ing template");
        Mockito.verifyNoMoreInteractions(cw);
    }

    @Test
    void serializableExecuteShouldReturnListOfTemplateNames() throws Exception {
        Mockito.when(connection.getTemplateService()).thenReturn(templateSvc);
        Template foo = new Template("Foo", "a foo-ing template", "Foo Inc.");
        Template bar = new Template("Bar", "a bar-ing template", "Bar Inc.");
        Template baz = new Template("Baz", "a baz-ing template", "Baz Inc.");
        List<Template> expectedList = List.of(foo, bar, baz);
        Mockito.when(templateSvc.getTemplates()).thenReturn(expectedList);

        cmd.connectionChanged(connection);

        Output<?> output = cmd.serializableExecute(new String[0]);
        MatcherAssert.assertThat(output, Matchers.instanceOf(ListOutput.class));
        List<Template> list = ((ListOutput<Template>) output).getPayload();
        MatcherAssert.assertThat(list, Matchers.equalTo(expectedList));
    }
}
