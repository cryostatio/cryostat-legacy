package es.andrewazor.containertest;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public class StdoutTest {
    protected PrintStream origOut;
    protected ByteArrayOutputStream stdout;

    @BeforeEach
    void stdoutSetup() {
        origOut = System.out;
        stdout = new ByteArrayOutputStream();
        System.setOut(new PrintStream(stdout));
    }

    @AfterEach
    void stdoutReset() {
        System.setOut(origOut);
    }
}