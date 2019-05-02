package es.andrewazor.containertest.tui;

public interface CommandExecutor {
    void run(String clientArgString);

    public enum ExecutionMode {
        INTERACTIVE,
        BATCH,
        SOCKET,
        WEBSOCKET
    }
}