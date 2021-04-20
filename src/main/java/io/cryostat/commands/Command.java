/*-
 * #%L
 * Cryostat
 * %%
 * Copyright (C) 2020 - 2021 The Cryostat Authors
 * %%
 * The Universal Permissive License (UPL), Version 1.0
 * 
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or data
 * (collectively the "Software"), free of charge and under any and all copyright
 * rights in the Software, and any and all patent rights owned or freely
 * licensable by each licensor hereunder covering either (i) the unmodified
 * Software as contributed to or provided by such licensor, or (ii) the Larger
 * Works (as defined below), to deal in both
 * 
 * (a) the Software, and
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software (each a "Larger Work" to which the Software
 * is contributed by such licensors),
 * 
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 * 
 * This license is subject to the following condition:
 * The above copyright notice and either this complete permission notice or at
 * a minimum a reference to the UPL must be included in all copies or
 * substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * #L%
 */
package io.cryostat.commands;

import java.net.MalformedURLException;
import java.util.List;
import java.util.Map;

import javax.management.remote.JMXServiceURL;

import io.cryostat.commands.internal.FailedValidationException;
import io.cryostat.net.TargetConnectionManager;

import org.apache.commons.lang3.exception.ExceptionUtils;

public interface Command {

    String getName();

    Output<?> execute(String[] args);

    void validate(String[] args) throws FailedValidationException;

    boolean isAvailable();

    default boolean validateTargetId(String targetId) {
        boolean jmxServiceUrlMatch = true;
        try {
            new JMXServiceURL(targetId);
        } catch (MalformedURLException e) {
            jmxServiceUrlMatch = false;
        }
        boolean hostPatternMatch =
                TargetConnectionManager.HOST_PORT_PAIR_PATTERN.matcher(targetId).matches();
        return jmxServiceUrlMatch || hostPatternMatch;
    }

    default boolean validateRecordingName(String name) {
        return name.matches("[\\w-_]+(\\.\\d+)?(\\.jfr)?");
    }

    default boolean validateNoNullArgs(String[] args) {
        for (String arg : args) {
            if (arg == null) {
                return false;
            }
        }
        return true;
    }

    interface Output<T> {
        T getPayload();
    }

    class SuccessOutput implements Output<Void> {
        @Override
        public Void getPayload() {
            return null;
        }
    }

    class StringOutput implements Output<String> {
        private final String message;

        public StringOutput(String message) {
            this.message = message;
        }

        @Override
        public String getPayload() {
            return message;
        }
    }

    class ListOutput<T> implements Output<List<T>> {
        private final List<T> data;

        public ListOutput(List<T> data) {
            this.data = data;
        }

        @Override
        public List<T> getPayload() {
            return data;
        }
    }

    class MapOutput<K, V> implements Output<Map<K, V>> {
        private final Map<K, V> data;

        public MapOutput(Map<K, V> data) {
            this.data = data;
        }

        @Override
        public Map<K, V> getPayload() {
            return data;
        }
    }

    class ExceptionOutput implements Output<String> {
        private final Exception e;

        public ExceptionOutput(Exception e) {
            this.e = e;
        }

        @Override
        public String getPayload() {
            return ExceptionUtils.getMessage(e);
        }
    }

    class FailureOutput implements Output<String> {
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
