package com.redhat.rhjmc.containerjfr.tui;

import org.apache.commons.lang3.exception.ExceptionUtils;

public interface ClientWriter {
    void print(String s);
    default void print(char c) {
        print(new String(new char[]{c}));
    }
    default void println(String s) {
        print(s + '\n');
    }
    default void println() {
        print("\n");
    }
    default void println(Exception e) {
        println(ExceptionUtils.getStackTrace(e));
    }
}