package com.redhat.rhjmc.containerjfr;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import com.redhat.rhjmc.containerjfr.tui.ClientWriter;

public class TestBase {
    protected PrintStream mockStdout;
    protected ClientWriter mockClientWriter;
    private ByteArrayOutputStream baos;
    private boolean echoStdout = false;

    protected void setEcho(boolean echo) {
        this.echoStdout = echo;
    }

    @BeforeEach
    void stdoutSetup() {
        baos = new ByteArrayOutputStream();
        mockStdout = new PrintStream(baos);
        mockClientWriter = mockStdout::print;
    }

    @AfterEach
    void echoStdout() {
        if (echoStdout) {
            System.out.println(mockStdout.toString());
        }
    }

    protected String stdout() {
        return baos.toString();
    }

    protected void testLog(String tag, String msg) {
        System.out.println(String.format("[%s] %s", tag, msg));
    }

    protected void testLog(String msg) {
        System.out.println(msg);
    }
}