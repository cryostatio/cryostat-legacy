package es.andrewazor.containertest;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.inject.Qualifier;

interface CommandExecutor extends ConnectionListener {
    void run(String[] args);

    @Qualifier
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @interface ExecutionMode { }

    enum Mode {
        INTERACTIVE,
        BATCH
    }
}