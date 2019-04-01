package es.andrewazor.containertest;

import java.io.Closeable;

public interface ClientReader extends AutoCloseable, Closeable {
    String readLine();
}