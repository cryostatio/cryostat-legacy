package es.andrewazor.containertest.tui;

import java.io.IOException;

import es.andrewazor.containertest.tui.ClientReader;

class NoOpClientReader implements ClientReader {
    @Override
    public String readLine() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() throws IOException { }
}