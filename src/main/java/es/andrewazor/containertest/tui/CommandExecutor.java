package es.andrewazor.containertest.tui;

import es.andrewazor.containertest.net.ConnectionListener;

public interface CommandExecutor extends ConnectionListener {
    void run(String clientArgString);

    public enum ExecutionMode {
        INTERACTIVE,
        BATCH,
        SOCKET
    }
}