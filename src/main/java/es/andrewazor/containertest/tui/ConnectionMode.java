package es.andrewazor.containertest.tui;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import javax.inject.Qualifier;

import es.andrewazor.containertest.ExecutionMode;

@Qualifier
@Documented
@Retention(RetentionPolicy.RUNTIME)
public @interface ConnectionMode {
    ExecutionMode value() default ExecutionMode.INTERACTIVE;
}