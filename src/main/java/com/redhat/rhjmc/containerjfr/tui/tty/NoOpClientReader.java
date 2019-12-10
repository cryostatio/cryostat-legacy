package com.redhat.rhjmc.containerjfr.tui.tty;

import java.io.IOException;

import com.redhat.rhjmc.containerjfr.core.tui.ClientReader;

class NoOpClientReader implements ClientReader {
    @Override
    public String readLine() {
        throw new UnsupportedOperationException("NoOpClientReader does not support readLine");
    }

    @Override
    public void close() throws IOException {}
}
