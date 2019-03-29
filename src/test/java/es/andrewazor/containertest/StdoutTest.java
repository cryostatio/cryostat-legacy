package es.andrewazor.containertest;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public class StdoutTest {
    protected PrintStream origOut;
    protected ByteArrayOutputStream stdout;
    protected boolean echoStdout = false;

    protected void setEcho(boolean echo) {
        this.echoStdout = echo;
    }

    @BeforeEach
    void stdoutSetup() {
        origOut = System.out;
        stdout = new ByteArrayOutputStream();
        System.setOut(new PrintStream(stdout));
    }

    @AfterEach
    void stdoutReset() {
        if (echoStdout) {
            origOut.println(stdout.toString());
        }
        System.setOut(origOut);
    }

    public void testLog(String tag, String msg) {
        origOut.println(String.format("[%s] %s", tag, msg));
    }

    public void testLog(String msg) {
        origOut.println(msg);
    }
}