package com.redhat.rhjmc.containerjfr.tui;

import java.io.Closeable;

public interface ClientReader extends AutoCloseable, Closeable {
    String readLine();
}