package com.redhat.rhjmc.containerjfr.tui.tty;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.util.NoSuchElementException;

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

import com.redhat.rhjmc.containerjfr.TestBase;
import com.redhat.rhjmc.containerjfr.commands.CommandRegistry;
import com.redhat.rhjmc.containerjfr.core.net.JMCConnection;
import com.redhat.rhjmc.containerjfr.core.tui.ClientReader;

@ExtendWith(MockitoExtension.class)
class InteractiveShellExecutorTest extends TestBase {

    InteractiveShellExecutor executor;
    @Mock ClientReader mockClientReader;
    @Mock CommandRegistry mockRegistry;
    @Mock JMCConnection mockConnection;

    @BeforeEach
    void setup() {
        executor = new InteractiveShellExecutor(mockClientReader, mockClientWriter, () -> mockRegistry);
    }

    @Test
    void shouldExecuteAndChangePromptOnConnection() throws Exception {
        verifyZeroInteractions(mockClientReader);
        verifyZeroInteractions(mockRegistry);

        when(mockRegistry.validate(anyString(), any(String[].class))).thenReturn(true);
        when(mockClientReader.readLine())
            .thenReturn("connect foo")
            .thenAnswer(new Answer<String>() {
                @Override
                public String answer(InvocationOnMock invocation) {
                    executor.connectionChanged(mockConnection);
                    return "disconnect";
                }
            })
            .thenAnswer(new Answer<String>() {
                @Override
                public String answer(InvocationOnMock invocation) throws Throwable {
                    executor.connectionChanged(null);
                    return "exit";
                }
            });

        executor.run(null);

        MatcherAssert.assertThat(stdout(),
                Matchers.equalTo("- \n\"connect\" \"[foo]\"\n- \n\"disconnect\" \"[]\"\n> \n\"exit\" \"[]\"\n"));

        verify(mockRegistry).validate("connect", new String[] { "foo" });
        verify(mockRegistry).validate("exit", new String[0]);
        verify(mockClientReader).close();

        InOrder inOrder = inOrder(mockRegistry);
        inOrder.verify(mockRegistry).execute("connect", new String[]{ "foo" });
        inOrder.verify(mockRegistry).execute("disconnect", new String[0]);
        inOrder.verify(mockRegistry).execute("exit", new String[0]);

        verifyNoMoreInteractions(mockClientReader);
        verifyNoMoreInteractions(mockRegistry);
    }

    @Test
    void shouldPrintCommandExceptions() throws Exception {
        verifyZeroInteractions(mockClientReader);
        verifyZeroInteractions(mockRegistry);

        when(mockRegistry.validate(anyString(), any(String[].class))).thenReturn(true);
        when(mockClientReader.readLine()).thenReturn("help").thenReturn("exit");
        doThrow(UnsupportedOperationException.class).when(mockRegistry).execute(eq("help"), any(String[].class));

        executor.run(null);

        MatcherAssert.assertThat(stdout(), Matchers.equalTo(
                "- \n\"help\" \"[]\"\nhelp operation failed due to null\njava.lang.UnsupportedOperationException\n\n- \n\"exit\" \"[]\"\n"));

        verify(mockRegistry).validate("help", new String[0]);
        verify(mockRegistry).validate("exit", new String[0]);
        verify(mockClientReader).close();

        InOrder inOrder = inOrder(mockRegistry);
        inOrder.verify(mockRegistry).execute("help", new String[0]);
        inOrder.verify(mockRegistry).execute("exit", new String[0]);

        verifyNoMoreInteractions(mockClientReader);
        verifyNoMoreInteractions(mockRegistry);
    }

    @Test
    void shouldHandleClientReaderExceptions() throws Exception {
        verifyZeroInteractions(mockClientReader);
        verifyZeroInteractions(mockRegistry);

        when(mockClientReader.readLine()).thenThrow(NullPointerException.class);

        executor.run(null);

        MatcherAssert.assertThat(stdout(), Matchers.equalTo("- java.lang.NullPointerException\n\n"));
        verify(mockClientReader).readLine();
        verify(mockClientReader).close();

        verifyNoMoreInteractions(mockClientReader);
        verifyNoMoreInteractions(mockRegistry);
    }

    @Test
    void shouldHandleClientReaderNoSuchElementExceptions() throws Exception {
        verifyZeroInteractions(mockClientReader);
        verifyZeroInteractions(mockRegistry);

        when(mockRegistry.validate(anyString(), any(String[].class))).thenReturn(true);
        when(mockClientReader.readLine()).thenThrow(NoSuchElementException.class);

        executor.run(null);

        MatcherAssert.assertThat(stdout(), Matchers.equalTo("- \n\"exit\" \"[]\"\n"));

        verify(mockClientReader).readLine();
        verify(mockRegistry).validate("exit", new String[0]);
        verify(mockRegistry).execute("exit", new String[0]);
        verify(mockClientReader).close();

        verifyNoMoreInteractions(mockClientReader);
        verifyNoMoreInteractions(mockRegistry);
    }

    @Test
    void shouldHandleClientReaderReturnsNull() throws Exception {
        verifyZeroInteractions(mockClientReader);
        verifyZeroInteractions(mockRegistry);

        when(mockRegistry.validate(anyString(), any(String[].class))).thenReturn(true);
        when(mockClientReader.readLine()).thenReturn(null);

        executor.run(null);

        MatcherAssert.assertThat(stdout(), Matchers.equalTo("- \n\"exit\" \"[]\"\n"));

        verify(mockClientReader).readLine();
        verify(mockRegistry).validate("exit", new String[0]);
        verify(mockRegistry).execute("exit", new String[0]);
        verify(mockClientReader).close();

        verifyNoMoreInteractions(mockClientReader);
        verifyNoMoreInteractions(mockRegistry);
    }

}