package es.andrewazor.containertest.tui;

import java.io.Closeable;

public interface ClientReader extends AutoCloseable, Closeable {
    String readLine();
}