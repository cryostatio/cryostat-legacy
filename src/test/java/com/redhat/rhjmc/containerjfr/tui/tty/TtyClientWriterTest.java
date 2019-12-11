package com.redhat.rhjmc.containerjfr.tui.tty;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import java.io.PrintStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TtyClientWriterTest {

    TtyClientWriter clientWriter;
    PrintStream origOut;
    @Mock PrintStream out;

    @BeforeEach
    void setup() {
        origOut = System.out;
        System.setOut(out);

        clientWriter = new TtyClientWriter();
    }

    @AfterEach
    void teardown() {
        System.setOut(origOut);
    }

    @Test
    void testPrintString() {
        verifyZeroInteractions(out);
        clientWriter.print("foo");
        verify(out).print("foo");
        verifyNoMoreInteractions(out);
    }

    @Test
    void testPrintChar() {
        verifyZeroInteractions(out);
        clientWriter.print('c');
        verify(out).print('c');
        verifyNoMoreInteractions(out);
    }

    @Test
    void testPrintlnString() {
        verifyZeroInteractions(out);
        clientWriter.println("foo");
        verify(out).println("foo");
        verifyNoMoreInteractions(out);
    }

    @Test
    void testPrintln() {
        verifyZeroInteractions(out);
        clientWriter.println();
        verify(out).println();
        verifyNoMoreInteractions(out);
    }
}
