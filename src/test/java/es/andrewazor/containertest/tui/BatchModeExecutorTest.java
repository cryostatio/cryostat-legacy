package es.andrewazor.containertest.tui;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import es.andrewazor.containertest.TestBase;
import es.andrewazor.containertest.commands.CommandRegistry;

@ExtendWith(MockitoExtension.class)
class BatchModeExecutorTest extends TestBase {

    CommandExecutor executor;
    @Mock ClientReader mockClientReader;
    @Mock CommandRegistry mockRegistry;

    @BeforeEach
    void setup() {
        executor = new BatchModeExecutor(mockClientReader, mockClientWriter, () -> mockRegistry);
    }

    @Test
    void shouldValidateAndExitWhenPassedNoArgs() throws Exception {
        verifyZeroInteractions(mockClientReader);
        verifyZeroInteractions(mockRegistry);

        when(mockRegistry.validate(eq("exit"), any(String[].class))).thenReturn(true);

        executor.run(new String[]{ "" });

        MatcherAssert.assertThat(stdout(), Matchers.containsString("\"exit\" \"[]\""));

        InOrder inOrder = inOrder(mockRegistry);
        inOrder.verify(mockRegistry).validate("exit", new String[0]);
        inOrder.verify(mockRegistry).execute("exit", new String[0]);

        verifyNoMoreInteractions(mockClientReader);
        verifyNoMoreInteractions(mockRegistry);
    }

    @Test
    void shouldValidateAndExitWhenPassedOnlyComment() throws Exception {
        verifyZeroInteractions(mockClientReader);
        verifyZeroInteractions(mockRegistry);

        when(mockRegistry.validate(eq("exit"), any(String[].class))).thenReturn(true);

        executor.run(new String[]{ "# This should be a comment ;" });

        MatcherAssert.assertThat(stdout(), Matchers.containsString("\"exit\" \"[]\""));

        InOrder inOrder = inOrder(mockRegistry);
        inOrder.verify(mockRegistry).validate("exit", new String[0]);
        inOrder.verify(mockRegistry).execute("exit", new String[0]);

        verifyNoMoreInteractions(mockClientReader);
        verifyNoMoreInteractions(mockRegistry);
    }

    @Test
    void shouldValidateAndExecuteSingleCommand() throws Exception {
        verifyZeroInteractions(mockClientReader);
        verifyZeroInteractions(mockRegistry);

        when(mockRegistry.validate(eq("help"), any(String[].class))).thenReturn(true);
        when(mockRegistry.validate(eq("exit"), any(String[].class))).thenReturn(true);

        executor.run(new String[]{ "help" });

        MatcherAssert.assertThat(stdout(), Matchers.allOf(
            Matchers.containsString("\"help\" \"[]\""),
            Matchers.containsString("\"exit\" \"[]\"")
        ));

        verify(mockRegistry).validate("help", new String[0]);
        verify(mockRegistry).validate("exit", new String[0]);

        InOrder inOrder = inOrder(mockRegistry);
        inOrder.verify(mockRegistry).execute("help", new String[0]);
        inOrder.verify(mockRegistry).execute("exit", new String[0]);

        verifyNoMoreInteractions(mockClientReader);
        verifyNoMoreInteractions(mockRegistry);
    }

    @Test
    void shouldValidateAndExecuteSingleCommandWithSemicolon() throws Exception {
        verifyZeroInteractions(mockClientReader);
        verifyZeroInteractions(mockRegistry);

        when(mockRegistry.validate(anyString(), any(String[].class))).thenReturn(true);

        executor.run(new String[]{ "help;" });

        MatcherAssert.assertThat(stdout(), Matchers.allOf(
            Matchers.containsString("\"help\" \"[]\""),
            Matchers.containsString("\"exit\" \"[]\"")
        ));

        verify(mockRegistry).validate("help", new String[0]);
        verify(mockRegistry).validate("exit", new String[0]);

        InOrder inOrder = inOrder(mockRegistry);
        inOrder.verify(mockRegistry).execute("help", new String[0]);
        inOrder.verify(mockRegistry).execute("exit", new String[0]);

        verifyNoMoreInteractions(mockClientReader);
        verifyNoMoreInteractions(mockRegistry);
    }

    @Test
    void shouldValidateAndExecuteMultipleCommands() throws Exception {
        verifyZeroInteractions(mockClientReader);
        verifyZeroInteractions(mockRegistry);

        when(mockRegistry.validate(anyString(), any(String[].class))).thenReturn(true);

        executor.run(new String[]{ "help; connect foo; disconnect;" });

        MatcherAssert.assertThat(stdout(), Matchers.allOf(
            Matchers.containsString("\"help\" \"[]\""),
            Matchers.containsString("\"connect\" \"[foo]\""),
            Matchers.containsString("\"disconnect\" \"[]\""),
            Matchers.containsString("\"exit\" \"[]\"")
        ));

        verify(mockRegistry).validate("help", new String[0]);
        verify(mockRegistry).validate("connect", new String[]{ "foo" });
        verify(mockRegistry).validate("disconnect", new String[0]);
        verify(mockRegistry).validate("exit", new String[0]);

        InOrder inOrder = inOrder(mockRegistry);
        inOrder.verify(mockRegistry).execute("help", new String[0]);
        inOrder.verify(mockRegistry).execute("connect", new String[]{ "foo" });
        inOrder.verify(mockRegistry).execute("disconnect", new String[0]);
        inOrder.verify(mockRegistry).execute("exit", new String[0]);

        verifyNoMoreInteractions(mockClientReader);
        verifyNoMoreInteractions(mockRegistry);
    }

    @Test
    void shouldValidateAndExecuteMultipleCommandsWithScriptStyle() throws Exception {
        verifyZeroInteractions(mockClientReader);
        verifyZeroInteractions(mockRegistry);

        when(mockRegistry.validate(anyString(), any(String[].class))).thenReturn(true);

        executor.run(new String[]{ "help;\n# Connect to foo-host;\nconnect foo;\ndisconnect;" });

        MatcherAssert.assertThat(stdout(), Matchers.allOf(
            Matchers.containsString("\"help\" \"[]\""),
            Matchers.containsString("\"connect\" \"[foo]\""),
            Matchers.containsString("\"disconnect\" \"[]\""),
            Matchers.containsString("\"exit\" \"[]\"")
        ));

        verify(mockRegistry).validate("help", new String[0]);
        verify(mockRegistry).validate("connect", new String[]{ "foo" });
        verify(mockRegistry).validate("disconnect", new String[0]);
        verify(mockRegistry).validate("exit", new String[0]);

        InOrder inOrder = inOrder(mockRegistry);
        inOrder.verify(mockRegistry).execute("help", new String[0]);
        inOrder.verify(mockRegistry).execute("connect", new String[]{ "foo" });
        inOrder.verify(mockRegistry).execute("disconnect", new String[0]);
        inOrder.verify(mockRegistry).execute("exit", new String[0]);

        verifyNoMoreInteractions(mockClientReader);
        verifyNoMoreInteractions(mockRegistry);
    }

    @Test
    void shouldAbortWhenAnySuppliedCommandIsInvalid() throws Exception {
        verifyZeroInteractions(mockClientReader);
        verifyZeroInteractions(mockRegistry);

        when(mockRegistry.validate(anyString(), any(String[].class))).thenAnswer(new Answer<Boolean>() {
            @Override
            public Boolean answer(InvocationOnMock invocation) {
                String cmd = (String) invocation.getArguments()[0];
                return !cmd.equals("connect");
            }
        });

        executor.run(new String[]{ "help; connect foo; disconnect;" });

        MatcherAssert.assertThat(stdout(), Matchers.containsString("\"[foo]\" are invalid arguments to connect"));

        verify(mockRegistry).validate("help", new String[0]);
        verify(mockRegistry).validate("connect", new String[]{ "foo" });
        verify(mockRegistry).validate("disconnect", new String[0]);
        verify(mockRegistry).validate("exit", new String[0]);

        verifyNoMoreInteractions(mockClientReader);
        // Implicit verification that no registry.execute() calls were made
        verifyNoMoreInteractions(mockRegistry);
    }

    @Test
    void shouldContinueIfCommandThrows() throws Exception {
        verifyZeroInteractions(mockClientReader);
        verifyZeroInteractions(mockRegistry);

        when(mockRegistry.validate(anyString(), any(String[].class))).thenReturn(true);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Exception {
                String cmd = (String) invocation.getArguments()[0];
                if (cmd.equals("connect")) {
                    throw new NullPointerException("SomeException");
                }
                return null;
            }
        }).when(mockRegistry).execute(anyString(), any(String[].class));

        executor.run(new String[]{ "help; connect foo; disconnect;" });

        MatcherAssert.assertThat(stdout(), Matchers.containsString("connect foo operation failed due to SomeException\njava.lang.NullPointerException: SomeException"));

        verify(mockRegistry).validate("help", new String[0]);
        verify(mockRegistry).validate("connect", new String[]{ "foo" });
        verify(mockRegistry).validate("disconnect", new String[0]);
        verify(mockRegistry).validate("exit", new String[0]);

        InOrder inOrder = inOrder(mockRegistry);
        inOrder.verify(mockRegistry).execute("help", new String[0]);
        inOrder.verify(mockRegistry).execute("connect", new String[]{ "foo" });
        inOrder.verify(mockRegistry).execute("disconnect", new String[0]);
        inOrder.verify(mockRegistry).execute("exit", new String[0]);

        verifyNoMoreInteractions(mockClientReader);
        verifyNoMoreInteractions(mockRegistry);
    }

}