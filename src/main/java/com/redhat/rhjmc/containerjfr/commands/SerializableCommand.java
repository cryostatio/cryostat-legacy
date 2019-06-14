package com.redhat.rhjmc.containerjfr.commands;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.exception.ExceptionUtils;

public interface SerializableCommand extends Command {

    Output<?> serializableExecute(String[] args);

    public interface Output<T> {
        T getPayload();
    }

    public class SuccessOutput implements Output<Void> {
        @Override
        public Void getPayload() {
            return null;
        }
    }

    public class StringOutput implements Output<String> {
        private final String message;

        public StringOutput(String message) {
            this.message = message;
        }

        @Override
        public String getPayload() {
            return message;
        }
    }

    public class ListOutput<T> implements Output<List<T>> {
        private final List<T> data;

        public ListOutput(List<T> data) {
            this.data = data;
        }

        @Override
        public List<T> getPayload() {
            return data;
        }
    }

    public class MapOutput<K, V> implements Output<Map<K, V>> {
        private final Map<K, V> data;

        public MapOutput(Map<K, V> data) {
            this.data = data;
        }

        @Override
        public Map<K, V> getPayload() {
            return data;
        }
    }

    public class ExceptionOutput implements Output<String> {
        private final Exception e;

        public ExceptionOutput(Exception e) {
            this.e = e;
        }

        @Override
        public String getPayload() {
            return ExceptionUtils.getMessage(e);
        }
    }

    public class FailureOutput implements Output<String> {
        private final String message;

        public FailureOutput(String message) {
            this.message = message;
        }

        @Override
        public String getPayload() {
            return message;
        }
    }
}