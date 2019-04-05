package es.andrewazor.containertest.util;

import java.util.function.Consumer;

public interface CheckedConsumer<T> extends Consumer<T> {
    default void accept(T t) {
        try {
            acceptThrows(t);
        } catch (Exception e) {
            handleException(e);
        }
    }

    void acceptThrows(T t) throws Exception;

    default void handleException(Exception e) {}
}