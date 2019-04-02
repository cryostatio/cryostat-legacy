package es.andrewazor.containertest.tui;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.inject.Qualifier;

import es.andrewazor.containertest.net.ConnectionListener;

public interface CommandExecutor extends ConnectionListener {
    void run(String[] args);

    @Qualifier
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ExecutionMode { }

    public enum Mode {
        INTERACTIVE,
        BATCH
    }
}