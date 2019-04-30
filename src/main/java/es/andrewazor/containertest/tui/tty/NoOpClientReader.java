package es.andrewazor.containertest.tui.tty;

import java.io.IOException;

import es.andrewazor.containertest.tui.ClientReader;

class NoOpClientReader implements ClientReader {
    @Override
    public String readLine() {
        throw new UnsupportedOperationException("NoOpClientReader does not support readLine");
    }

    @Override
    public void close() throws IOException { }
}