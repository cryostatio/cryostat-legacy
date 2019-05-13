package es.andrewazor.containertest.commands;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.exception.ExceptionUtils;

public interface SerializableCommand extends Command {

    Output serializableExecute(String[] args);

    public interface Output {
    }

    public class SuccessOutput implements Output {
    }

    public class StringOutput implements Output {
        private final String message;

        public StringOutput(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    public class ListOutput<T> implements Output {
        private final List<T> data;

        public ListOutput(List<T> data) {
            this.data = data;
        }

        public List<T> getData() {
            return data;
        }
    }

    public class MapOutput<K, V> implements Output {
        private final Map<K, V> data;

        public MapOutput(Map<K, V> data) {
            this.data = data;
        }

        public Map<K, V> getData() {
            return data;
        }
    }

    public class ExceptionOutput implements Output {
        private final Exception e;

        public ExceptionOutput(Exception e) {
            this.e = e;
        }

        public String getExceptionMessage() {
            return ExceptionUtils.getMessage(e);
        }
    }

    public class FailureOutput implements Output {
        private final String message;

        public FailureOutput(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }
}