package es.andrewazor.containertest.tui;

import es.andrewazor.containertest.net.ConnectionListener;

public interface CommandExecutor extends ConnectionListener {
    void run(String[] args);

    public enum ExecutionMode {
        INTERACTIVE,
        BATCH
    }
}